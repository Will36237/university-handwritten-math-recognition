FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /srv/backend
COPY gateway/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt
COPY gateway ./gateway
COPY shared ./shared

USER 65532:65532
EXPOSE 8000
CMD ["python", "-m", "uvicorn", "gateway.app.main:app", "--host", "0.0.0.0", "--port", "8000"]
