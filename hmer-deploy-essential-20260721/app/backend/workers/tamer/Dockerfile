FROM nvidia/cuda:11.8.0-cudnn8-runtime-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 python3-pip libglib2.0-0 libgl1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv/backend
COPY workers/tamer/requirements.txt /tmp/requirements.txt
RUN python3 -m pip install --no-cache-dir -r /tmp/requirements.txt
COPY workers ./workers
COPY shared ./shared

EXPOSE 8101
CMD ["python3", "-m", "uvicorn", "workers.tamer.app.main:app", "--host", "0.0.0.0", "--port", "8101"]
