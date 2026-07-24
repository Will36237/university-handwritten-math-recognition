# Backend architecture

```text
Android phone / emulator
       |
       | GET /health, POST /predict
       v
Gateway :8000
       |-- model=tamer_a3 ------> TAMER worker :8101 (legacy PyTorch environment)
       `-- model=unimumer_lora -> Uni-MuMER worker :8102 (Transformers environment)
```

The gateway owns the public API contract, validates the upload, assigns a request ID,
and routes it to exactly one worker. Images are transferred in memory and are not
persisted. Each GPU worker has an independent dependency file so incompatible model
runtimes do not share a Python environment.

`backend/app` is retained as the legacy single-process mock API for reference. New
deployment work should use `gateway` and `workers`.
