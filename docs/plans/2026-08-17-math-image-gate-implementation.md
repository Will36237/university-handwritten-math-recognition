# Math Image Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject images without mathematical expressions before Uni-MuMER LoRA generates LaTeX, while accepting handwritten, printed, and screen-displayed mathematics.

**Architecture:** Load official `Qwen/Qwen3.5-2B` as a dedicated validator inside the existing Uni-MuMER worker, because the recognizer checkpoint is already a full HMER fine-tune. Run a short deterministic three-label classification, then run the existing Uni-MuMER LoRA OCR only for `MATH`; propagate stable errors through the unchanged gateway contract to Android.

**Tech Stack:** Python 3.12, FastAPI, Transformers 5.5.4, PEFT 0.19.1, pytest 8.4.1, Kotlin, Android/JUnit, Gradle.

## Global Constraints

- Accept handwritten, printed, and computer-screen mathematical expressions.
- Reject prose, ordinary handwriting, animals, objects, people, scenery, and ambiguous or illegible non-mathematical content.
- Pin official `Qwen/Qwen3.5-2B` revision `15852e8c16360a2fea060d615a32b45270f8a8fc` as the validator; do not add another container or network service.
- Keep TAMER, the success response schema, and Android model-selection behavior unchanged.
- Work only in the Codex worktree; do not modify the primary local directory or push GitHub.
- Add production behavior only after observing the corresponding test fail.

---

### Task 1: Uni-MuMER semantic gate

**Files:**
- Modify: `hmer-deploy-essential-20260721/app/backend/tests/test_settings.py`
- Modify: `hmer-deploy-essential-20260721/app/backend/tests/test_deployment_contract.py`
- Modify: `hmer-deploy-essential-20260721/app/backend/shared/settings.py`
- Modify: `hmer-deploy-essential-20260721/app/backend/workers/unimumer/app/main.py`
- Modify: `hmer-deploy-essential-20260721/app/backend/.env.gpu.example`
- Modify: `hmer-deploy-essential-20260721/app/backend/docker-compose.gpu.yml`
- Modify: `hmer-deploy-essential-20260721/app/backend/verify_bundle.sh`
- Modify: `hmer-deploy-essential-20260721/app/backend/tests/test_unimumer_adapter.py`
- Modify: `hmer-deploy-essential-20260721/app/backend/workers/unimumer/app/adapter.py`

**Interfaces:**
- Produces: validator settings, `MathImageDecision`, `parse_math_image_decision(output: str) -> MathImageDecision`, and a gated `UniMumerLoraAdapter.predict(image: Image.Image) -> str`.
- Raises: `ApiError(422, "NON_MATH_IMAGE", ...)` for `NON_MATH` and `UNCERTAIN`; `ApiError(503, "IMAGE_CLASSIFIER_UNAVAILABLE", ...)` for malformed classifier output.

- [ ] **Step 1: Write failing validator configuration and deployment tests**

Assert defaults and overrides for `HMER_MATH_CLASSIFIER_MODEL` and
`HMER_MATH_CLASSIFIER_REVISION`, compose forwarding, and bundle preflight
requirements for the pinned official Qwen snapshot.

- [ ] **Step 2: Run focused configuration tests and verify RED**

Run `pytest tests/test_settings.py tests/test_deployment_contract.py -q` and
confirm failure because validator settings and deployment variables are absent.

- [ ] **Step 3: Implement validator configuration and deployment wiring**

Add the two immutable settings fields, pass them to `UniMumerLoraAdapter`, expose
them in `.env.gpu.example` and compose, and require the official snapshot's
`model.safetensors-00001-of-00001.safetensors` in `verify_bundle.sh`.

- [ ] **Step 4: Run focused configuration tests and verify GREEN**

Expected: settings and deployment contract tests pass.

- [ ] **Step 5: Write failing parser tests**

Add parameterized tests proving that whitespace and case normalize to the three enum labels, while explanatory or unknown text raises `IMAGE_CLASSIFIER_UNAVAILABLE`.

```python
@pytest.mark.parametrize(
    ("output", "expected"),
    [("MATH", MathImageDecision.MATH), (" non_math\n", MathImageDecision.NON_MATH), ("uncertain", MathImageDecision.UNCERTAIN)],
)
def test_parse_math_image_decision_accepts_only_contract_labels(output, expected):
    assert parse_math_image_decision(output) is expected
```

- [ ] **Step 6: Run parser tests and verify RED**

Run from `hmer-deploy-essential-20260721/app/backend`:

```powershell
.\hmer_ui\Scripts\python.exe -m pytest tests/test_unimumer_adapter.py -q
```

Expected: collection/import failure because `MathImageDecision` and `parse_math_image_decision` do not exist.

- [ ] **Step 7: Implement the minimal strict parser**

