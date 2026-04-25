FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential gcc \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY . /app/

ENV PYTHONUNBUFFERED=1 PYTHONPATH=/app/src
EXPOSE 8003

CMD ["uvicorn", "tactical_baseline.api_app:app", "--host", "0.0.0.0", "--port", "8003"]