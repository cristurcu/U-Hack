from __future__ import annotations

from app.core.config import Settings
from app.schemas.retrieval import RetrievedDocument
from app.vectorstores.faiss_reader import FaissReader


class RetrievalService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._reader = FaissReader(settings.storage_dir)

    def retrieve(
        self,
        *,
        match_id: int,
        query_vector: list[float],
        top_k: int,
        team_id: int | None = None,
        document_types: list[str] | None = None,
        min_score: float | None = None,
    ) -> tuple[list[RetrievedDocument], list[str]]:
        warnings: list[str] = []
        normalized_doc_types = {x.lower() for x in (document_types or [])}
        threshold = self._settings.rag_min_score_default if min_score is None else float(min_score)
        safe_top_k = max(1, min(top_k, self._settings.rag_top_k_max))
        candidate_k = min(max(safe_top_k * 5, safe_top_k), self._settings.rag_top_k_max * 10)

        candidates = self._reader.search(
            match_id=match_id,
            query_vector=query_vector,
            top_k=safe_top_k,
            candidate_k=candidate_k,
        )
        filtered = self._apply_filters(
            docs=candidates,
            team_id=team_id,
            doc_types=normalized_doc_types,
            min_score=threshold,
        )

        if not filtered and (team_id is not None or normalized_doc_types or min_score is not None):
            warnings.append("No results after filters. Retrying with relaxed filters.")
            filtered = self._apply_filters(
                docs=candidates,
                team_id=None,
                doc_types=set(),
                min_score=self._settings.rag_min_score_default,
            )

        deduped = self._dedupe_by_doc_id(filtered)
        return deduped[:safe_top_k], warnings

    def list_matches(self) -> list[dict]:
        return self._reader.list_matches()

    def list_sources(self, match_id: int, limit: int = 100) -> list[RetrievedDocument]:
        rows = self._reader.load_match_documents(match_id)
        docs = [
            RetrievedDocument(
                docId=str(row.get("docId")),
                matchId=int(row.get("matchId")),
                teamId=row.get("teamId"),
                teamName=row.get("teamName"),
                sourceService=str(row.get("sourceService")),
                documentType=str(row.get("documentType")),
                category=row.get("category"),
                title=row.get("title"),
                text=str(row.get("text", "")),
                metadata=row.get("metadata") or {},
                score=0.0,
            )
            for row in rows[: max(1, limit)]
        ]
        return docs

    @staticmethod
    def _apply_filters(
        *,
        docs: list[RetrievedDocument],
        team_id: int | None,
        doc_types: set[str],
        min_score: float,
    ) -> list[RetrievedDocument]:
        filtered: list[RetrievedDocument] = []
        for doc in docs:
            if team_id is not None and doc.teamId is not None and int(doc.teamId) != int(team_id):
                continue
            if doc_types and doc.documentType.lower() not in doc_types:
                continue
            if doc.score < min_score:
                continue
            filtered.append(doc)
        filtered.sort(key=lambda item: item.score, reverse=True)
        return filtered

    @staticmethod
    def _dedupe_by_doc_id(docs: list[RetrievedDocument]) -> list[RetrievedDocument]:
        seen: set[str] = set()
        result: list[RetrievedDocument] = []
        for doc in docs:
            if doc.docId in seen:
                continue
            seen.add(doc.docId)
            result.append(doc)
        return result

