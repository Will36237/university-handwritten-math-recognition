package vn.edu.fpt.hmerdemo.ui

/** Stable error codes shared by Android UI and the future FastAPI backend. */
enum class HmerErrorCode {
    NO_IMAGE,
    CAMERA_PERMISSION_DENIED,
    CAMERA_UNAVAILABLE,
    GALLERY_PERMISSION_DENIED,
    UNSUPPORTED_IMAGE_TYPE,
    IMAGE_TOO_LARGE,
    IMAGE_TOO_SMALL,
    IMAGE_DECODE_FAILED,
    IMAGE_EMPTY,
    IMAGE_TOO_DARK,
    IMAGE_TOO_BRIGHT,
    IMAGE_TOO_BLURRY,
    CROP_INVALID,
    NO_FORMULA_CONTENT,
    NON_MATH_IMAGE,
    MULTIPLE_FORMULAS,
    NETWORK_OFFLINE,
    NETWORK_TIMEOUT,
    API_UNREACHABLE,
    REQUEST_REJECTED,
    MODEL_LOADING,
    MODEL_UNAVAILABLE,
    GPU_BUSY,
    GPU_OUT_OF_MEMORY,
    EMPTY_MODEL_OUTPUT,
    INVALID_LATEX_OUTPUT,
    INTERNAL_SERVER_ERROR,
    UNKNOWN,
}

data class UiError(
    val code: HmerErrorCode,
    val title: String,
    val message: String,
    val suggestion: String,
    val canRetry: Boolean = true,
)

