# Math Image Gate Design

## Goal

Prevent Uni-MuMER LoRA from inventing LaTeX for images that do not contain a
mathematical expression. The application accepts mathematical expressions that
are handwritten, printed, or displayed on a computer screen. It rejects normal
prose, ordinary handwriting, animals, objects, people, scenery, and other
non-mathematical images.

## Existing behavior and cause

The gateway currently validates file size, format, dimensions, contrast, and
stroke detail. Those checks reject corrupt, blank, or nearly smooth crops, but
ordinary text still contains enough strokes to pass. The Uni-MuMER prompt then
assumes that every accepted image contains a formula, so the model may generate
repeated mathematical tokens for an unrelated image.

Uni-MuMER already loads a Qwen3.5-2B multimodal base model and attaches a LoRA
adapter. Loading a second Qwen model would duplicate weights, increase startup
time, and consume additional GPU memory. The gate therefore reuses the loaded
base model with the LoRA adapter temporarily disabled.

## Accepted and rejected inputs

The semantic gate uses three decisions:

- `MATH`: at least one visible mathematical expression, equation, inequality,
  arithmetic calculation, symbolic derivation, matrix, integral, fraction, or
  comparable mathematical notation is the main subject of the image.
- `NON_MATH`: prose, an isolated ordinary word, an ordinary handwritten note,
  animal, object, person, landscape, UI screenshot without a mathematical
  expression, or any other non-mathematical content.
- `UNCERTAIN`: the content is too ambiguous, occluded, or illegible to decide
  safely.

Only `MATH` proceeds to Uni-MuMER LoRA. Both `NON_MATH` and `UNCERTAIN` are
rejected rather than allowing the recognizer to hallucinate a result.

## Architecture

The request flow remains Android -> gateway -> Uni-MuMER worker. The gateway
keeps its existing deterministic image checks and error forwarding. The
Uni-MuMER worker performs the new semantic gate immediately before recognition:

1. Decode and validate the uploaded image using the existing shared validator.
2. Acquire the Uni-MuMER adapter lock for the complete semantic-check and OCR
   sequence.
3. Disable the LoRA adapter and ask the Qwen3.5 base model for exactly one of
   `MATH`, `NON_MATH`, or `UNCERTAIN` using deterministic generation.
4. Parse the answer strictly. Only the exact normalized token `MATH` is accepted.
5. Re-enable the existing LoRA adapter and run the current LaTeX prompt when the
   decision is `MATH`.
6. Return the existing successful prediction contract unchanged.

The semantic gate is contained in the Uni-MuMER adapter because it shares that
model's processor, weights, GPU device, and lock. TAMER behavior remains
unchanged.

## Error contract and Android behavior

The Uni-MuMER worker returns HTTP 422 with a stable `NON_MATH_IMAGE` code for
both `NON_MATH` and `UNCERTAIN`. The response message tells the user to capture
or crop a mathematical expression. The gateway already forwards worker error
codes and messages without changing them.

Android adds `NON_MATH_IMAGE` to `HmerErrorCode` and maps it to localized UI
copy:

- Title: `Không tìm thấy công thức toán`
- Message: `Ảnh không chứa công thức toán đủ rõ để nhận dạng.`
- Action: `Hãy chụp hoặc cắt sát một công thức toán rồi thử lại.`

If Qwen returns an output outside the three-label contract or classification
itself fails, the worker returns HTTP 503 with `IMAGE_CLASSIFIER_UNAVAILABLE`.
This operational failure is not mislabeled as a bad user image.

## Concurrency and resource safety

The same PEFT model instance switches between base inference and LoRA inference,
so both operations execute under the existing adapter lock. No request can
enable or disable the adapter while another request is generating. The design
does not load a second model and does not introduce a new container or network
service.

Classification uses deterministic decoding, a short output limit, and no
sampling. The existing prediction latency includes both the semantic check and
LaTeX generation, making the added cost visible to clients.

## Testing strategy

Automated tests are added before production changes:

1. Unit tests for strict decision parsing: accepted labels, whitespace/case
   normalization, and rejection of explanatory or malformed output.
2. Uni-MuMER adapter tests with fake processor/model objects proving that
   `MATH` runs LoRA recognition while `NON_MATH` and `UNCERTAIN` stop before OCR.
3. Worker/gateway contract tests proving that `NON_MATH_IMAGE` remains HTTP 422
   and reaches the client unchanged.
4. Android unit tests for the new error-code mapping and user guidance.
5. Existing backend mock tests and Android unit, lint, and debug build checks.

GPU acceptance testing uses a small labeled image set containing handwritten
math, printed math, math shown on a monitor, ordinary printed text, ordinary
handwriting, animals, objects, scenery, blank images, and ambiguous crops. The
feature is not considered ready for the primary local directory until those
tests demonstrate that all required positive examples pass and all required
negative examples are rejected.

## Scope boundaries

- No second Qwen model or validator service is introduced.
- No model weights are changed or retrained in this change.
- No TAMER behavior or Android model-selection UI is changed.
- No changes are made to the primary local directory or GitHub until the user
  validates this worktree implementation.
