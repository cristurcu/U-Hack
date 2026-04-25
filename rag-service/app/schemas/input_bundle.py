from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, field_validator


class ModelOutputs(BaseModel):
    fusion: dict[str, Any] | None = None
    tacticalBaseline: dict[str, Any] | None = None
    tacticalIntelligence: dict[str, Any] | None = None
    decisionQuality: dict[str, Any] | None = None
    playerProfiles: list[dict[str, Any]] | None = None
    pressing: dict[str, Any] | None = None
    passingNetwork: dict[str, Any] | None = None
    passingNewtork: dict[str, Any] | None = None


class IndexOptions(BaseModel):
    vectorStore: str = Field(default="faiss")
    rebuild: bool = True
    topNPhases: int = Field(default=10, ge=1, le=100)
    includeDebugDocuments: bool = False


class IndexMatchRequest(BaseModel):
    matchId: int = Field(..., gt=0)
    teamId: int | None = Field(default=None, gt=0)
    teamName: str | None = None
    source: str = "generated_models"
    outputs: ModelOutputs
    options: IndexOptions = Field(default_factory=IndexOptions)

    @field_validator("teamName")
    @classmethod
    def _normalize_team_name(cls, value: str | None) -> str | None:
        if value is None:
            return value
        text = value.strip()
        return text or None

