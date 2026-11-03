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

Người dùng đã chốt: làm các story của Epic 3 và Epic 4 mà Epic 2 cần trước, theo STT trên Jira, rồi mới quay lại Epic 2. Mỗi story làm trong **một session riêng**.

**"Làm story tiếp theo"** nghĩa là: lấy dòng đầu tiên trong bảng có trạng thái "Chưa làm", và kiểm tra trên Jira rằng các story nó phụ thuộc đã xong.

**Story phụ thuộc chưa được merge**: nếu story trước đã có PR nhưng chưa merge vào `develop`, merge branch của story đó vào branch hiện tại trước khi code. Ghi trong mô tả PR là PR này dựa trên PR kia. Khi người dùng merge PR trước, diff của PR sau sẽ tự gọn lại.

Mọi branch đã có sẵn trên origin, chỉ cần checkout, không tạo branch mới.

| # | Story | Branch | Trạng thái |
|---|-------|--------|------------|
| 1 | LC-19 Nhật ký thao tác (Epic 3, STT 5) | `feature/LC-19-audit-log-recording-and-search` | PR mở, chờ merge |
| 2 | LC-20 Tài khoản admin duy nhất + 2FA (Epic 3, STT 6) | `feature/LC-20-single-admin-account-with-two-factor-auth` | Đang làm |
| 3 | LC-25 AI provider gateway (Epic 4, STT 11) | `feature/LC-25-swappable-ai-provider-gateway` | Chưa làm |
| 4 | LC-26 Cấu hình model/prompt có phiên bản (Epic 4, STT 12) | `feature/LC-26-versioned-model-and-prompt-config` | Chưa làm |
| 5 | LC-27 Quota AI và dung lượng (Epic 4, STT 13) | `feature/LC-27-fair-quotas-for-ai-and-storage` | Chưa làm |
| 6 | LC-28 Lọc nội dung không an toàn, chống prompt injection (Epic 4, STT 14) | `feature/LC-28-unsafe-content-filter-and-prompt-injection-guard` | Chưa làm |
| 7 | Các story còn lại của Epic 3 và 4, xếp theo STT trên Jira khi phần phụ thuộc đã có: LC-37, 90, 92, 93, 94, 95, 96, 97, 99, 100, 101 | `feature/LC-<key>-…` (xem `git branch -r`) | Chưa làm |
| 8 | Quay lại Epic 2: LC-38, 44, 78, 86, 87, 88, 113, 116 | `feature/LC-<key>-…` | Chờ |

Đã xong: Epic LC-1 (NCL-01). Epic LC-2: LC-18 (PR #10), LC-24 (PR #11).

## Quy ước code cần nhớ

- LC-19 dựng sẵn khu vực admin dùng chung: `AdminRoute` (gọi `GET /admin/me`, nếu 403 thì hiện trang forbidden, server ghi log `ROLE_ACCESS_DENIED`), danh sách `sections` trong `AdminLayout`, và mục "Quản trị" trong account menu cho role ADMIN. Các story admin sau cứ gắn vào đây.
- `frontend/src/features/admin/lib/auditCatalog.ts` phải luôn khớp với enum `AuditAction` ở backend, mỗi action có Category và Outcome tương ứng.
- Viết code theo phong cách của code xung quanh. i18n đặt trong `frontend/src/shared/i18n/locales/{vi,en}`.

## Bẫy khi chạy test

- Playwright dùng `reuseExistingServer`: nếu backend đã chạy từ trước khi sửa code backend, nó vẫn phục vụ code cũ và endpoint mới sẽ trả 404. Sau khi sửa backend, nhớ restart backend trước khi chạy e2e.
- Không sửa `frontend/src` trong lúc e2e đang chạy, vì Vite hot-reload sẽ làm test fail giả.
- Nếu một test bị timeout, chạy lại riêng test đó trước khi kết luận là lỗi thật.

## Việc cố ý để lại sau

6 lint warning có từ trước (`watch()` của react-hook-form, export của Button); config nginx cần `proxy_buffering off` cho `/api/v1/notifications/stream` và header cache cho service worker; SSE hub hiện chỉ chạy được trên một instance.
