# Tactical RAG Service

Serviciu FastAPI pentru hackathon, cu 2 capabilitati:
- indexare documente tactice din output-uri model;
- query RAG peste FAISS + generare raspuns (OpenAI sau fallback local).

## Contract De Integrare (Cine Trimite Request-uri Cui)

1. `Data producer` (pipeline/orchestrator/backend) trimite catre acest serviciu:
- `POST /index/match` cu output-urile modelelor (`fusion`, `tacticalBaseline`, `decisionQuality`, etc.).

2. `Frontend` (sau BFF-ul frontendului) trimite catre acest serviciu:
- `POST /rag/query` cu `sessionId`, `question`, `matchId` (minim).
- optional filtre: `teamId`, `documentTypes`, `topK`, `minScore`.

3. Acest serviciu trimite extern doar catre:
- OpenAI Responses API (doar daca `RAG_LLM_PROVIDER=openai` si `OPENAI_API_KEY` este setat).

4. Persistenta locala:
- FAISS + metadate JSON in `storage/vector_store/match_<matchId>/`.

## Rulare Locala

```powershell
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload
```

Swagger:
- `http://127.0.0.1:8000/docs`

## Endpoint Contract

### `GET /health`

Response:
```json
{
  "status": "ok",
  "service": "tactical-rag-service"
}
```

### `POST /documents/build`

Construieste documente RAG din output-uri, fara sa salveze in FAISS.

Request body (`IndexMatchRequest`):
```json
{
  "matchId": 900000000,
  "teamId": 14,
  "teamName": "Universitatea Cluj",
  "source": "generated_models",
  "outputs": {
    "fusion": {},
    "tacticalBaseline": {},
    "tacticalIntelligence": {},
    "decisionQuality": {},
    "playerProfiles": [],
    "pressing": {},
    "passingNetwork": {}
  },
  "options": {
    "vectorStore": "faiss",
    "rebuild": true,
    "topNPhases": 10,
    "includeDebugDocuments": false
  }
}
```

Note contract:
- `matchId` obligatoriu (`>0`).
- `outputs` obligatoriu ca obiect; campurile interne pot lipsi.
- compatibilitate typo legacy: `passingNewtork` este acceptat.

Response (`BuildDocumentsResponse`):
```json
{
  "matchId": 900000000,
  "teamId": 14,
  "teamName": "Universitatea Cluj",
  "documentsCreated": 42,
  "warnings": [
    "playerProfiles output missing."
  ],
  "documents": [
    {
      "docId": "match_900000000_...",
      "matchId": 900000000,
      "teamId": 14,
      "teamName": "Universitatea Cluj",
      "sourceService": "tactical-baseline-service",
      "documentType": "baseline_summary",
      "category": "baseline",
      "title": "Tactical baseline summary",
      "text": "....",
      "metadata": {}
    }
  ]
}
```

### `POST /index/match`

Indexare completa: build documente + embeddings + persistenta FAISS.

Request body:
- acelasi contract ca `POST /documents/build`.

Response (`IndexMatchResponse`):
```json
{
  "matchId": 900000000,
  "teamId": 14,
  "teamName": "Universitatea Cluj",
  "status": "indexed",
  "documentsCreated": 42,
  "embeddingsCreated": 42,
  "vectorStore": "faiss",
  "collectionName": "match_900000000",
  "documentTypes": {
    "baseline_summary": 1,
    "player_priority": 2
  },
  "warnings": [],
  "documentPreview": [
    {
      "docId": "match_900000000_...",
      "type": "baseline_summary",
      "textPreview": "..."
    }
  ]
}
```

### `GET /index/matches`

Response:
```json
[
  {
    "matchId": 900000000,
    "collectionName": "match_900000000",
    "documentsCount": 42,
    "vectorStore": "faiss"
  }
]
```

### `GET /index/matches/{matchId}/documents`

Response:
- lista de `RagDocument` pentru meciul respectiv.

