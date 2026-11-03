# Runbook: nhà cung cấp trí tuệ nhân tạo gặp sự cố

Lớp kết nối (NCL-04-CN-001, `ai/gateway`) là đường duy nhất tới mô hình. Mỗi khả năng (sinh văn bản, biểu diễn ngữ
nghĩa, đọc hình ảnh, nhận dạng chữ) có nhà cung cấp chính, dự phòng, thời gian chờ, số lần thử lại và ngắt mạch.

## Hành vi tự động

- Mỗi lần gọi chờ tối đa `timeoutSeconds` (mặc định 30 giây), thử lại `maxRetries` lần (mặc định 1), rồi trả
  `AI_TEMPORARILY_UNAVAILABLE` (HTTP 503) cho chức năng gọi.
- `breakerFailureThreshold` lỗi liên tiếp trong `breakerWindowSeconds` (mặc định 5 lỗi trong 60 giây) thì mạch mở:
  lời gọi đi thẳng sang dự phòng trong `breakerOpenSeconds`, sau đó một lời gọi thử quyết định đóng hay mở tiếp.
- Khi mạch mở: cảnh báo hiện ở **Khu quản trị → Dịch vụ AI**, quản trị viên nhận thư `AI_CIRCUIT_OPEN`, nhật ký thao
  tác có dòng `AI_CIRCUIT_OPENED` (người thực hiện: Hệ thống).
- Trạng thái mạch nằm trong bộ nhớ của từng máy chủ; khởi động lại máy chủ thì mạch đóng.

## Khi nhận cảnh báo

1. Mở **Dịch vụ AI**, xem thẻ định tuyến của khả năng bị ảnh hưởng và bảng "Lời gọi gần nhất" (mã lỗi: `TIMEOUT`,
   `AUTH`, `RATE_LIMITED`, `HTTP_5xx`, `SECRET_MISSING`…).
2. `AUTH` hoặc `SECRET_MISSING`: kiểm tra khóa trong kho bí mật (xem dưới).
3. Sự cố kéo dài: bấm **Chuyển sang dự phòng** để đổi vai chính và dự phòng hẳn (mạch của khả năng được đóng lại).
   Thao tác được ghi `AI_FAILOVER_SWITCHED`.
4. Bấm **Đã xem** để xác nhận cảnh báo. Mạch đó sẽ không cảnh báo lại cho tới khi đã xác nhận.

## Thêm hoặc đổi nhà cung cấp

1. Đặt khóa vào kho bí mật của máy chủ dưới một tên chữ in hoa, ví dụ biến môi trường `LC_AI_OPENAI_KEY` (hoặc thuộc
   tính `app.ai.secrets.LC_AI_OPENAI_KEY`). Giá trị khóa không bao giờ nhập qua giao diện hay lưu trong cơ sở dữ liệu.
2. **Thêm nhà cung cấp**: kiểu kết nối (giao diện kiểu OpenAI hoặc Anthropic Messages), địa chỉ `https://`, tên mô hình
   cho từng khả năng, tên khóa, giá mỗi triệu đơn vị.
3. Chỉ đánh dấu cam kết không huấn luyện sau khi đã kiểm tra hợp đồng hoặc điều khoản (QTN-11). Thiếu xác nhận, thiếu
   khóa hay thiếu mô hình thì hệ thống từ chối bật và ghi `AI_PROVIDER_ENABLE_REJECTED`.
4. Bật nhà cung cấp, rồi **Sửa** định tuyến của khả năng để chọn làm chính hoặc dự phòng. Dùng **Gọi thử** để kiểm.

## Môi trường thử

Hai bản mô phỏng `mock-primary` và `mock-backup` trả kết quả cố định. Công cụ dev (chỉ khi `app.dev-tools.enabled`):

- `POST /api/v1/dev/ai/mock/{mã}?mode=FAIL|HANG|OK` cho bản mô phỏng lỗi ngay, treo hoặc chạy bình thường.
- `POST /api/v1/dev/ai/reset` đưa định tuyến, mạch và cảnh báo về ban đầu.
