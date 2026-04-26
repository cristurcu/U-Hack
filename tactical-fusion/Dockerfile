FROM python:3.12-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app

COPY requirements.txt /app/requirements.txt
COPY requirements-fastapi.txt /app/requirements-fastapi.txt
RUN pip install --no-cache-dir -r /app/requirements.txt -r /app/requirements-fastapi.txt

COPY . /app/

EXPOSE 8005

CMD ["uvicorn", "tactical_fusion.api.fastapi_app:app", "--host", "0.0.0.0", "--port", "8005"]
