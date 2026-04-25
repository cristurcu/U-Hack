"""Thread-safe in-memory store of Wyscout events keyed by match_id.

Filled by the Kafka consumer thread (kafka_consumer.py); read by both the
periodic publisher (live_publisher.py) and on-demand HTTP endpoints
(via loader.py).

Idempotent: events with the same wyscout id are only added once per match.
"""

from __future__ import annotations

import threading
from collections import defaultdict
from typing import Iterable


class EventStore:
    def __init__(self):
        self._lock = threading.RLock()
        self._events: dict[int, list[dict]] = defaultdict(list)
        self._seen: dict[int, set] = defaultdict(set)
        self._finished: set[int] = set()

    def add(self, match_id: int, event: dict) -> bool:
        """Append event under match_id. Returns True if it was actually added."""
        if match_id is None:
            return False
        with self._lock:
            ev_id = event.get("id")
            if ev_id is not None and ev_id in self._seen[match_id]:
                return False
            if ev_id is not None:
                self._seen[match_id].add(ev_id)
            self._events[match_id].append(event)
            # Detect match-end sentinel: match_id is now considered finished
            if (event.get("type") or {}).get("primary") == "match_end":
                self._finished.add(match_id)
            return True

    def add_many(self, match_id: int, events: Iterable[dict]) -> int:
        n = 0
        for e in events:
            if self.add(match_id, e):
                n += 1
        return n

    def get(self, match_id: int) -> list[dict]:
        with self._lock:
            return list(self._events.get(match_id, []))

    def match_ids(self) -> list[int]:
        with self._lock:
            return list(self._events.keys())

    def is_finished(self, match_id: int) -> bool:
        with self._lock:
            return match_id in self._finished

    def mark_finished(self, match_id: int) -> None:
        with self._lock:
            self._finished.add(match_id)


# module-level singleton — every importer shares the same buffer
STORE = EventStore()
