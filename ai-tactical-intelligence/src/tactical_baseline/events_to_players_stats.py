"""Aggregate raw Wyscout events into the `players_stats` shape this service
expects. Called by the new /api/insights/from-events endpoint so Platform
can hand off the event log directly without doing a translation in Java.

The output mirrors the shape produced by Wyscout's `*_players_stats.json`
exports: a top-level dict with a `players` list, each player carrying a
`total` sub-dict of the metrics consumed by feature_engineering.py
(total_passes, total_shotsOnTarget, total_duelsWon, ...).

Event-tag mapping is best-effort — Wyscout's per-match stats use slightly
different denominators (e.g. defensiveDuels) than what raw events expose.
We approximate; downstream metrics like passSuccessRate will be exact
because they only use total_passes and total_successfulPasses, both of
which are direct event counts.
"""

from __future__ import annotations

from typing import Any


_INITIAL_TOTALS = {
    "passes": 0,
    "successfulPasses": 0,
    "progressivePasses": 0,
    "successfulProgressivePasses": 0,
    "passesToFinalThird": 0,
    "successfulPassesToFinalThird": 0,
    "forwardPasses": 0,
    "successfulForwardPasses": 0,
    "losses": 0,
    "ownHalfLosses": 0,
    "dangerousOwnHalfLosses": 0,
    "dribbles": 0,
    "receivedPass": 0,
    "shots": 0,
    "shotsOnTarget": 0,
    "xgShot": 0.0,
    "touchInBox": 0,
    "duels": 0,
    "duelsWon": 0,
    "defensiveDuels": 0,
    "defensiveDuelsWon": 0,
    "offensiveDuels": 0,
    "offensiveDuelsWon": 0,
    "aerialDuels": 0,
    "aerialDuelsWon": 0,
    "recoveries": 0,
    "opponentHalfRecoveries": 0,
    "counterpressingRecoveries": 0,
    "minutesOnField": 0,
}

_PROGRESSIVE = "progressive_pass"
_LOSS_TAGS = {"loss", "duel_lost"}
_WON_TAGS = {"duel_won"}


def _to_float(v: Any) -> float:
    try:
        return float(v) if v is not None else 0.0
    except (TypeError, ValueError):
        return 0.0


def events_to_players_stats(events: list[dict[str, Any]]) -> dict[str, Any]:
    """Roll events up into one row per player with cumulative `total` metrics."""
    by_player: dict[int, dict[str, Any]] = {}

    # Pass 1 — actor-side metrics + minute bookkeeping
    for ev in events:
        pid = (ev.get("player") or {}).get("id")
        if not pid:
            continue

        rec = by_player.get(pid)
        if rec is None:
            rec = {
                "playerId": pid,
                "name": (ev.get("player") or {}).get("name"),
                "position": (ev.get("player") or {}).get("position"),
                "teamId": (ev.get("team") or {}).get("id"),
                "_first_minute": None,
                "_last_minute": None,
                "total": dict(_INITIAL_TOTALS),
            }
            by_player[pid] = rec

        # Minute window
        minute = ev.get("minute")
        if minute is not None:
            try:
                m = int(minute)
                period = ev.get("matchPeriod")
                if period == "2H":
                    m += 45
                if rec["_first_minute"] is None or m < rec["_first_minute"]:
                    rec["_first_minute"] = m
                if rec["_last_minute"] is None or m > rec["_last_minute"]:
                    rec["_last_minute"] = m
            except (TypeError, ValueError):
                pass

        primary = (ev.get("type") or {}).get("primary")
        secondary = set((ev.get("type") or {}).get("secondary") or [])
        loc = ev.get("location") or {}
        x = loc.get("x")
        t = rec["total"]

        # Touches in opposition box (final-third penalty area proxy)
        if x is not None and x >= 83:
            t["touchInBox"] += 1

        # Pass metrics
        if primary == "pass":
            pd = ev.get("pass") or {}
            accurate = bool(pd.get("accurate"))
            t["passes"] += 1
            if accurate:
                t["successfulPasses"] += 1

            if _PROGRESSIVE in secondary:
                t["progressivePasses"] += 1
                if accurate:
                    t["successfulProgressivePasses"] += 1

            end = pd.get("endLocation") or {}
            end_x = end.get("x")
            if end_x is not None:
                if end_x >= 200 / 3:
                    t["passesToFinalThird"] += 1
                    if accurate:
                        t["successfulPassesToFinalThird"] += 1
                if x is not None and end_x > x + 5:
                    t["forwardPasses"] += 1
                    if accurate:
                        t["successfulForwardPasses"] += 1

            if not accurate:
                t["losses"] += 1
                if x is not None:
                    if x < 50:
                        t["ownHalfLosses"] += 1
                    if x < 100 / 3:
                        t["dangerousOwnHalfLosses"] += 1

        # Explicit loss / duel-lost tags
        if secondary & _LOSS_TAGS:
            t["losses"] += 1
            if x is not None:
                if x < 50:
                    t["ownHalfLosses"] += 1
                if x < 100 / 3:
                    t["dangerousOwnHalfLosses"] += 1

        # Shots
        if primary == "shot":
            t["shots"] += 1
            sh = ev.get("shot") or {}
            if sh.get("onTarget"):
                t["shotsOnTarget"] += 1
            t["xgShot"] += _to_float(sh.get("xg"))

        # Duels (defensive heuristic: ground_duel, offensive heuristic: when initiated by attacker — we
        # split via a 50/50 estimate since Wyscout secondary doesn't always tag direction).
        if primary in ("ground_duel", "aerial_duel"):
            t["duels"] += 1
            won = bool(secondary & _WON_TAGS)
            if won:
                t["duelsWon"] += 1
                t["recoveries"] += 1
                if x is not None and x > 50:
                    t["opponentHalfRecoveries"] += 1
                # crude counter-pressing proxy: any duel won in opp half within 5s of being out of possession
                # we don't have prev-event context here; skip and leave as 0
            if primary == "ground_duel":
                t["defensiveDuels"] += 1
                if won:
                    t["defensiveDuelsWon"] += 1
            else:
                t["aerialDuels"] += 1
                if won:
                    t["aerialDuelsWon"] += 1

        # Carries → dribbles (approximate)
        if primary == "carry":
            t["dribbles"] += 1

    # Pass 2 — receivedPass (this player was the recipient of an accurate pass)
    for ev in events:
        if (ev.get("type") or {}).get("primary") != "pass":
            continue
        pd = ev.get("pass") or {}
        if not pd.get("accurate"):
            continue
        rec_id = (pd.get("recipient") or {}).get("id")
        rec = by_player.get(rec_id) if rec_id else None
        if rec:
            rec["total"]["receivedPass"] += 1

    # Minutes-on-field from observed minute window. Clamp at 90.
    for rec in by_player.values():
        first = rec.get("_first_minute") or 0
        last = rec.get("_last_minute") or 0
        rec["total"]["minutesOnField"] = max(1, min(90, last - first + 1))
        rec.pop("_first_minute", None)
        rec.pop("_last_minute", None)

    players_list = sorted(by_player.values(), key=lambda r: r["playerId"])
    return {"players": players_list}