### `POST /rag/query`

Endpointul principal pentru frontend.

Request body (`RagQueryRequest`):
```json
{
  "sessionId": "9f9b7f1c-0d0d-4ea0-94be-8f9b0e4dd7ba",
  "question": "Care au fost principalele riscuri tactice?",
  "matchId": 900000000,
  "teamId": 14,
  "topK": 4,
  "documentTypes": [
    "baseline_summary",
    "pressing_summary"
  ],
  "minScore": 0.1,
  "includeDebug": false
}
```

Campuri:
- obligatorii: `sessionId`, `question`, `matchId`.
- optionale: `teamId`, `topK`, `documentTypes`, `minScore`, `includeDebug`.

Request minim recomandat pentru frontend:
```json
{
  "sessionId": "uuid-generat-in-frontend",
  "question": "Care au fost principalele riscuri tactice?",
  "matchId": 900000000
}
```

Response (`RagQueryResponse`):
```json
{
  "sessionId": "9f9b7f1c-0d0d-4ea0-94be-8f9b0e4dd7ba",
  "matchId": 900000000,
  "answer": "Observatii:\n- ...",
  "retrievedCount": 4,
  "sources": [
    {
      "docId": "match_900000000_...",
      "documentType": "baseline_summary",
      "title": "Tactical baseline summary",
      "score": 0.2015,
      "sourceService": "tactical-baseline-service"
    }
  ],
  "warnings": [],
  "latencyMs": 842,
  "model": "gpt-5-mini"
}
```

### `POST /rag/query/debug`

Acelasi input ca `/rag/query`, dar include extra:
- `context`
- `systemPrompt`
- `retrievedDocuments`

### `GET /rag/matches`

Lista meciurilor disponibile pentru retrieval (citite din storage vectorial).

### `GET /rag/matches/{matchId}/sources?limit=100`

Returneaza documentele brute ale meciului (fara generare raspuns LLM).

### `GET /rag/sessions/{sessionId}/history`

Returneaza istoricul din memorie pentru sesiune (maxim ultimele 5 mesaje, configurabil).

### `POST /rag/sessions/{sessionId}/reset`

Goleste istoricul sesiunii.

Response:
```json
{
  "sessionId": "9f9b7f1c-0d0d-4ea0-94be-8f9b0e4dd7ba",
  "cleared": true,
  "messagesRemoved": 5
}
```

## Erori Si Validare

- `422` pentru input invalid (ex: lipsa `sessionId` sau `matchId <= 0`).
- Erori aplicatie (bad request/internal) au format:
```json
{
  "error": "Mesaj eroare",
  "details": "optional"
}
```

## Variabile De Mediu

```text
APP_ENV=dev
VECTOR_STORE=faiss
STORAGE_DIR=./storage/vector_store
EMBEDDING_PROVIDER=sentence_transformers
EMBEDDING_MODEL=sentence-transformers/all-MiniLM-L6-v2
MAX_PHASE_DOCUMENTS=10
MAX_PLAYER_DOCUMENTS=10
RAG_TOP_K_DEFAULT=4
RAG_TOP_K_MAX=20
RAG_CONTEXT_MAX_CHARS=5000
RAG_HISTORY_MAX_MESSAGES=5
RAG_HISTORY_MESSAGE_MAX_CHARS=800
RAG_MIN_SCORE_DEFAULT=-1
RAG_LLM_PROVIDER=openai
RAG_OPENAI_MODEL=gpt-5-mini
RAG_OPENAI_REASONING_EFFORT=none
RAG_OPENAI_TIMEOUT_SEC=20
OPENAI_API_KEY=your_key_here
```

## Smoke Test

```powershell
.\.venv\Scripts\python .\smoke_test.py
.\.venv\Scripts\python .\smoke_test_rag.py
```

## Storage Layout

```text
storage/
  vector_store/
    match_<matchId>/
      index.faiss
      documents.json
```
