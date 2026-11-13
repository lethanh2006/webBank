# Đối chiếu CV và hướng dẫn demo WebBank

Kiểm tra ngày 13/11/2026. Đây là dự án mô phỏng nghiệp vụ ngân hàng; danh tính được chọn bằng
khách hàng demo. CV không nên mô tả đây là hệ thống ngân hàng production hoặc có đăng nhập JWT.

## Những gì có thể chứng minh bằng mã và kiểm thử

| Nội dung CV | Mã nguồn | Bằng chứng chạy |
| --- | --- | --- |
| Java 21, Spring Boot 4, Spring MVC, JPA/Hibernate, JUnit 6, Flyway, PostgreSQL 17, Testcontainers | `backend/pom.xml`, `compose.yaml`, `TestcontainersConfiguration` | `./mvnw verify`: PostgreSQL 17 được khởi động bằng Testcontainers, migration và Hibernate validation thành công |
| REST API theo module customer, ledger, transfer, idempotency | Các package `com.webbank.*` | `ModularityTests`, `AccountApiIT`, `TransferApiIT` |
| RFC 9457, mã lỗi và request ID | `GlobalExceptionHandler`, `RequestIdFilter` | `SystemControllerTest`, `TransferApiIT` |
| Sổ cái kép, bút toán cân bằng, postings bất biến | `V2__ledger.sql`, `DefaultLedgerService` | `LedgerServiceIT`: chặn sửa/xóa, từ chối lệch bút toán tại commit, rollback và đảo bút toán |
| Khóa tài khoản theo ID tăng dần | `DefaultLedgerService.lockAccounts` | `TransferConcurrencyIT`: 100 lần chuyển 10.000 từ 500.000, đúng 50 thành công; 200 lần chuyển ngược chiều không deadlock |
| Idempotency-Key và response lưu trong cùng transaction | `DefaultIdempotencyService` | `TransferApiIT`: replay tuần tự, 20 request trùng đồng thời, đổi payload cùng key bị từ chối, giao dịch lỗi không lưu kết quả |
| Sao kê keyset pagination | `TransactionHistoryQuery`, `DefaultAccountService` | `AccountApiIT`: 5 dòng trên 3 trang, đúng thứ tự, không trùng/thiếu |
| 44 kiểm thử backend | `backend/src/test/java` | 19 test Surefire + 25 test Failsafe, không lỗi hoặc bỏ qua |
| Docker API, GitHub Actions, Render, Neon, React/Vercel | `backend/Dockerfile`, `.github/workflows/ci.yml`, `render.yaml`, `frontend/vercel.json` | Backend live trả danh sách demo; UI live gọi API qua rewrite. `render.yaml` nhận thông tin PostgreSQL ngoài qua biến môi trường; tên nhà cung cấp Neon cần đối chiếu dashboard triển khai |

Số 19 là nhóm Surefire, gồm unit, MVC slice, kiểm tra module và một context smoke test;
số 25 là integration test Failsafe với PostgreSQL thật. Nếu cần diễn đạt chặt chẽ hơn trên CV,
dùng **“44 automated backend tests (19 unit/slice/architecture/smoke checks and 25 PostgreSQL
integration tests via Testcontainers)”**. Chín browser test bổ sung không tính vào 44 test backend.

## Kịch bản demo khoảng 3 phút

1. Mở trang. Khi máy chủ nghỉ, trang thông báo đang tải và tự thử lại; khi lỗi có nút **Thử lại**.
2. Khách hàng đầu tiên được chọn tự động. Kiểm tra số dư tài khoản và mở lịch sử giao dịch.
3. Đổi sang khách hàng thứ hai, ghi lại số tài khoản; trở lại người gửi.
4. Chọn **Chuyển tiền**, nhập tài khoản nhận, 1.000 VND và nội dung. Kiểm tra tên người nhận rồi xác nhận.
5. Chọn **Xem tài khoản nguồn**: số dư giảm đúng 1.000 và sao kê có mã giao dịch vừa tạo.
6. Đổi sang người nhận: số dư tăng đúng 1.000. Mỗi khách hàng có bản nháp riêng; không thể đổi người dùng lúc đang gửi giao dịch.
7. Để trình bày khả năng đồng thời và idempotency, chạy test dưới đây; không gửi hàng trăm giao dịch vào demo công khai.

## Lệnh kiểm chứng

Từ thư mục `backend`:

```bash
./mvnw verify
./mvnw -Dit.test=TransferConcurrencyIT,TransferApiIT verify
```

Từ thư mục `frontend`, khi API local đang chạy:

```bash
npm ci
npx playwright install chromium
npm run lint
npm run test:e2e
```

Browser test tự build và phục vụ bản build trên cổng 4174; dùng API local cổng 8081.
Test `@backend` gửi một giao dịch 1.000 VND, replay cùng key và đối chiếu số dư cả hai bên;
tám test còn lại chủ động giả lập trạng thái API để chứng minh khả năng phục hồi của UI.
Bản nháp giữ cùng khóa chống trùng khi tải lại trang hoặc chuyển màn hình. Nếu giao dịch chưa rõ
kết quả thì không cho sửa yêu cầu; bản nháp quá 24 giờ yêu cầu đối chiếu sao kê thay vì gửi lại.

Kết quả local đã quan sát: backend **44/44**, browser **9/9**, lint và production build thành công.
Trạng thái CI và bản triển khai cần kiểm tra ở từng commit/deployment; các kết quả local không
thay thế kết quả chạy trên GitHub Actions hoặc quyền truy cập dashboard Neon/Render.
