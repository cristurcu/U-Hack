from __future__ import annotations

import json
import shutil
from pathlib import Path
from typing import Any

import numpy as np

from app.core.errors import InternalServerError
from app.services.embedding_service import EmbeddedDocument


class FaissStore:
    def __init__(self, storage_dir: Path) -> None:
        self._storage_dir = storage_dir
        self._storage_dir.mkdir(parents=True, exist_ok=True)

    def save_match_index(
        self,
        *,
        match_id: int,
        embedded_documents: list[EmbeddedDocument],
        rebuild: bool,
    ) -> None:
        if not embedded_documents:
            raise InternalServerError("Cannot save FAISS index: no embedded documents.")

        match_dir = self._match_dir(match_id)
        if match_dir.exists():
            if not rebuild:
                raise InternalServerError(
                    f"Index for match {match_id} already exists and rebuild=false."
                )
            shutil.rmtree(match_dir)
        match_dir.mkdir(parents=True, exist_ok=True)

        vectors = np.array([item.embedding for item in embedded_documents], dtype="float32")
        if vectors.ndim != 2 or vectors.shape[0] == 0 or vectors.shape[1] == 0:
            raise InternalServerError("Invalid vectors matrix for FAISS save.")

        try:
            import faiss
        except Exception as exc:  # pragma: no cover - dependency error path
            raise InternalServerError("faiss-cpu is not installed.") from exc

        try:
            faiss.normalize_L2(vectors)
            index = faiss.IndexFlatIP(int(vectors.shape[1]))
            index.add(vectors)
            faiss.write_index(index, str(match_dir / "index.faiss"))
        except Exception as exc:
            raise InternalServerError(f"Failed to save FAISS index for match {match_id}.") from exc

        serialized_docs: list[dict[str, Any]] = []
        for i, item in enumerate(embedded_documents):
            row = item.document.model_dump()
            row["vectorId"] = i
            serialized_docs.append(row)
        with (match_dir / "documents.json").open("w", encoding="utf-8") as f:
            json.dump(serialized_docs, f, ensure_ascii=False, indent=2)

    def load_match_documents(self, match_id: int) -> list[dict[str, Any]]:
        path = self._match_dir(match_id) / "documents.json"
        if not path.exists():
            raise FileNotFoundError(f"No documents found for match {match_id}.")
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, list):
            raise InternalServerError(f"documents.json malformed for match {match_id}.")
        return data

    def list_indexes(self) -> list[dict[str, Any]]:
        entries: list[dict[str, Any]] = []
        for path in self._storage_dir.glob("match_*"):
            if not path.is_dir():
                continue
            match_id = self._parse_match_id(path.name)
            if match_id is None:
                continue
            docs_path = path / "documents.json"
            count = 0
            if docs_path.exists():
                try:
                    with docs_path.open("r", encoding="utf-8") as f:
                        content = json.load(f)
                    if isinstance(content, list):
                        count = len(content)
                except json.JSONDecodeError:
                    count = 0
            entries.append(
                {
                    "matchId": match_id,
                    "collectionName": path.name,
                    "documentsCount": count,
                    "vectorStore": "faiss",
                }
            )
        entries.sort(key=lambda item: item["matchId"])
        return entries

    def delete_match_index(self, match_id: int) -> bool:
        path = self._match_dir(match_id)
        if not path.exists():
            return False
        shutil.rmtree(path)
        return True

    def _match_dir(self, match_id: int) -> Path:
        return self._storage_dir / f"match_{match_id}"

    @staticmethod
    def _parse_match_id(name: str) -> int | None:
        if not name.startswith("match_"):
            return None
        raw = name.replace("match_", "", 1)
        try:
            return int(raw)
        except ValueError:
            return None

