FROM python:3.12-slim

WORKDIR /app

COPY wyscout_mock_server.py /app/wyscout_mock_server.py
COPY data/ /app/data/

ENV PYTHONUNBUFFERED=1
EXPOSE 5001

ENTRYPOINT ["python", "wyscout_mock_server.py"]
CMD ["-i", "/app/data/match.json", "-p", "5001", "-s", "10"]
