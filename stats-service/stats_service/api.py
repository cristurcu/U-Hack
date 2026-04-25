"""FastAPI app exposing live match stats."""

from __future__ import annotations

from typing import Optional

from fastapi import FastAPI, HTTPException, Query

from . import analyzer
from .store import MatchStore

app = FastAPI(title="Stats Service", version="1.0")
_store: Optional[MatchStore] = None


def attach_store(store: MatchStore) -> None:
    global _store
    _store = store


def _get_store() -> MatchStore:
    if _store is None:
        raise HTTPException(503, "store not ready")
    return _store


@app.get("/healthz")
def healthz():
    store = _get_store()
    return {"ok": True, "matches": store.match_ids()}


@app.get("/matches")
def matches():
    store = _get_store()
    return {"matches": store.summary()}


def _summary_view(stats: dict) -> dict:
    teamStats = {}
    for tid, ts in (stats.get("teamStats") or {}).items():
        if not ts:
            continue
        teamStats[tid] = {k: v for k, v in ts.items() if k != "topChainsByEffectiveness"}
    return {
        "metadata": stats.get("metadata"),
        "teamStats": teamStats,
        "comparison": stats.get("comparison"),
    }


@app.get("/stats/{match_id}/summary")
def stats_summary(match_id: int):
    """Compact view (no chain detail) — ~5 KB instead of ~280 KB."""
    full = stats(match_id)
    return _summary_view(full)


@app.get("/stats/{match_id}")
def stats(match_id: int):
    store = _get_store()
    cached = store.latest_stats(match_id)
    if cached is not None:
        return cached
    events = store.snapshot(match_id)
    if not events:
        raise HTTPException(404, f"no events yet for match {match_id}")
    home_id, away_id = store.teams(match_id)
    fresh = analyzer.analyze_match(events, home_id, away_id, match_id=match_id)
    store.set_stats(match_id, fresh)
    return fresh


@app.get("/events/{match_id}")
def events(match_id: int):
    """Live event stream snapshot — used by downstream insight services."""
    store = _get_store()
    evs = store.snapshot(match_id)
    if not evs:
        raise HTTPException(404, f"no events yet for match {match_id}")
    home_id, away_id = store.teams(match_id)
    return {
        "matchId": match_id,
        "homeTeamId": home_id,
        "awayTeamId": away_id,
        "eventCount": len(evs),
        "events": evs,
    }


@app.get("/stats/{match_id}/chains")
def chains(match_id: int,
           team: Optional[int] = Query(None, description="filter by team id"),
           top: int = Query(10, ge=1, le=100)):
    store = _get_store()
    cached = store.latest_stats(match_id)
    if cached is None:
        raise HTTPException(404, f"no stats yet for match {match_id}")
    by_team = cached.get("chains", {})
    if team is not None:
        team_chains = by_team.get(str(team), [])
    else:
        team_chains = []
        for v in by_team.values():
            team_chains.extend(v)
    team_chains = sorted(team_chains, key=lambda c: c.get("effectivenessScore", 0), reverse=True)
    return {"matchId": match_id, "team": team, "chains": team_chains[:top]}
