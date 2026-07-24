# Security audit — 2026-07-24

## Scope and verdict

This review covered the tracked Git tree and history, Android file/network
configuration, FastAPI request handling, Docker network exposure, model artifact
loading, and direct Python dependencies.

- **Safe to publish the source repository:** yes, after the final squash into
  `main` and exclusion of internal workflow documents.
- **Safe for the documented private demo topology:** yes. The gateway is
  loopback-only and clients use SSH tunneling or ADB reverse.
- **Safe to expose directly to the public internet:** no. Authentication, TLS,
  rate limiting, and an internet-facing reverse proxy are intentionally outside
  this demo's scope.

## Repository confidentiality

- A high-confidence secret scan of the current tracked tree and every Git commit
  found no private key, provider token, API key, or credential pattern.
- `.env` files, virtual environments, model weights, model caches, APK/AAB files,
  and deployment archives are ignored by Git.
- The tracked `.env.gpu.example` contains paths and non-secret defaults only.
- The largest tracked file is the Uni-MuMER tokenizer at about 19.1 MiB. No
  checkpoint, adapter weight, APK, or archive is tracked.
- A provider IP found in an internal implementation plan was replaced with a
  placeholder. Internal `docs/superpowers` history must not be pushed as a public
  feature branch; the final public `main` should be created with a squash.

## Resolved findings

### SEC-001 — Gateway exposed on every host interface

- **Original severity:** High when the GPU VM has a public address.
- **Evidence:** `app/backend/docker-compose.gpu.yml`.
- **Impact:** The unauthenticated inference endpoint could consume GPU resources
  or process arbitrary uploads from the internet.
- **Resolution:** The published port now defaults to
  `127.0.0.1:8000:8000`, with an explicit
  `HMER_GATEWAY_BIND_ADDRESS` override. Deployment documentation prohibits a
  public bind without additional controls.
- **Verification:** deployment contract test and `docker compose config` both
  confirmed `host_ip: 127.0.0.1`.

### SEC-002 — Android allowed cleartext HTTP to every host

- **Original severity:** Medium.
- **Evidence:** `android-ui-project/app/src/main/AndroidManifest.xml`.
- **Impact:** A misconfigured API URL could send formula images over cleartext to
  a non-local host.
- **Resolution:** cleartext is denied by default and allowed only for
  `127.0.0.1`, `localhost`, and the emulator bridge `10.0.2.2`.
- **Verification:** `NetworkSecurityInstrumentedTest` confirms the two demo
  endpoints are allowed and an external hostname is denied.

### SEC-003 — Hugging Face remote code was not revision-pinned

- **Original severity:** High supply-chain risk.
- **Evidence:** `workers/unimumer/app/adapter.py` uses
  `trust_remote_code=True`.
- **Impact:** Resolving a changed or untrusted model repository could execute
  different Python code during model loading.
- **Resolution:** both processor and model loading now receive the vetted
  revision `40a6288292057f1c162b3b0eaccd362036dbd495`. The revision is
  configurable but has a secure recorded default, and the production deployment
  remains offline.
- **Verification:** `test_unimumer_adapter.py` checks that both calls receive the
  configured revision.

### SEC-004 — Vulnerable upload/image edge dependencies

- **Original severity:** High because uploaded images cross the trust boundary.
- **Evidence:** the original requirements pinned older `python-multipart`,
  Pillow, FastAPI, and Starlette releases.
- **Resolution:** gateway and worker edge dependencies were upgraded and pinned.
  The obsolete, unused Uni-MuMER environment snapshot was removed.
- **Verification:** `pip-audit` reports no known vulnerability in the direct
  gateway or Uni-MuMER requirements after the change.

### SEC-005 — Trusted TAMER checkpoint was checked for existence only

- **Original severity:** High if an attacker can replace the checkpoint.
- **Evidence:** TAMER loads a PyTorch Lightning checkpoint, and the legacy
  framework has known unsafe-deserialization advisories for untrusted
  checkpoints.
- **Resolution:** `verify_bundle.sh` now validates the recorded SHA-256 values for
  the TAMER checkpoint, dictionary, and Uni-MuMER adapter artifacts before
  deployment.
- **Verification:** the real local artifacts passed all four checksum checks and
  the preflight returned `BUNDLE_PREFLIGHT_OK`.

## Accepted residual risks

### RISK-001 — Legacy TAMER framework

- **Severity:** High for untrusted checkpoints; Low residual risk in the documented
  deployment.
- **Status:** `pytorch-lightning==1.9.5` remains because the trained checkpoint and
  runtime contract depend on that generation. `pip-audit` reports three
  advisories, including unsafe checkpoint deserialization.
- **Controls:** only the fixed project checkpoint is loaded; its SHA-256 is
  verified; the project volume is read-only; the worker is not published; the
  gateway is loopback-only.
- **Rule:** never load a user-supplied or newly downloaded `.ckpt` file. A
  Lightning/Torch migration requires a separate checkpoint-compatibility project
  and real-GPU validation.

### RISK-002 — Public service controls are not implemented

- **Severity:** High only if the loopback boundary is intentionally overridden.
- **Status:** no user authentication or rate limiting exists because this is a
  local/private demonstration stack.
- **Rule:** do not set `HMER_GATEWAY_BIND_ADDRESS=0.0.0.0` for an internet-facing
  deployment without authentication, TLS, rate limiting, and firewall/reverse
  proxy controls.

### RISK-003 — Audit coverage limitations

- CUDA-specific Torch/Torchvision builds were not resolvable by the PyPI audit
  service and were reported as skipped.
- Android dependencies are version-pinned and covered by Gradle build/lint/tests,
  but no separate mobile dependency CVE scanner was available in this review.
- The remote GPU host operating system, NVIDIA driver, Docker daemon, and cloud
  provider controls are outside the repository audit.

## Verification summary

- Backend regression after dependency upgrades: `32 passed, 1 skipped`.
- Gateway direct dependency audit: no known vulnerabilities.
- Uni-MuMER direct dependency audit: no known vulnerabilities; CUDA Torchvision
  was skipped by the audit service.
- TAMER direct dependency audit: three PyTorch Lightning advisories remain under
  the trusted-checkpoint controls above.
- Docker Compose configuration: valid and bound to `127.0.0.1`.
- Model artifact checksum preflight: passed.
- Android network-policy instrumentation test: passed.
- RTX 3090 Ti deployment: all three rebuilt containers are healthy; the gateway
  reports both models ready.
- Real inference: TAMER-A3 and Uni-MuMER both returned `mock: false` with valid
  LaTeX response contracts.
- External port check: connecting from the laptop to the GPU host's port 8000
  failed as expected; the same health endpoint remained available on server
  loopback.
