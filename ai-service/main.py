from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from loader import load_match
from insights_network.passing_network import build_passing_network
from insights_network.schemas import PassingNetworkResponse
from insights_player_profile.player_profile import build_player_profile
from insights_player_profile.schemas import PlayerProfileResponse
from insights_pressing.pressing import build_pressing
from insights_pressing.schemas import PressingResponse


@asynccontextmanager
async def _lifespan(app: FastAPI):
    try:
        from live_publisher import maybe_start
        app.state.publisher = maybe_start()
    except Exception as e:
        import logging
        logging.getLogger(__name__).warning("publisher not started: %s", e)
        app.state.publisher = None
    yield
    p = getattr(app.state, "publisher", None)
    if p is not None:
        p.stop()


app = FastAPI(title="U-Hack AI Service", lifespan=_lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"ok": True}


@app.get("/insights/passing-network/{match_id}", response_model=PassingNetworkResponse)
def passing_network(
    match_id: int,
    team_id: int = Query(..., description="Team ID to build the network for"),
    period: str = Query("full", pattern="^(full|1H|2H)$"),
    until_first_sub: bool = True,
    min_passes: int = Query(2, ge=1),
):
    element = load_match(match_id=match_id)

    if element["match"]["wyId"] != match_id:
        raise HTTPException(404, f"match {match_id} not found in mock data")

    team_info = (element.get("teams") or {}).get(str(team_id))
    if not team_info:
        raise HTTPException(404, f"team {team_id} not in match")

    result = build_passing_network(
        events=element["events"],
        team_id=team_id,
        substitutions=element.get("substitutions"),
        period=period,
        until_first_sub=until_first_sub,
        min_passes=min_passes,
    )

    return {
        "match_id": match_id,
        "team": {"id": team_id, "name": team_info["name"]},
        "period": period,
        "cutoff_minute": result["cutoff_minute"],
        "nodes": result["nodes"],
        "edges": result["edges"],
    }


@app.get("/insights/player-profile/{match_id}", response_model=PlayerProfileResponse)
def player_profile(
    match_id: int,
    player_id: int = Query(..., description="Player ID to build the heatmap for"),
    period: str = Query("full", pattern="^(full|1H|2H)$"),
    grid_cols: int = Query(12, ge=2, le=40),
    grid_rows: int = Query(8, ge=2, le=30),
):
    element = load_match(match_id=match_id)

    if element["match"]["wyId"] != match_id:
        raise HTTPException(404, f"match {match_id} not found in mock data")

    result = build_player_profile(
        events=element["events"],
        player_id=player_id,
        period=period,
        grid_cols=grid_cols,
        grid_rows=grid_rows,
    )
    if result is None:
        raise HTTPException(404, f"player {player_id} not found in match events")

    return {
        "match_id": match_id,
        "period": period,
        "player": result["player"],
        "team": result["team"],
        "grid": result["grid"],
        "stats": result["stats"],
    }


@app.get("/insights/pressing/{match_id}", response_model=PressingResponse)
def pressing(
    match_id: int,
    team_id: int = Query(..., description="Team ID to analyse pressing for"),
    period: str = Query("full", pattern="^(full|1H|2H)$"),
):
    element = load_match(match_id=match_id)

    if element["match"]["wyId"] != match_id:
        raise HTTPException(404, f"match {match_id} not found in mock data")

    team_info = (element.get("teams") or {}).get(str(team_id))
    if not team_info:
        raise HTTPException(404, f"team {team_id} not in match")

    result = build_pressing(
        events=element["events"],
        team_id=team_id,
        period=period,
    )

    return {
        "match_id": match_id,
        "team_id": team_id,
        "period": period,
        **result,
    }
