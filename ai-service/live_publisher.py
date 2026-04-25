"""Live publisher loop for ai-service.

Periodically pulls cumulative events from stats-service `/events/{matchId}`,
computes passing-network and pressing for both teams, publishes the results
on Kafka topics `passing-network` and `pressing-stats`. Triggered only when
env `KAFKA_BOOTSTRAP` is set (in docker-compose).
"""

from __future__ import annotations

import json
import logging
import os
import threading
import time
from urllib.request import urlopen

from confluent_kafka import Producer

from insights_network.passing_network import build_passing_network
from insights_pressing.pressing import build_pressing

log = logging.getLogger(__name__)


def _http_get_json(url: str, timeout: float = 5.0):
    with urlopen(url, timeout=timeout) as r:
        return json.loads(r.read())


class LivePublisher(threading.Thread):
    def __init__(self):
        super().__init__(name="ai-service-publisher", daemon=True)
        self._stop = threading.Event()
        self._stats_url = os.environ.get("STATS_SERVICE_URL", "http://stats-service:8000").rstrip("/")
        self._mock_url = os.environ.get("WYSCOUT_MOCK_URL", "http://wyscout-mock:5001").rstrip("/")
        self._interval = int(os.environ.get("RECOMPUTE_MS", "3000")) / 1000.0
        self._producer = Producer({
            "bootstrap.servers": os.environ["KAFKA_BOOTSTRAP"],
            "linger.ms": 50,
        })
        self._passing_topic = os.environ.get("OUT_TOPIC_PASSING", "passing-network")
        self._pressing_topic = os.environ.get("OUT_TOPIC_PRESSING", "pressing-stats")
        self._meta_cache: dict = {}
        self._meta_cache_ts: float = 0.0

    def stop(self):
        self._stop.set()

    def run(self):
        log.info("publisher loop tick=%.1fs stats=%s mock=%s",
                 self._interval, self._stats_url, self._mock_url)
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
        try:
            data = _http_get_json(f"{self._stats_url}/matches")
        except Exception as e:
            log.debug("stats-service /matches not ready: %s", e)
            return

        for m in data.get("matches", []):
            match_id = m.get("matchId")
            if match_id is None:
                continue
            self._process_match(match_id)

    def _meta(self):
        # Refresh meta from mock at most once every 30s.
        now = time.time()
        if not self._meta_cache or (now - self._meta_cache_ts) > 30:
            try:
                self._meta_cache = _http_get_json(f"{self._mock_url}/match-meta")
                self._meta_cache_ts = now
            except Exception:
                self._meta_cache = self._meta_cache or {}
        return self._meta_cache

    def _process_match(self, match_id: int):
        try:
            payload = _http_get_json(f"{self._stats_url}/events/{match_id}")
        except Exception as e:
            log.debug("no events for match %s: %s", match_id, e)
            return

        events = payload.get("events") or []
        if not events:
            return

        meta = self._meta()
        teams_meta = meta.get("teams") or {}
        subs = meta.get("substitutions") or []
        home_id = payload.get("homeTeamId")
        away_id = payload.get("awayTeamId")

        for team_id in filter(None, (home_id, away_id)):
            self._publish_passing(match_id, team_id, events, subs, teams_meta)
            self._publish_pressing(match_id, team_id, events, teams_meta)

        self._producer.poll(0)

    def _team_name(self, team_id, teams_meta):
        info = teams_meta.get(str(team_id)) or teams_meta.get(team_id) or {}
        return info.get("officialName") or info.get("name") or str(team_id)

    def _publish_passing(self, match_id, team_id, events, subs, teams_meta):
        try:
            result = build_passing_network(
                events=events,
                team_id=team_id,
                substitutions=subs,
                period="full",
                until_first_sub=True,
                min_passes=2,
            )
        except Exception as e:
            log.debug("passing build failed match=%s team=%s: %s", match_id, team_id, e)
            return

        out = {
            "matchId": match_id,
            "teamId": team_id,
            "teamName": self._team_name(team_id, teams_meta),
            "period": "full",
            "cutoffMinute": result.get("cutoff_minute"),
            "nodes": result.get("nodes", []),
            "edges": result.get("edges", []),
        }
        self._producer.produce(
            self._passing_topic,
            key=f"{match_id}:{team_id}".encode(),
            value=json.dumps(out, ensure_ascii=False).encode(),
        )

    def _publish_pressing(self, match_id, team_id, events, teams_meta):
        try:
            result = build_pressing(events=events, team_id=team_id, period="full")
        except Exception as e:
            log.debug("pressing build failed match=%s team=%s: %s", match_id, team_id, e)
            return

        out = {
            "matchId": match_id,
            "teamId": team_id,
            "teamName": self._team_name(team_id, teams_meta),
            "period": "full",
            **result,
        }
        self._producer.produce(
            self._pressing_topic,
            key=f"{match_id}:{team_id}".encode(),
            value=json.dumps(out, ensure_ascii=False).encode(),
        )


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