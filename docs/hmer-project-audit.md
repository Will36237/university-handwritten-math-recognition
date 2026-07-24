# HMER project refactor audit

Ngày xác nhận: 2026-07-24

Nhánh: `codex/refactor-hmer-project`
Mốc trước refactor: `refactor/safety-net`

## Kết luận

CodeGraph liệt kê 34 file Python trong `app/hmer-project`: 29 file production và
5 file test. Audit này bao phủ đủ 29/29 file production.

Refactor chỉ thay đổi 8 file production có vấn đề cô lập được. Các parser,
attention, transformer, encoder/decoder và beam-search kernel còn lại được giữ
nguyên có chủ đích vì thứ tự phép toán, tensor shape, state-dict key và sai số số
học là một phần của hợp đồng checkpoint. Việc không viết lại các file đó là quyết
định tương thích, không phải bỏ sót code rác.

Trạng thái dùng trong bảng:

- `modified`: đã sửa bằng commit nhỏ và có regression test.
- `tested/kept`: đã review, không có defect hợp lệ để sửa; hành vi được khóa bằng
  characterization hoặc integration gate.
- `upstream numerical kernel`: code tính toán nhạy tương thích, được giữ nguyên và
  kiểm tra bằng checkpoint/exact-inference gate.
- `parser compatibility`: parser cũ được giữ nguyên để không thay đổi token/tree.

## Audit 29 file production

| # | File | Trách nhiệm | Phát hiện và xử lý | Trạng thái | Bằng chứng |
|---:|---|---|---|---|---|
| 1 | `tamer/__init__.py` | Khai báo package TAMER | Không có logic hoặc hardcode runtime cần sửa. | tested/kept | Import trong toàn bộ 11 test HMER và real worker. |
| 2 | `tamer/datamodule/__init__.py` | Export `Batch`, datamodule và vocabulary | Public import path đang được backend/checkpoint sử dụng; giữ nguyên. | tested/kept | `test_data_contracts.py`; strict checkpoint load. |
| 3 | `tamer/datamodule/datamodule.py` | Dataset CROHME, `Batch`, padding/collate | Padding bị lặp với university datamodule và còn comment return đã bỏ. Tách `pad_images` dùng chung, giữ nguyên mask convention. | modified | `test_data_contracts.py`; 11 HMER tests; exact GPU inference. |
| 4 | `tamer/datamodule/dataset.py` | Đọc archive, gom sample theo kích thước | Không phát hiện defect độc lập; thay đổi dễ ảnh hưởng thứ tự batch/token. | tested/kept | Data characterization; strict checkpoint và exact inference. |
| 5 | `tamer/datamodule/latex2gtd.py` | Chuyển LaTeX token sang graph/tree | Parser tương thích cũ; rewrite có nguy cơ đổi tree/token order. Giữ nguyên toàn bộ. | parser compatibility | `test_domain_contracts.py`; exact LaTeX GPU baseline. |
| 6 | `tamer/datamodule/transforms.py` | Biến đổi ảnh/tensor cho dataset gốc | Không có defect tái hiện; giữ nguyên pixel/mask semantics. | tested/kept | Data characterization và exact GPU fixture. |
| 7 | `tamer/datamodule/university_datamodule.py` | Manifest dataset, replay sampler, dataloader đại học | Dùng lại `pad_images`, bỏ phần padding trùng lặp; sampler và seed không đổi. | modified | `test_data_contracts.py` khóa replay ratio, metadata, padding và seed. |
| 8 | `tamer/datamodule/vocab.py` | Ánh xạ token ↔ index | Xóa `__main__` debug hardcode đường dẫn dictionary; public singleton/API giữ nguyên. | modified | `test_domain_contracts.py`; strict checkpoint; exact inference. |
| 9 | `tamer/model/__init__.py` | Export package model | Không có logic cần sửa; giữ import path. | tested/kept | State manifest và import trong real worker. |
| 10 | `tamer/model/adapter.py` | Gated bottleneck adapter | Initialization/parameter names là một phần state dict; không có defect mới. | tested/kept | Strict `state_dict` manifest 727 key và exact inference. |
| 11 | `tamer/model/decoder.py` | Decoder, fusion và structural similarity | Operation order, layer names và tensor shape nhạy checkpoint; không rewrite. | tested/kept | Strict checkpoint load; 727-key manifest; exact GPU output. |
| 12 | `tamer/model/encoder.py` | Dense image encoder | Numerical path ổn định; không phát hiện defect có regression test. | tested/kept | Strict checkpoint load và exact GPU output. |
| 13 | `tamer/model/pos_enc.py` | Positional encoding ảnh/từ | Buffer/shape ảnh hưởng trực tiếp checkpoint; giữ nguyên. | tested/kept | State manifest và exact GPU output. |
| 14 | `tamer/model/tamer.py` | Kết nối encoder, adapter, decoder và beam search | Hai đường forward/search lặp encode. Tách `_encode` private, không thêm parameter/buffer. | modified | State manifest vẫn 727 key; `test_model_contracts.py`; exact inference. |
| 15 | `tamer/model/transformer/__init__.py` | Export transformer implementation | Giữ import compatibility. | upstream numerical kernel | Strict checkpoint và exact inference. |
| 16 | `tamer/model/transformer/arm.py` | Attention refinement/coverage | Kernel số học upstream; rewrite có thể đổi attention score. Giữ nguyên. | upstream numerical kernel | Strict checkpoint và exact inference. |
| 17 | `tamer/model/transformer/attention.py` | Multi-head attention primitives | Kernel số học upstream; giữ nguyên thứ tự/mask/softmax. | upstream numerical kernel | Strict checkpoint và exact inference. |
| 18 | `tamer/model/transformer/transformer_decoder.py` | Transformer decoder stack/layers | Kernel số học upstream; giữ nguyên layer order và residual path. | upstream numerical kernel | Strict checkpoint và exact inference. |
| 19 | `tamer/university/__init__.py` | Export utility domain đại học | Public exports hợp lệ, không có code chết. | tested/kept | `test_domain_contracts.py`. |
| 20 | `tamer/university/augmentation.py` | Augmentation giấy/nhiễu có seed | Thuật toán ảnh nhạy phân phối training; giữ nguyên. Characterization ghi nhận seed 1234 cho shape 60×112. | tested/kept | `test_data_contracts.py` deterministic augmentation. |
| 21 | `tamer/university/image_io.py` | Đọc grayscale và chuyển tensor | Không phát hiện defect; giữ pixel normalization hiện tại. | tested/kept | Data characterization. |
| 22 | `tamer/university/latex.py` | Chuẩn hóa/validate LaTeX | Hành vi domain hiện tại hợp lệ; không rewrite parser/validator. | tested/kept | `test_domain_contracts.py`. |
| 23 | `tamer/university/metrics.py` | Edit distance, ExpRate, report JSON/CSV | Loại lặp field mapping bằng `METRIC_FIELDS` và `_write_group_metrics`; payload giữ nguyên. | modified | `test_domain_contracts.py` khóa metric/report payload. |
| 24 | `tamer/utils/__init__.py` | Khai báo package utilities | Không có logic cần sửa. | tested/kept | Import trong model/training tests. |
| 25 | `tamer/utils/beam_search.py` | Beam scorer và finalize hypothesis | Search ordering/score nhạy exact output; không rewrite. | tested/kept | Exact real-GPU LaTeX baseline. |
| 26 | `tamer/utils/generation_utils.py` | Bidirectional generation, reverse/structure scoring | Tách `_structure_scores` private và bỏ TODO; giữ nguyên tuyệt đối operation order. | modified | 727-key manifest; focused model test; exact GPU output. |
| 27 | `tamer/utils/utils.py` | Loss, target conversion, hypothesis và ExpRate | Token direction/loss semantics ổn định; giữ nguyên. | tested/kept | Domain/training tests và exact inference. |
| 28 | `tamer/lit_tamer.py` | Lightning training/test loop và CROHME artifacts | Sửa mutable default, tách artifact writer, xóa các alternative/comment inactive; optimizer, scheduler, log keys và payload không đổi. | modified | `test_training_contracts.py`; 11 HMER tests; exact GPU inference. |
| 29 | `tamer/lit_university.py` | Fine-tune/replay validation và retention metrics | Sửa annotation tuple từ `List` sang `Sequence`, vẫn truyền `list(milestones)`; training behavior không đổi. | modified | Training/domain tests; strict checkpoint; exact GPU inference. |