fun HmerErrorCode.toUiError(): UiError = when (this) {
    HmerErrorCode.NO_IMAGE -> UiError(this, "Chưa có ảnh", "Không tìm thấy ảnh đầu vào.", "Hãy chụp ảnh, chọn từ thư viện hoặc dùng ảnh mẫu.", false)
    HmerErrorCode.CAMERA_PERMISSION_DENIED -> UiError(this, "Chưa có quyền camera", "Ứng dụng không thể mở camera.", "Cho phép quyền camera trong Cài đặt hoặc chọn ảnh từ thư viện.", false)
    HmerErrorCode.CAMERA_UNAVAILABLE -> UiError(this, "Camera không khả dụng", "Thiết bị hoặc trình giả lập không cung cấp camera.", "Chọn ảnh từ thư viện hoặc dùng ảnh mẫu.", false)
    HmerErrorCode.GALLERY_PERMISSION_DENIED -> UiError(this, "Không thể mở thư viện", "Ứng dụng chưa được phép truy cập ảnh.", "Cấp quyền truy cập ảnh trong Cài đặt rồi thử lại.", false)
    HmerErrorCode.UNSUPPORTED_IMAGE_TYPE -> UiError(this, "Định dạng chưa được hỗ trợ", "File đã chọn không phải PNG, JPEG hoặc WEBP.", "Chuyển ảnh sang định dạng được hỗ trợ rồi chọn lại.", false)
    HmerErrorCode.IMAGE_TOO_LARGE -> UiError(this, "Ảnh quá lớn", "Dung lượng ảnh vượt giới hạn 10 MB.", "Giảm kích thước hoặc nén ảnh rồi thử lại.", false)
    HmerErrorCode.IMAGE_TOO_SMALL -> UiError(this, "Ảnh quá nhỏ", "Độ phân giải không đủ để nhận dạng rõ công thức.", "Chọn ảnh ít nhất 64 × 64 px hoặc chụp lại gần hơn.", false)
    HmerErrorCode.IMAGE_DECODE_FAILED -> UiError(this, "Không thể đọc ảnh", "File ảnh có thể bị hỏng hoặc không hoàn chỉnh.", "Chọn một ảnh khác hoặc chụp lại.", false)
    HmerErrorCode.IMAGE_EMPTY -> UiError(this, "Ảnh gần như trống", "Không phát hiện nét viết đủ rõ trong ảnh.", "Chụp lại và bảo đảm công thức nằm trong khung hình.", false)
    HmerErrorCode.IMAGE_TOO_DARK -> UiError(this, "Ảnh quá tối", "Nét viết không đủ rõ để nhận dạng.", "Chụp lại ở nơi đủ sáng và tránh bóng đổ.", false)
    HmerErrorCode.IMAGE_TOO_BRIGHT -> UiError(this, "Ảnh bị cháy sáng", "Một phần nét viết có thể đã bị mất.", "Giảm ánh sáng hoặc tắt đèn flash rồi chụp lại.", false)
    HmerErrorCode.IMAGE_TOO_BLURRY -> UiError(this, "Ảnh bị mờ", "Đường nét công thức không đủ sắc nét.", "Giữ máy ổn định, lấy nét và chụp lại gần hơn.", false)
    HmerErrorCode.CROP_INVALID -> UiError(this, "Vùng cắt chưa hợp lệ", "Vùng đã chọn quá nhỏ hoặc nằm ngoài ảnh.", "Cắt lại và bao trọn một biểu thức.", false)
    HmerErrorCode.NO_FORMULA_CONTENT -> UiError(this, "Không tìm thấy công thức", "Vùng ảnh chưa thể hiện rõ một biểu thức toán.", "Cắt sát công thức hoặc chọn ảnh khác.", false)
    HmerErrorCode.NON_MATH_IMAGE -> UiError(this, "Không tìm thấy công thức toán", "Ảnh không chứa công thức toán đủ rõ để nhận dạng.", "Hãy chụp hoặc cắt sát một công thức toán rồi thử lại.", false)
    HmerErrorCode.MULTIPLE_FORMULAS -> UiError(this, "Có nhiều công thức", "Mỗi lượt nhận dạng nên chứa một biểu thức.", "Cắt riêng công thức cần nhận dạng.", false)
    HmerErrorCode.NETWORK_OFFLINE -> UiError(this, "Không có kết nối mạng", "Ứng dụng chưa thể kết nối tới máy chủ nhận dạng.", "Kiểm tra Wi-Fi hoặc mạng di động rồi thử lại.")
    HmerErrorCode.NETWORK_TIMEOUT -> UiError(this, "Yêu cầu quá thời gian", "Máy chủ phản hồi chậm hơn dự kiến.", "Giữ nguyên ảnh và thử lại sau ít phút.")
    HmerErrorCode.API_UNREACHABLE -> UiError(this, "Không thể kết nối máy chủ", "Dịch vụ nhận dạng hiện không truy cập được.", "Kiểm tra địa chỉ API hoặc thử lại sau.")
    HmerErrorCode.REQUEST_REJECTED -> UiError(this, "Yêu cầu bị từ chối", "Máy chủ không chấp nhận dữ liệu được gửi lên.", "Chọn lại ảnh và thử lại.")
    HmerErrorCode.MODEL_LOADING -> UiError(this, "Mô hình đang khởi động", "Máy chủ đang tải trọng số vào GPU.", "Đợi một lát rồi thử lại.")
    HmerErrorCode.MODEL_UNAVAILABLE -> UiError(this, "Mô hình chưa sẵn sàng", "Mô hình được chọn hiện không thể phục vụ.", "Thử mô hình còn lại hoặc thử lại sau.")
    HmerErrorCode.GPU_BUSY -> UiError(this, "GPU đang bận", "Máy chủ đang xử lý một yêu cầu khác.", "Giữ nguyên ảnh và thử lại sau vài giây.")
    HmerErrorCode.GPU_OUT_OF_MEMORY -> UiError(this, "Không đủ bộ nhớ GPU", "Máy chủ không thể hoàn tất lượt nhận dạng này.", "Thử lại từng mô hình riêng hoặc giảm kích thước ảnh.")
    HmerErrorCode.EMPTY_MODEL_OUTPUT -> UiError(this, "Không có kết quả", "Mô hình không sinh được chuỗi LaTeX.", "Cắt lại công thức rõ hơn hoặc thử mô hình còn lại.")
    HmerErrorCode.INVALID_LATEX_OUTPUT -> UiError(this, "LaTeX chưa hợp lệ", "Kết quả chưa thể render thành công thức.", "Bạn vẫn có thể sao chép output thô hoặc thử mô hình còn lại.")
    HmerErrorCode.INTERNAL_SERVER_ERROR -> UiError(this, "Lỗi xử lý trên máy chủ", "Dịch vụ gặp lỗi ngoài dự kiến.", "Giữ nguyên ảnh và thử lại; nếu lỗi lặp lại, kiểm tra server log.")
    HmerErrorCode.UNKNOWN -> UiError(this, "Đã xảy ra lỗi", "Ứng dụng chưa thể hoàn tất thao tác.", "Giữ nguyên ảnh và thử lại; nếu lỗi lặp lại, khởi động lại ứng dụng.")
}
