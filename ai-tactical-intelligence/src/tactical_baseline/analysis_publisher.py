"""Kafka publisher for post-match tactical-intelligence reports.

Activated only when env KAFKA_BOOTSTRAP is set. After the API builds a report
it calls `publish(match_id, report)`; Platform's AnalysisReportConsumer
subscribes to `analysis-tactical-intelligence` and persists the payload.

Failure is non-fatal — the HTTP response goes out either way.
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
        _topic = os.environ.get("OUT_TOPIC_ANALYSIS", "analysis-tactical-intelligence")
        log.info("tactical-intelligence analysis publisher → topic '%s'", _topic)
    return _producer


def publish(match_id, report: dict) -> None:
    p = _ensure_producer()
    if p is None:
        return
    try:
        wire = {
            "match_id": int(match_id) if match_id is not None else None,
            "type": "tactical-intelligence",
            "payload": report,
        }
        p.produce(
            _topic,
            key=str(match_id).encode("utf-8") if match_id is not None else None,
            value=json.dumps(wire, ensure_ascii=False, default=str).encode("utf-8"),
        )
        p.poll(0)
    except Exception as e:
        log.warning("failed to publish tactical-intelligence report for %s: %s", match_id, e)
