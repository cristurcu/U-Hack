from __future__ import annotations

from typing import Any

from app.schemas.documents import RagDocument
from app.utils.ids import build_doc_id


def make_doc(
    *,
    match_id: int,
    team_id: int | None,
    team_name: str | None,
    source_service: str,
    document_type: str,
    text: str,
    slug: str | None = None,
    title: str | None = None,
    category: str | None = None,
    metadata: dict[str, Any] | None = None,
) -> RagDocument:
    return RagDocument(
        docId=build_doc_id(match_id=match_id, source=source_service, doc_type=document_type, slug=slug),
        matchId=match_id,
        teamId=team_id,
        teamName=team_name,
        sourceService=source_service,
        documentType=document_type,
        category=category,
        title=title,
        text=text.strip(),
        metadata=metadata or {},
    )

