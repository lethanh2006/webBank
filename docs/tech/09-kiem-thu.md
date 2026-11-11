# 09. Kiểm thử và đo hiệu năng

## Bài toán

Chứng minh được: tiền không âm khi có 100 request đồng thời, retry không trừ tiền hai lần, sổ cái luôn
cân bằng, mỗi endpoint chặn được truy cập trái phép, và hệ thống chịu được bao nhiêu TPS. "Em đã test
rồi" không có giá trị bằng một test chạy được trong CI.

## Các tầng kiểm thử

| Tầng | WebBank dùng cho |
| --- | --- |
| Unit test | Logic thuần: `Money`, kiểm tra bút toán cân bằng, chữ số kiểm tra số tài khoản |
| Integration test (Testcontainers) | Repository, khóa, trigger, idempotency, API đầy đủ trên PostgreSQL thật |
| Test kiến trúc | `ApplicationModules.verify()`: ranh giới module |
| Test đồng thời | Nhiều luồng cùng lúc, kiểm tra bất biến |
| Load test | Thông lượng, độ trễ, điểm nghẽn |

## CSDL dùng khi test: Testcontainers, H2 hay mock?

| Lựa chọn | Bản chất | Đánh giá |
| --- | --- | --- |
| H2 (kể cả chế độ tương thích PostgreSQL) | CSDL Java trong bộ nhớ | Nhanh, nhưng khác dialect, khác cơ chế khóa và MVCC, không chạy được trigger PL/pgSQL, không có JSONB. Test xanh trên H2 không chứng minh được gì về xử lý đồng thời |
| **Testcontainers** | Bật PostgreSQL thật trong Docker cho mỗi lần chạy test | Đúng như production; khởi động chậm hơn vài giây, giảm bằng cách dùng chung container và cache Spring context |
| CSDL dùng chung cho cả team | Mọi người test trên cùng một DB | Dữ liệu dẫm lên nhau, test chập chờn |
| Mock repository | Thay repository bằng đối tượng giả | Nhanh, hợp với unit test, nhưng không kiểm tra được SQL, khóa, constraint |

**Quyết định: Testcontainers.** Với dự án mà trọng tâm là khóa và constraint, test trên CSDL thật là
bắt buộc. Spring Boot hỗ trợ sẵn qua `@ServiceConnection`.

## Giả lập hệ thống ngoài

| Lựa chọn | Đánh giá |
| --- | --- |
| **WireMock** | Giả lập HTTP, cấu hình được độ trễ, lỗi, ngắt kết nối giữa chừng. Hợp để test timeout và circuit breaker |
| MockServer | Tương tự WireMock |
| Contract test (Spring Cloud Contract, Pact) | Consumer và provider thống nhất hợp đồng API, kiểm tra tự động cả hai phía. Hữu ích khi nhiều team cùng phát triển |
| Chạy hệ thống giả lập thật (`clearing-simulator`) | Dùng cho test end-to-end và demo chaos |

## Test xử lý đồng thời

- Dùng `ExecutorService` cùng một `CountDownLatch` để các luồng **xuất phát cùng một lúc**, tăng khả
  năng va chạm.
- Kiểm tra **bất biến** thay vì kiểm tra từng bước: tổng số dư không đổi, không có số dư âm, số giao
  dịch thành công đúng bằng số tiền chia cho số tiền mỗi lệnh.
- Chạy với PostgreSQL thật, connection pool thật.

## Load test

| Công cụ | Bản chất | Đánh giá |
| --- | --- | --- |
| JMeter | Kịch bản dạng GUI, lưu thành XML | **Rất phổ biến ở đội QA và performance của ngân hàng Việt Nam**; file XML khó review, tốn tài nguyên khi chạy tải lớn |
| Gatling | Kịch bản bằng code (Java, Scala, Kotlin), engine bất đồng bộ, báo cáo HTML đẹp | Mạnh, hiệu quả |
| **k6** | Kịch bản JavaScript, engine viết bằng Go, chạy bằng dòng lệnh; ngưỡng pass/fail viết ngay trong kịch bản | Kịch bản nằm trong repo; chạy được trong CI; tích hợp với Grafana |
| Locust | Kịch bản Python | Hợp với team Python |

**Quyết định: k6**, vì kịch bản là code nằm trong repo và có ngưỡng như `p(95) < 300ms` để CI tự đánh
fail. Nên biết JMeter vì đội QA của ngân hàng thường dùng nó.

## Câu hỏi phỏng vấn

**Vì sao không test bằng H2?**
Vì H2 khác PostgreSQL ở đúng những chỗ quan trọng nhất của dự án: khóa, MVCC, trigger, kiểu dữ liệu.

**Test race condition thế nào?**
Cho nhiều luồng xuất phát cùng lúc bằng latch, lặp lại nhiều lần, kiểm tra bất biến. Không dùng
`Thread.sleep` để "đợi cho chắc".

**Test chập chờn (flaky) thường do đâu, khắc phục thế nào?**
Phụ thuộc thời gian thực, thứ tự chạy test, dữ liệu dùng chung, chờ bất đồng bộ bằng `sleep`. Khắc
phục: inject `Clock`, mỗi test tự tạo dữ liệu riêng, dùng Awaitility để chờ có điều kiện.

**Test code phụ thuộc thời gian (ngày nghiệp vụ, hết hạn OTP) thế nào?**
Không gọi `LocalDate.now()` trực tiếp; inject một `Clock` để test điều khiển được thời gian.
