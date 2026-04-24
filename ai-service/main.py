from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from loader import load_match
from insights_network.passing_network import build_passing_network
from insights_network.schemas import PassingNetworkResponse

app = FastAPI(title="U-Hack AI Service")

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
    element = load_match()

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