## Acceptance evidence

### Local backend

- `30 passed, 1 skipped in 6.63s`.
- Skip duy nhất là real-GPU test khi Windows local không có các biến môi trường
  checkpoint/GPU.

### HMER project trên RTX 3090 Ti

- `python -m compileall`: không có lỗi.
- `11 passed, 4 warnings in 1.17s`.
- Checkpoint được load với `strict=True`.
- State manifest khớp đủ 727 key và shape.

### Uni-MuMER CUDA vision runtime

- Image base vẫn là `pytorch/pytorch:2.7.1-cuda12.6-cudnn9-runtime`.
- `pip check`: `No broken requirements found`.
- Torch: `2.7.1+cu126`.
- Torchvision: `0.22.1+cu126`.
- `torchvision.ops.nms` chạy trên CUDA và trả `[0]`.

### Docker real-GPU stack

- `verify_bundle.sh`: `BUNDLE_PREFLIGHT_OK`.
- Compose config hợp lệ.
- Gateway, TAMER và Uni-MuMER image đều build thành công.
- Sau recreate, cả ba service healthy/ready.
- TAMER device: `cuda`; Uni-MuMER device: `cuda:0`.
- Cả hai request đi qua gateway trả HTTP 200, `mock:false`,
  `valid_latex:true`, request ID khác rỗng và metadata `738 x 107 PNG`.
- Cả hai model khớp chính xác baseline:

```text
\cos y + \sqrt { t ^ { 2 } + 7 } + u \cdot m ^ { 2 } + \log ( v + 2 ) + n ^ { 5 } + \log k + \frac { m ^ { 4 } } { m + 5 }
```

- TAMER latency ở lần acceptance cuối: 347.79 ms.
- Uni-MuMER latency ở lần acceptance cuối: 1,250.25 ms.
- Log scan không có traceback, segmentation fault, fatal Python error hoặc CUDA
  out-of-memory.
- Marker: `REAL_GPU_REFACTOR_OK`.

Hai worker không publish cổng 8101/8102 ra host; health của chúng được kiểm tra
đúng qua mạng nội bộ Compose. Gateway tiếp tục phục vụ hợp đồng ngoài ở cổng
8000.

### Android

- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`:
  `BUILD SUCCESSFUL`.
- AVD: `HMER_Test_API`, thiết bị `emulator-5554`.
- Instrumentation:
  `HmerDemoSmokeTest.onboardingSkipOpensRecognitionWorkspace`.
- Kết quả: `OK (1 test)`.

## Quyết định cuối

Không có regression nào được phát hiện sau refactor. Checkpoint/model output,
backend API, Docker deployment và Android UI smoke flow đều giữ đúng hợp đồng đã
ghi nhận trước refactor. Nhánh sẵn sàng cho review/merge sau khi Git final gates
vẫn sạch.
