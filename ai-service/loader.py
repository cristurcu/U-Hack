"""Match data loader.

Two modes:
1. STATIC (default) — read a Wyscout JSON file from disk. Path resolved from
   `--source` arg, env `WYSCOUT_MOCK_PATH`, or the bundled `mock_data/` fallback.
2. LIVE — when env `STATS_SERVICE_URL` is set, fetch the live event stream from
   the running stats-service, and pull match metadata (teams, subs) from
   `WYSCOUT_MOCK_URL/match-meta`. Returns the same shape as the static loader,
   so the rest of the codebase doesn't change.
"""

import json
import os
from pathlib import Path
from typing import Optional
from urllib.request import urlopen

DEFAULT_MOCK = (
    Path(__file__).resolve().parent
    / "mock_data"
    / "u_cluj_wyscout_mock_match_events_april_2026.json"
)


def _live_load(stats_url: str, mock_url: Optional[str], match_id: Optional[int]) -> dict:
    if match_id is None:
        # Pick the first active match if not specified.
        with urlopen(f"{stats_url.rstrip('/')}/healthz", timeout=5) as r:
            health = json.loads(r.read())
        ids = health.get("matches") or []
        if not ids:
            raise RuntimeError("stats-service has no active matches yet")
        match_id = ids[0]

    with urlopen(f"{stats_url.rstrip('/')}/events/{match_id}", timeout=5) as r:
        ev_payload = json.loads(r.read())

    meta = {}
    if mock_url:
        try:
            with urlopen(f"{mock_url.rstrip('/')}/match-meta", timeout=5) as r:
                meta = json.loads(r.read())
        except Exception:
            meta = {}

    return {
        "events": ev_payload.get("events", []),
        "teams": meta.get("teams", {}),
        "substitutions": meta.get("substitutions", []),
        "match": meta.get("match", {
            "wyId": match_id,
            "teamsData": {
                "home": {"teamId": ev_payload.get("homeTeamId")},
                "away": {"teamId": ev_payload.get("awayTeamId")},
            },
        }),
    }


def load_match(source: str | Path | None = None, match_id: Optional[int] = None) -> dict:
    stats_url = os.getenv("STATS_SERVICE_URL")
    if stats_url:
        mock_url = os.getenv("WYSCOUT_MOCK_URL")
        return _live_load(stats_url, mock_url, match_id)

    path = Path(source) if source else Path(os.getenv("WYSCOUT_MOCK_PATH", DEFAULT_MOCK))
    with open(path) as f:
        data = json.load(f)
    return data["elements"][0]
