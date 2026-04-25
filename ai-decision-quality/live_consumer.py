"""Live Kafka consumer that scores incoming Wyscout events with the trained
decision-quality model and republishes results on `event-scores`.

The model expects the same feature shape as the offline pipeline. Here we map
the slim Wyscout JSON shape that flows through Kafka into a single-row
DataFrame, build context features against a small per-(match, team) buffer of
recent actions, and call `predict_proba`.

Activated only when env `KAFKA_BOOTSTRAP` is set (in docker-compose). Otherwise
the API behaves exactly like before — pure offline batch.
"""

from __future__ import annotations

import json
import logging
import os
import threading
from collections import defaultdict, deque

import joblib
import numpy as np
import pandas as pd
from confluent_kafka import Consumer, Producer

from decision_quality_pipeline import (
    add_context_features,
    build_feature_matrix,
)

log = logging.getLogger(__name__)

ACTION_TYPES = {"pass", "shot", "carry"}


def _flank(y) -> str:
    if y is None:
        return "unknown"
    try:
        y = float(y)
    except (TypeError, ValueError):
        return "unknown"
    if y < 33.3:
        return "right"
    if y > 66.7:
        return "left"
    return "central"


def _flatten_event(ev: dict, match_id: int) -> dict:
    """Convert a slim Wyscout event into the column shape the pipeline expects."""
    loc = ev.get("location") or {}
    team = ev.get("team") or {}
    player = ev.get("player") or {}
    typ = ev.get("type") or {}
    poss = ev.get("possession") or {}

    minute = ev.get("minute") or 0
    second = ev.get("second") or 0
    period = ev.get("matchPeriod") or "1H"
    period_offset = 45 * 60 if period == "2H" else 0

    return {
        "match_id": match_id,
        "event_id": ev.get("id") or 0,
        "minute": int(minute),
        "second": int(second),
        "period": period,
        "absolute_second": int(period_offset + minute * 60 + second),
        "event_primary": typ.get("primary") or "unknown",
        "x": float(loc.get("x") or 0.0),
        "y": float(loc.get("y") or 0.0),
        "team_id": team.get("id"),
        "team_name": team.get("name") or "UNK",
        "player_id": player.get("id"),
        "player_name": player.get("name") or "UNK",
        "position": player.get("position") or "UNK",
        "possession_id": poss.get("id"),
        "possession_flank": _flank(loc.get("y")),
        "shot_is_goal_bool": bool(((ev.get("shot") or {}).get("isGoal")) or False),
    }


class LiveScorer(threading.Thread):
    def __init__(self, model_path, bootstrap, in_topic, out_topic, group_id):
        super().__init__(name="dq-live-scorer", daemon=True)
        self._model = joblib.load(model_path)
        self._consumer = Consumer({
            "bootstrap.servers": bootstrap,
            "group.id": group_id,
            "auto.offset.reset": "earliest",
            "enable.auto.commit": True,
        })
        self._producer = Producer({"bootstrap.servers": bootstrap, "linger.ms": 50})
        self._in_topic = in_topic
        self._out_topic = out_topic
        self._buffers: dict = defaultdict(lambda: deque(maxlen=400))
        self._stop = threading.Event()

    def stop(self):
        self._stop.set()

    def run(self):
        log.info("dq live scorer subscribing to %s", self._in_topic)
        self._consumer.subscribe([self._in_topic])
        scored = 0
        try:
            while not self._stop.is_set():
                msg = self._consumer.poll(timeout=1.0)
                if msg is None or msg.error():
                    continue
                try:
                    payload = json.loads(msg.value().decode("utf-8"))
                except (ValueError, UnicodeDecodeError):
                    continue
                match_id = payload.get("matchId")
                if match_id is None:
                    continue

                row = _flatten_event(payload, match_id)
                key = (match_id, row["team_id"])
                buf = self._buffers[key]
                buf.append(row)

                if row["event_primary"] not in ACTION_TYPES:
                    continue

                try:
                    score = self._score(buf)
                except Exception as e:
                    log.warning("scoring failed eid=%s: %s", row["event_id"], e)
                    continue

                out = {
                    "matchId": match_id,
                    "eventId": row["event_id"],
                    "minute": row["minute"],
                    "second": row["second"],
                    "period": row["period"],
                    "playerId": row["player_id"],
                    "playerName": row["player_name"],
                    "teamId": row["team_id"],
                    "teamName": row["team_name"],
                    "eventPrimary": row["event_primary"],
                    "decisionValue": score,
                }
                self._producer.produce(
                    self._out_topic,
                    key=str(row["event_id"]).encode(),
                    value=json.dumps(out).encode(),
                )
                self._producer.poll(0)
                scored += 1
                if scored % 50 == 0:
                    log.info("dq scored %d events", scored)
        finally:
            try:
                self._producer.flush(2.0)
                self._consumer.close()
            except Exception:
                pass

    def _score(self, buf) -> float:
        df = pd.DataFrame(list(buf))
        df = df.sort_values(by=["match_id", "minute", "second", "event_id"]).reset_index(drop=True)
        df = add_context_features(df)
        X, _, _ = build_feature_matrix(df)
        last_X = X.iloc[[-1]]
        proba = self._model.predict_proba(last_X)
        # second column = positive class
        if proba.shape[1] >= 2:
            return float(proba[0, 1])
        return float(proba[0, 0])


def maybe_start(model_path) -> "LiveScorer | None":
    bootstrap = os.environ.get("KAFKA_BOOTSTRAP")
    if not bootstrap:
        return None
    in_topic = os.environ.get("IN_TOPIC", "wyscout-events")
    out_topic = os.environ.get("OUT_TOPIC", "event-scores")
    group_id = os.environ.get("GROUP_ID", "ai-decision-quality")
    log.info("Starting live scorer (in=%s out=%s group=%s)", in_topic, out_topic, group_id)
    scorer = LiveScorer(model_path, bootstrap, in_topic, out_topic, group_id)
    scorer.start()
    return scorer
