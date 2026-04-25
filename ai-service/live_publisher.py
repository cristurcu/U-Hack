"""Periodic insight publisher for ai-service.

Every RECOMPUTE_MS, walks every match in the EventStore (filled by the Kafka
consumer thread) and publishes:
  - passing-network    → topic `insights-passing-network`
  - pressing           → topic `insights-pressing`
  - ball-losses        → topic `insights-ball-losses`
  - line-breaks        → topic `insights-line-breaks`

One message per (match, team, insight type). Wire shape Platform expects:
{
  "match_id": <wyId>,
  "team_id":  <teamId>,
  "type":     "passing-network" | "pressing" | "ball-losses" | "line-breaks",
  "payload":  { ...the existing insight response body... }
}

When the match-end sentinel is seen the publisher additionally emits one
message on the `match-lifecycle` topic so Platform's lifecycle consumer can
trigger DQ + TI dispatch. We track which matches we've already finalized so
we don't spam the topic.
"""

from __future__ import annotations

import json
import logging
import os
import threading
import time
from urllib.request import urlopen

from confluent_kafka import Producer

from event_store import STORE
from insights_ball_losses.ball_losses import build_ball_losses
from insights_line_breaks.line_breaks import build_line_breaks
from insights_network.passing_network import build_passing_network
from insights_pressing.pressing import build_pressing

log = logging.getLogger(__name__)


def _http_get_json(url: str, timeout: float = 5.0):
    with urlopen(url, timeout=timeout) as r:
        return json.loads(r.read())


def _team_ids_in(events: list[dict]) -> list[int]:
    seen: set[int] = set()
    order: list[int] = []
    for e in events:
        tid = (e.get("team") or {}).get("id")
        if tid is not None and tid not in seen:
            seen.add(tid)
            order.append(tid)
    return order


class LivePublisher(threading.Thread):
    def __init__(self):
        super().__init__(name="ai-service-publisher", daemon=True)
        self._stop = threading.Event()
        self._interval = int(os.environ.get("RECOMPUTE_MS", "3000")) / 1000.0
        self._mock_url = os.environ.get(
            "WYSCOUT_MOCK_URL", "http://wyscout-mock:5001"
        ).rstrip("/")
        self._producer = Producer({
            "bootstrap.servers": os.environ["KAFKA_BOOTSTRAP"],
            "linger.ms": 50,
        })
        self._topics = {
            "passing-network": os.environ.get(
                "OUT_TOPIC_PASSING_NETWORK", "insights-passing-network"
            ),
            "pressing": os.environ.get(
                "OUT_TOPIC_PRESSING", "insights-pressing"
            ),
            "ball-losses": os.environ.get(
                "OUT_TOPIC_BALL_LOSSES", "insights-ball-losses"
            ),
            "line-breaks": os.environ.get(
                "OUT_TOPIC_LINE_BREAKS", "insights-line-breaks"
            ),
        }
        self._lifecycle_topic = os.environ.get(
            "OUT_TOPIC_LIFECYCLE", "match-lifecycle"
        )
        self._meta_cache: dict = {}
        self._meta_cache_ts: float = 0.0
        self._finalized: set[int] = set()

    def stop(self):
        self._stop.set()

    def run(self):
        log.info("publisher loop tick=%.1fs topics=%s", self._interval, list(self._topics.values()))
        while not self._stop.wait(self._interval):
            try:
                self._tick()
            except Exception as e:
                log.warning("publisher tick failed: %s", e)
        try:
            self._producer.flush(2.0)
        except Exception:
            pass

    def _tick(self):
        for match_id in STORE.match_ids():
            events = STORE.get(match_id)
            if not events:
                continue
            self._publish_match(match_id, events)
            if STORE.is_finished(match_id) and match_id not in self._finalized:
                self._publish_lifecycle_end(match_id)
                self._finalized.add(match_id)
        self._producer.poll(0)

    def _meta(self) -> dict:
        # Soft-fetch metadata from the mock — refreshed at most once / 30s.
        # Failure is non-fatal; meta is only used for substitutions and team names.
        now = time.time()
        if not self._meta_cache or (now - self._meta_cache_ts) > 30:
            try:
                self._meta_cache = _http_get_json(f"{self._mock_url}/match-meta")
                self._meta_cache_ts = now
            except Exception:
                self._meta_cache = self._meta_cache or {}
        return self._meta_cache

    def _publish_match(self, match_id: int, events: list[dict]):
        meta = self._meta()
        subs = meta.get("substitutions") or []
        team_ids = _team_ids_in(events)
        for team_id in team_ids:
            self._publish_passing(match_id, team_id, events, subs)
            self._publish_pressing(match_id, team_id, events)
            self._publish_ball_losses(match_id, team_id, events)
            self._publish_line_breaks(match_id, team_id, events)

    def _emit(self, kind: str, match_id: int, team_id: int, payload: dict):
        wire = {
            "match_id": match_id,
            "team_id": team_id,
            "type": kind,
            "payload": payload,
        }
        self._producer.produce(
            self._topics[kind],
            key=f"{match_id}:{team_id}".encode("utf-8"),
            value=json.dumps(wire, ensure_ascii=False).encode("utf-8"),
        )

    def _publish_passing(self, match_id, team_id, events, subs):
        try:
            r = build_passing_network(
                events=events,
                team_id=team_id,
                substitutions=subs,
                period="full",
                until_first_sub=True,
                min_passes=2,
            )
        except Exception as e:
            log.debug("passing failed match=%s team=%s: %s", match_id, team_id, e)
            return
        self._emit("passing-network", match_id, team_id, r)

    def _publish_pressing(self, match_id, team_id, events):
        try:
            r = build_pressing(events=events, team_id=team_id, period="full")
        except Exception as e:
            log.debug("pressing failed match=%s team=%s: %s", match_id, team_id, e)
            return
        self._emit("pressing", match_id, team_id, r)

    def _publish_ball_losses(self, match_id, team_id, events):
        try:
            r = build_ball_losses(events=events, team_id=team_id, period="full")
        except Exception as e:
            log.debug("ball-losses failed match=%s team=%s: %s", match_id, team_id, e)
            return
        self._emit("ball-losses", match_id, team_id, r)

    def _publish_line_breaks(self, match_id, team_id, events):
        try:
            r = build_line_breaks(events=events, team_id=team_id, period="full")
        except Exception as e:
            log.debug("line-breaks failed match=%s team=%s: %s", match_id, team_id, e)
            return
        self._emit("line-breaks", match_id, team_id, r)

    def _publish_lifecycle_end(self, match_id: int):
        msg = {"event": "match_end", "match_id": match_id}
        try:
            self._producer.produce(
                self._lifecycle_topic,
                key=str(match_id).encode("utf-8"),
                value=json.dumps(msg).encode("utf-8"),
            )
            log.info("emitted match_end for match %s", match_id)
        except Exception as e:
            log.warning("failed to emit match_end for %s: %s", match_id, e)


def maybe_start():
    if not os.environ.get("KAFKA_BOOTSTRAP"):
        return None
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s [%(threadName)s] %(name)s: %(message)s",
    )
    p = LivePublisher()
    p.start()
    log.info("ai-service live publisher started")
    return p
