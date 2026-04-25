"""Kafka consumer thread for ai-service.

Subscribes to the raw Wyscout event stream (`wyscout-events` by default) and
appends every event into the shared in-process EventStore. The publisher
thread and the HTTP endpoints read from the same store.

Started only when KAFKA_BOOTSTRAP is set (i.e., in docker-compose). When
unset (local dev with a JSON file), the loader falls back to disk and this
thread never runs.
"""

from __future__ import annotations

import json
import logging
import os
import threading

from confluent_kafka import Consumer

from event_store import STORE

log = logging.getLogger(__name__)


class WyscoutEventConsumer(threading.Thread):
    def __init__(self):
        super().__init__(name="ai-service-kafka-consumer", daemon=True)
        self._stop = threading.Event()
        self._consumer = Consumer({
            "bootstrap.servers": os.environ["KAFKA_BOOTSTRAP"],
            "group.id": os.environ.get("GROUP_ID", "ai-service"),
            "auto.offset.reset": "earliest",
            "enable.auto.commit": True,
        })
        self._topic = os.environ.get("IN_TOPIC", "wyscout-events")

    def stop(self):
        self._stop.set()

    def run(self):
        log.info("subscribing to '%s'", self._topic)
        self._consumer.subscribe([self._topic])
        ingested = 0
        while not self._stop.is_set():
            msg = self._consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                log.warning("kafka error: %s", msg.error())
                continue
            try:
                event = json.loads(msg.value().decode("utf-8"))
            except Exception as e:
                log.warning("bad event JSON on offset %s: %s", msg.offset(), e)
                continue

            match_id = _extract_match_id(event)
            if match_id is None:
                continue

            if STORE.add(match_id, event):
                ingested += 1
                if ingested % 100 == 0:
                    log.info("ingested %d events (matches: %s)", ingested, STORE.match_ids())


def _extract_match_id(event: dict) -> int | None:
    if "matchId" in event and event["matchId"] is not None:
        return int(event["matchId"])
    wy = (event.get("match") or {}).get("wyId")
    return int(wy) if wy is not None else None


def maybe_start():
    if not os.environ.get("KAFKA_BOOTSTRAP"):
        return None
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s [%(threadName)s] %(name)s: %(message)s",
    )
    c = WyscoutEventConsumer()
    c.start()
    log.info("ai-service kafka consumer started")
    return c
