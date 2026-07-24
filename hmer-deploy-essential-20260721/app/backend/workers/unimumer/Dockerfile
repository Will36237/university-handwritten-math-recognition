FROM pytorch/pytorch:2.7.1-cuda12.6-cudnn9-runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/opt/huggingface

WORKDIR /srv/backend
COPY workers/unimumer/requirements.txt /tmp/requirements.txt
RUN python -m pip install --no-cache-dir -r /tmp/requirements.txt
COPY workers ./workers
COPY shared ./shared

EXPOSE 8102
CMD ["python", "-m", "uvicorn", "workers.unimumer.app.main:app", "--host", "0.0.0.0", "--port", "8102"]
