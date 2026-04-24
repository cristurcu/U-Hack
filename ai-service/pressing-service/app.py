from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from models import PressingRequest, PressingResponse
from pressing import analyze_pressing

app = FastAPI(title="Pressing Analysis Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/analyze/pressing", response_model=PressingResponse)
def analyze(request: PressingRequest):
    return analyze_pressing(request)
