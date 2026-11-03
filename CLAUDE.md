# Learning Companion: hướng dẫn cho Claude

Monorepo: `backend/` (Spring Boot 4.1, Java 21, Maven wrapper `./mvnw`), `frontend/` (React + Vite, Node ≥ 22, npm). Docs nằm trong `docs/`.

Luôn trả lời người dùng bằng **tiếng Việt**.

## Quy trình cho mỗi story Jira

Jira: project `LC`, cloudId `44f5ab70-66fd-4932-bbd5-6141d0d938a8`. Sprint đang chạy: "LC Sprint 2" (id 198, board 80, field sprint `customfield_10020`).

1. **Branch**: `feature/LC-<key>-<english-kebab-summary>`, tạo từ `develop`. Branch cho các story Epic 3 và Epic 4 đã có sẵn trên origin, cứ checkout là dùng. Trước khi code, merge `develop` mới nhất vào branch.
2. **Bắt đầu**: chuyển story sang **In Progress** (transition id `21`) rồi đọc kỹ description trên Jira, bao gồm acceptance criteria TC-01, TC-02…
3. **Làm trọn vẹn**: cả backend lẫn frontend, bám sát acceptance criteria, UI theo phong cách mockup "first look".
4. **Test mọi tương tác**:
   - Backend: unit test + integration test với Testcontainers (`*IT.java`)
   - Frontend: Vitest (`npm run test:run`), `npm run typecheck`, `npm run lint`
   - Playwright e2e (`npm run test:e2e`) trên desktop và mobile, có kiểm tra a11y bằng axe
   - Xem lại giao diện bằng screenshot, dùng script để trong thư mục scratchpad
5. **Không tạo rác**: script dùng một lần để ngoài repo, không commit file tạm.
6. **Khi xong và test đã pass**: commit, push branch, rồi mở PR vào `develop` (KHÔNG phải `main`). Không cần hỏi lại người dùng. Commit message theo dạng `feat(<module>): <mô tả tiếng Anh> (LC-<key>, NCL-0x-CN-0yy)`, phần thân liệt kê thay đổi Backend/Frontend. Mô tả PR theo `.github/PULL_REQUEST_TEMPLATE.md`, ghi rõ test nào đã chạy và test nào không chạy được (ví dụ IT/e2e khi môi trường không có Docker).
7. **Sau khi mở PR**: chuyển story sang **Done** (transition `31`) và thêm comment tiếng Việt tóm tắt thay đổi, kèm link PR. Các transition khác: `2` Reviewing, `11` To Do.
8. **Người dùng tự review và merge PR.** Claude không bao giờ merge PR, không push thẳng vào `develop` hay `main` (trừ khi sửa file này), và không force-push.
9. Cuối cùng cập nhật bảng trong mục "Thứ tự làm việc" ở dưới (cột Trạng thái: "PR #x mở") ngay trong PR của story, rồi báo người dùng: link PR, kết quả test, và những gì cần họ kiểm tra.

## Thứ tự làm việc

Người dùng đã chốt: làm **theo đúng STT trên Jira** (STT ghi trong description của mỗi story, dòng `**STT**: n`, kèm dòng `**Phụ thuộc vào**`). STT đã được xếp sao cho phần phụ thuộc luôn làm trước, và các story của Epic 2 nằm rải trong thứ tự này, nên đi hết thứ tự là tự quay lại Epic 2. Mỗi story làm trong **một session riêng**.

**"Làm story tiếp theo"** nghĩa là: lấy story có STT nhỏ nhất mà chưa Done trên Jira (bảng dưới chỉ để tham khảo nhanh; Jira là nguồn chính), kiểm tra các story trong "Phụ thuộc vào" đã xong hoặc đã có PR.

**Story phụ thuộc chưa được merge**: nếu story trước đã có PR nhưng chưa merge vào `develop`, merge branch của story đó vào branch hiện tại trước khi code. Ghi trong mô tả PR là PR này dựa trên PR kia. Khi người dùng merge PR trước, diff của PR sau sẽ tự gọn lại.

**Branch**: các story của Epic 2, 3, 4 đã có branch sẵn trên origin. Story của epic khác (LC-5 trở đi) thì tự tạo branch từ `develop` theo quy tắc đặt tên ở trên rồi push.

