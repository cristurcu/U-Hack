from pydantic import BaseModel
from typing import Optional


# ── Request ────────────────────────────────────────────────────────────

class EventType(BaseModel):
    primary: str
    secondary: list[str] = []


class Location(BaseModel):
    x: float
    y: float


class Team(BaseModel):
    id: int
    name: str


class Player(BaseModel):
    id: int
    name: str
    position: Optional[str] = None


class Event(BaseModel):
    id: int
    matchPeriod: str
    minute: int
    second: int
    type: EventType
    location: Location
    team: Team
    player: Player


class PressingRequest(BaseModel):
    matchLabel: str
    teamId: int
    events: list[Event]


# ── Response ───────────────────────────────────────────────────────────

class PlayerPressing(BaseModel):
    name: str
    position: Optional[str]
    pressingDuels: int
    won: int
    efficiency: float
    inOpponentHalf: int
    intensityDrop: float


class PressingResponse(BaseModel):
    matchLabel: str
    teamPressingEfficiency: float
    firstHalfEfficiency: float
    secondHalfEfficiency: float
    intensityDrop: float
    insight: str
    players: list[PlayerPressing]
    topPresser: Optional[str]
