"""Kafka publisher for post-match decision-quality reports.

Activated only when env KAFKA_BOOTSTRAP is set (in docker-compose). The API
endpoint that builds the report calls `publish(match_id, report)` after the
response body is ready; Platform's AnalysisReportConsumer subscribes to
`analysis-decision-quality` and persists the payload.

Failure is non-fatal — if Kafka is unavailable the API still returns its
HTTP response, just without the asynchronous record.
"""

from __future__ import annotations

import json
import logging
import os
import threading

log = logging.getLogger(__name__)

_lock = threading.Lock()
_producer = None
_topic = None


def _ensure_producer():
    global _producer, _topic
    if _producer is not None:
        return _producer
    if not os.environ.get("KAFKA_BOOTSTRAP"):
        return None
    with _lock:
        if _producer is not None:
            return _producer
        try:
            from confluent_kafka import Producer
        except ImportError:
            log.warning("confluent-kafka not installed; analysis publisher disabled")
            return None
        _producer = Producer({
            "bootstrap.servers": os.environ["KAFKA_BOOTSTRAP"],
            "linger.ms": 50,
        })
        _topic = os.environ.get("OUT_TOPIC_ANALYSIS", "analysis-decision-quality")
        log.info("decision-quality analysis publisher → topic '%s'", _topic)
    return _producer


def publish(match_id, report: dict) -> None:
    p = _ensure_producer()
    if p is None:
        return
    try:
        wire = {
            "match_id": int(match_id) if match_id is not None else None,
            "type": "decision-quality",
            "payload": report,
        }
        p.produce(
            _topic,
            key=str(match_id).encode("utf-8") if match_id is not None else None,
            value=json.dumps(wire, ensure_ascii=False, default=str).encode("utf-8"),
        )
        p.poll(0)
    except Exception as e:
        log.warning("failed to publish decision-quality report for %s: %s", match_id, e)