| STT | Story | Epic | Branch | Trạng thái |
|-----|-------|------|--------|------------|
| 5 | LC-19 Nhật ký thao tác | LC-3 | `feature/LC-19-audit-log-recording-and-search` | Đã merge (PR #12) |
| 6 | LC-20 Tài khoản admin duy nhất + 2FA | LC-3 | `feature/LC-20-single-admin-account-with-two-factor-auth` | Đã merge (PR #13) |
| 11 | LC-25 Lớp kết nối dịch vụ AI thay được nhà cung cấp | LC-4 | `feature/LC-25-swappable-ai-provider-gateway` | Đã merge (PR #14) |
| 12 | LC-26 Cấu hình model/prompt theo phiên bản | LC-4 | `feature/LC-26-versioned-model-and-prompt-config` | Chưa làm |
| 13 | LC-27 Hạn mức AI và dung lượng | LC-4 | `feature/LC-27-fair-quotas-for-ai-and-storage` | Chưa làm |
| 14 | LC-28 Lọc nội dung không an toàn, chống prompt injection | LC-4 | `feature/LC-28-unsafe-content-filter-and-prompt-injection-guard` | Chưa làm |
| 15 | LC-29 Kho tài liệu: thư mục nhiều cấp, nhãn, gắn sao | LC-5 | tạo mới | Chưa làm |
| 16 | LC-30 Tải tài liệu lên kho | LC-5 | tạo mới | Chưa làm |
| 17 | LC-31 Trích văn bản, OCR, chia đoạn, lập chỉ mục | LC-5 | tạo mới | Chưa làm |
| 18 | LC-32 Thêm tài liệu từ trang web và văn bản dán | LC-5 | tạo mới | Chưa làm |
| 19 | LC-33 Xem tài liệu ở khung xem bên, mở đúng đoạn | LC-5 | tạo mới | Chưa làm |
| 20 | LC-34 Không gian học gắn tài liệu từ kho | LC-6 | tạo mới | Chưa làm |
| 21 | LC-35 Hỏi đáp có căn cứ trên tài liệu | LC-7 | tạo mới | Chưa làm |
| 22 | LC-36 Trích dẫn bấm mở đúng đoạn | LC-7 | tạo mới | Chưa làm |
| 23 | LC-37 Gắn nhãn nội dung do AI sinh | LC-4 | `feature/LC-37-label-ai-generated-content` | Chưa làm |
| 24 | LC-38 Hiển thị công thức toán, mã nguồn, bảng (**Epic 2**) | LC-2 | `feature/LC-38-render-math-code-and-tables-in-content` | Chưa làm |
| 25… | Tiếp tục theo STT trên Jira (LC-44 Epic 2 ở STT 30, LC-78 ở 64, LC-86/87/88 ở 72–74, LC-113 ở 99, LC-116 ở 102) | | | |

Đã xong: Epic LC-1 (NCL-01). Epic LC-2: LC-18 (PR #10), LC-24 (PR #11).

## Quy ước code cần nhớ

- LC-19 dựng sẵn khu vực admin dùng chung: `AdminRoute` (gọi `GET /admin/me`, nếu 403 thì hiện trang forbidden, server ghi log `ROLE_ACCESS_DENIED`), danh sách `sections` trong `AdminLayout`, và mục "Quản trị" trong account menu cho role ADMIN. Các story admin sau cứ gắn vào đây.
- `frontend/src/features/admin/lib/auditCatalog.ts` phải luôn khớp với enum `AuditAction` ở backend, mỗi action có Category và Outcome tương ứng.
- LC-25 dựng lớp kết nối AI dùng chung (`ai/gateway/AiGateway`): mọi chức năng AI sau chỉ gọi `streamText`, `embed`, `describeImage`, `recognizeText` với một mã chức năng chữ in hoa (ví dụ `ASSISTANT_CHAT`); không gọi thẳng nhà cung cấp. Môi trường thử dùng bản mô phỏng `mock-primary`/`mock-backup`; e2e đặt lại bằng `dev.resetAiGateway()`.
- LC-26 thêm cấu hình theo phiên bản (`ai/config`): chức năng AI mới thêm một giá trị vào enum `AiFeature` (kèm lời nhắc, nhiệt độ, độ dài gốc; phiên bản 1 tự được đăng ký khi khởi động), rồi dựng yêu cầu bằng `AiConfigResolver.chatRequest(AiFeature.X, ...)` trước khi gọi `AiGateway.streamText(AiFeature.X.name(), ...)`. Không viết cứng lời nhắc hệ thống trong code chức năng. Lời gọi được ghi số phiên bản cấu hình ở cột `ai_call_records.config_version`. E2e đặt lại bằng `dev.resetAiConfigs()`.
- Viết code theo phong cách của code xung quanh. i18n đặt trong `frontend/src/shared/i18n/locales/{vi,en}`.

## Bẫy khi chạy test

- Playwright dùng `reuseExistingServer`: nếu backend đã chạy từ trước khi sửa code backend, nó vẫn phục vụ code cũ và endpoint mới sẽ trả 404. Sau khi sửa backend, nhớ restart backend trước khi chạy e2e.
- Không sửa `frontend/src` trong lúc e2e đang chạy, vì Vite hot-reload sẽ làm test fail giả.
- Nếu một test bị timeout, chạy lại riêng test đó trước khi kết luận là lỗi thật.

## Việc cố ý để lại sau

6 lint warning có từ trước (`watch()` của react-hook-form, export của Button); config nginx cần `proxy_buffering off` cho `/api/v1/notifications/stream` và header cache cho service worker; SSE hub hiện chỉ chạy được trên một instance.
