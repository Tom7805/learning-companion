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
6. **Commit, push, PR chỉ làm khi người dùng yêu cầu.** PR luôn nhắm vào `develop` (không phải `main`). Message commit theo dạng `feat(<module>): <mô tả> (LC-<key>, NCL-0x-CN-0yy)`.
7. **Khi đã mở PR**: chuyển story sang **Done** (transition `31`) và thêm comment tiếng Việt tóm tắt thay đổi, kèm link PR. Người dùng sẽ tự review và merge. Các transition khác: `2` Reviewing, `11` To Do.
8. Làm xong một story thì báo kết quả rồi chờ người dùng bảo "commit/push/PR".

## Thứ tự làm việc

- Epic LC-1 (NCL-01): **Done**.
- Epic LC-2 (NCL-02): LC-18 (PR #10) và LC-24 (PR #11) đã merge. Còn LC-38, 44, 78, 86, 87, 88, 113, 116, đang **chờ** vì phụ thuộc story ở Epic 3 và 4.
- Người dùng đã chốt: làm các story phụ thuộc trước, theo thứ tự STT trên Jira:
  **LC-19** (STT 5, code đã commit + push trên branch, chờ mở PR) → **LC-20** (6) → **LC-25, 26, 27, 28** (11–14) → các story còn lại của Epic 3 (LC-94, 95, 96, 97, 99, 100, 101) và Epic 4 (LC-37, 90, 92, 93) khi phần phụ thuộc đã có → rồi mới quay lại Epic 2.
- Cập nhật mục này mỗi khi một story được merge.

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
