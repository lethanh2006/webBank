# 08. Observability: log, metrics, tracing

## Bài toán

Khách gọi tổng đài: "Tôi chuyển tiền lúc 10:05, bị trừ tiền nhưng người nhận chưa nhận được." Đội vận
hành phải lần ra giao dịch đó đã đi qua API, DB, Kafka, hệ thống bù trừ như thế nào, chậm hay lỗi ở
đâu. Ngoài ra còn phải phát hiện sự cố trước khi khách phát hiện, và báo cáo sự cố theo yêu cầu.

Ba trụ cột trả lời ba câu hỏi khác nhau:

- **Metrics:** hệ thống có đang khỏe không? (con số tổng hợp theo thời gian)
- **Traces:** một request cụ thể đã đi qua đâu, chậm ở đâu?
- **Logs:** chuyện gì đã xảy ra, chi tiết ra sao?

## Tracing

| Lựa chọn | Bản chất | Đánh giá |
| --- | --- | --- |
| **OpenTelemetry** | Chuẩn mở của CNCF: API, SDK, giao thức OTLP, Collector. Gửi được về bất kỳ backend nào (Tempo, Jaeger, APM thương mại) | Không bị khóa vào nhà cung cấp. Spring Boot 4 có `spring-boot-starter-opentelemetry` |
| Zipkin / Brave | Hệ thống tracing đời đầu | Vẫn dùng được qua Micrometer Tracing; xu hướng chuyển sang OpenTelemetry |
| Spring Cloud Sleuth | Tracing cho Spring Boot 2 | **Đã bị bỏ từ Spring Boot 3**, thay bằng Micrometer Tracing |
| APM thương mại (Dynatrace, AppDynamics, New Relic, Datadog, Elastic APM) | Agent tự gắn vào JVM, có giao diện phân tích mạnh | Phổ biến ở doanh nghiệp lớn và ngân hàng; tốn phí; khóa vào nhà cung cấp |

**Khái niệm phải nắm:**

- Trace gồm nhiều span. Ngữ cảnh truyền qua header `traceparent` (chuẩn W3C Trace Context) trong HTTP
  và trong **header của message Kafka**.
- Sampling: **head** (quyết định giữ trace ngay từ đầu, rẻ) hay **tail** (quyết định sau khi trace kết
  thúc, giữ được mọi trace lỗi, tốn tài nguyên hơn).

## Metrics

| Lựa chọn | Bản chất |
| --- | --- |
| **Prometheus** | Mô hình **pull**: Prometheus định kỳ đến lấy (scrape) `/actuator/prometheus`; lưu time series; truy vấn bằng PromQL; cảnh báo qua Alertmanager |
| Push (StatsD, InfluxDB, OTLP push) | Ứng dụng tự đẩy số liệu đi. Hợp với job ngắn hạn, môi trường không scrape được |
| Micrometer | **Lớp trừu tượng** (giống SLF4J cho log): code đo một lần, xuất ra bất kỳ backend nào |

**Đo cái gì:**

- **RED** cho từng service: Rate (số request), Errors (tỉ lệ lỗi), Duration (độ trễ p95, p99).
- **USE** cho tài nguyên: Utilization, Saturation, Errors. Ví dụ connection pool: đang dùng bao nhiêu,
  bao nhiêu request đang chờ kết nối.
- **Metrics nghiệp vụ:** số giao dịch theo trạng thái, số sự kiện tồn trong outbox, consumer lag.
- **Bẫy cardinality:** không gắn tag theo số tài khoản hay mã giao dịch. Mỗi giá trị tag tạo một time
  series riêng, hàng triệu tài khoản sẽ làm sập Prometheus.

## Log

| Lựa chọn | Bản chất | Đánh giá |
| --- | --- | --- |
| ELK / EFK (Elasticsearch) | Đánh chỉ mục toàn văn mọi trường | Tìm kiếm mạnh, tốn lưu trữ và RAM |
| **Grafana Loki** | Chỉ đánh chỉ mục các nhãn (service, level), nội dung nén thành từng khối | Rẻ hơn nhiều; truy vấn giống `grep` theo nhãn |
| Splunk | Thương mại, thường đồng thời là SIEM của ngân hàng | Mạnh, đắt |

**Quy tắc ghi log:** dạng JSON có cấu trúc (Spring Boot hỗ trợ sẵn qua
`logging.structured.format.console=ecs`), mỗi dòng có `traceId`; **không** ghi số thẻ, OTP, token, số
CCCD; che số tài khoản.

## Quyết định cho WebBank

OpenTelemetry để thu thập; image `grafana/otel-lgtm` (gói Grafana, Tempo, Loki và Prometheus trong
một container) để chạy local. Khi vào ngân hàng thật thì backend lưu trữ sẽ theo chuẩn của ngân hàng
(có thể là Dynatrace hoặc Splunk); code đo đạc dùng chuẩn mở nên không phải sửa.

## Câu hỏi phỏng vấn

**Một giao dịch chậm bất thường, bạn điều tra thế nào?**
Tìm trace theo mã giao dịch hoặc `traceId` trong log, xem span nào chiếm thời gian (chờ khóa DB, gọi
clearing, chờ connection pool), đối chiếu với metrics cùng thời điểm (pool đầy? GC dài?).

**SLI, SLO, error budget là gì?**
SLI là chỉ số đo (ví dụ tỉ lệ request chuyển tiền thành công dưới 1 giây). SLO là mục tiêu cho chỉ số
đó (99,9% mỗi tháng). Error budget là phần được phép hỏng (0,1%); dùng hết thì ưu tiên ổn định hơn
tính năng mới.

**Vì sao nhìn p99 chứ không nhìn trung bình?**
Trung bình che mất những request rất chậm. p99 = 2 giây nghĩa là cứ 100 người thì một người chờ ít
nhất 2 giây, và một màn hình gọi nhiều API sẽ gặp cái chậm đó thường xuyên hơn nhiều.

**Nên cảnh báo theo cái gì?**
Theo triệu chứng người dùng cảm nhận (tỉ lệ lỗi, độ trễ vượt SLO), không theo nguyên nhân (CPU 80%).
CPU cao mà người dùng không bị ảnh hưởng thì không cần đánh thức ai lúc 3 giờ sáng.