Add the enum, classification prompt, strict normalization, and stable malformed-output error. The prompt must describe all accepted and rejected categories and request exactly one of `MATH`, `NON_MATH`, or `UNCERTAIN`.

- [ ] **Step 8: Run parser tests and verify GREEN**

Run the same pytest command. Expected: parser tests pass.

- [ ] **Step 9: Write failing adapter orchestration tests**

Use separate fake validator and recognizer models/processors to prove:

```python
assert adapter.predict(image) == r"x^2"
assert generated_prompts == [MATH_IMAGE_CLASSIFICATION_PROMPT, LATEX_RECOGNITION_PROMPT]
```

Also prove `NON_MATH` and `UNCERTAIN` raise HTTP-domain code `NON_MATH_IMAGE` before the recognition prompt is generated, and malformed output maps to `IMAGE_CLASSIFIER_UNAVAILABLE`.

- [ ] **Step 10: Run orchestration tests and verify RED**

Expected: tests fail because prediction still runs only the existing LaTeX prompt.

- [ ] **Step 11: Implement separate-validator classification and OCR**

Refactor generation into one private helper accepting an explicit processor and model. Hold the existing lock across both generations, use the official Qwen validator for classification with a short output limit, and run the existing LoRA recognizer only after a `MATH` decision.

- [ ] **Step 12: Run adapter tests and verify GREEN**

Expected: all `test_unimumer_adapter.py` tests pass without loading real weights.

### Task 2: Backend error propagation contract

**Files:**
- Modify: `hmer-deploy-essential-20260721/app/backend/tests/test_gateway_worker_client.py`

**Interfaces:**
- Consumes: worker error `{error: {code: "NON_MATH_IMAGE", message: ...}}`.
- Produces: gateway `ApiError` retaining HTTP 422, code, and message.

- [ ] **Step 1: Add the failing/characterization test**

Add a transport handler returning HTTP 422 with `NON_MATH_IMAGE`, call `WorkerClient.predict`, and assert the captured `ApiError` preserves status, code, and message.

- [ ] **Step 2: Run the focused test**

```powershell
.\hmer_ui\Scripts\python.exe -m pytest tests/test_gateway_worker_client.py -q
```

Expected: pass if the existing generic propagation contract already covers the new code. No production gateway edit is allowed unless this test exposes a real gap.

### Task 3: Android error mapping

**Files:**
- Create: `android-ui-project/app/src/test/java/vn/edu/fpt/hmerdemo/ui/ErrorHandlingTest.kt`
- Modify: `android-ui-project/app/src/main/java/vn/edu/fpt/hmerdemo/ui/ErrorHandling.kt`

**Interfaces:**
- Consumes: backend code `NON_MATH_IMAGE` parsed through `HmerApiException`.
- Produces: `HmerErrorCode.NON_MATH_IMAGE.toUiError()` with non-retry guidance to capture or crop a mathematical expression.

- [ ] **Step 1: Write the failing Android unit test**

```kotlin
@Test
fun nonMathImageExplainsHowToCaptureAFormula() {
    val error = HmerErrorCode.NON_MATH_IMAGE.toUiError()
    assertEquals("Không tìm thấy công thức toán", error.title)
    assertFalse(error.canRetry)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat --console=plain testDebugUnitTest --tests "vn.edu.fpt.hmerdemo.ui.ErrorHandlingTest"
```

Expected: Kotlin compilation fails because `NON_MATH_IMAGE` does not exist.

- [ ] **Step 3: Add the enum member and localized mapping**

Add `NON_MATH_IMAGE` next to `NO_FORMULA_CONTENT`, with the title, message, and suggestion fixed by the approved spec and `canRetry = false`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Expected: `ErrorHandlingTest` passes.

### Task 4: Full verification and review checkpoint

**Files:**
- No additional production files.

**Interfaces:**
- Produces: a worktree-only implementation ready for user GPU/manual testing.

- [ ] **Step 1: Run the complete backend test suite**

```powershell
.\hmer_ui\Scripts\python.exe -m pytest .\tests -q
```

Expected: zero failed tests.

- [ ] **Step 2: Run Android unit tests, lint, and debug build**

```powershell
.\gradlew.bat --console=plain testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL` and zero lint errors.

- [ ] **Step 3: Check formatting and change scope**

Run `git diff --check`, inspect `git diff --stat`, and confirm unrelated existing Android label/test edits and untracked report files were not altered by this feature.

- [ ] **Step 4: Refresh CodeGraph and inspect affected tests**

Run `codegraph sync .`, `codegraph status .`, and `codegraph affected` for the modified production files. Expected: index up to date and affected test list includes the new adapter and Android error tests where supported.

- [ ] **Step 5: Commit only feature files**

Stage only the adapter, its tests, gateway contract test, Android error mapping, Android error test, and this plan. Commit them on the worktree branch without pushing.
