"""Match data loader — two modes.

1. STATIC (offline / dev) — read a Wyscout JSON file from disk. Path resolved
   from `--source` arg, env `WYSCOUT_MOCK_PATH`, or the bundled `mock_data/`.

2. LIVE — when `KAFKA_BOOTSTRAP` is set the in-process Kafka consumer fills
   the shared EventStore. `load_match()` reads from there. No more HTTP hop
   to a stats-service.

Match metadata (teams, substitutions) is fetched lazily from the wyscout-mock
control endpoint (`WYSCOUT_MOCK_URL/match-meta`) when running live, since the
event stream alone doesn't carry the substitutions block we need for the
"until first sub" cutoff used by the passing-network insight.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Optional
from urllib.request import urlopen

from event_store import STORE

DEFAULT_MOCK = (
    Path(__file__).resolve().parent
    / "mock_data"
    / "u_cluj_wyscout_mock_match_events_april_2026.json"
)


def _live_load(mock_url: Optional[str], match_id: Optional[int]) -> dict:
    # Pick the first known match if none specified.
    known = STORE.match_ids()
    if match_id is None:
        if not known:
            raise RuntimeError("no matches in EventStore yet — Kafka consumer hasn't received events")
        match_id = known[0]

    events = STORE.get(int(match_id))
    if not events:
        raise RuntimeError(f"no events for match {match_id} in EventStore")

    meta = {}
    if mock_url:
        try:
            with urlopen(f"{mock_url.rstrip('/')}/match-meta", timeout=5) as r:
                meta = json.loads(r.read())
        except Exception:
            meta = {}

    home_id = (events[0].get("team") or {}).get("id")
    return {
        "events": events,
        "teams": meta.get("teams", {}),
        "substitutions": meta.get("substitutions", []),
        "match": meta.get("match") or {
            "wyId": match_id,
            "teamsData": {
                "home": {"teamId": home_id},
                "away": {"teamId": None},
            },
        },
    }


def load_match(source: str | Path | None = None, match_id: Optional[int] = None) -> dict:
    if os.getenv("KAFKA_BOOTSTRAP"):
        mock_url = os.getenv("WYSCOUT_MOCK_URL")
        return _live_load(mock_url, match_id)

    path = Path(source) if source else Path(os.getenv("WYSCOUT_MOCK_PATH", DEFAULT_MOCK))
    with open(path) as f:
        data = json.load(f)
    return data["elements"][0]
