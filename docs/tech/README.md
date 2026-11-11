# Lựa chọn công nghệ: vì sao chọn cái này mà không chọn cái kia

Không có công nghệ "tốt nhất", chỉ có công nghệ phù hợp với ràng buộc. Người phỏng vấn ít khi hỏi
"Kafka là gì" mà hỏi "**tại sao** dùng Kafka chứ không dùng RabbitMQ". Trả lời được câu đó nghĩa là
hiểu bản chất của cả hai.

Mỗi tài liệu trong thư mục này có cùng cấu trúc:

1. **Bài toán** trong WebBank cần giải.
2. **Bản chất** của từng lựa chọn: nó hoạt động bên trong như thế nào.
3. **So sánh** theo các tiêu chí quan trọng.
4. **Quyết định** cho WebBank và lý do.
5. **Khi nào nên chọn phương án khác.** Phần này quan trọng không kém phần quyết định.
6. **Câu hỏi phỏng vấn** kèm ý trả lời.

Ràng buộc của WebBank, theo thứ tự ưu tiên: tiền phải đúng, rồi mới đến hiệu năng; một người làm;
chạy được trên một máy bằng Docker Compose; công nghệ phổ biến ở ngân hàng Việt Nam; mọi lựa chọn phải
giải thích được.

## Danh sách tài liệu

| # | Chủ đề | Các lựa chọn được so sánh |
| --- | --- | --- |
| 01 | [Kiến trúc](01-kien-truc.md) | Layered monolith, modular monolith, microservices, SOA/ESB |
| 02 | [Message broker](02-message-broker.md) | Kafka, RabbitMQ, ActiveMQ Artemis, IBM MQ, Pulsar, Redis Streams, NATS |
| 03 | [Cơ sở dữ liệu](03-co-so-du-lieu.md) | PostgreSQL, Oracle, MySQL, MongoDB, NewSQL; Flyway, Liquibase; JPA, MyBatis, jOOQ, JdbcClient |
| 04 | [Nhất quán dữ liệu](04-nhat-quan-du-lieu.md) | Các kiểu khóa, isolation level, idempotency, outbox/CDC/2PC, saga, workflow engine |
| 05 | [Bảo mật](05-bao-mat.md) | Keycloak, Spring Authorization Server, SaaS, tự viết; JWT, opaque token, session; PKCE, BFF; OTP; rate limit |
| 06 | [Chịu lỗi và tác vụ nền](06-chiu-loi-tac-vu-nen.md) | Resilience4j, Spring Retry, Sentinel, service mesh; Spring Batch, Quartz, @Scheduled; ShedLock |
| 07 | [Cache và Redis](07-cache-redis.md) | Redis, Memcached, Hazelcast, Caffeine; các pattern cache; distributed lock |
| 08 | [Observability](08-observability.md) | OpenTelemetry, Zipkin, APM thương mại; Prometheus; Loki, ELK |
| 09 | [Kiểm thử](09-kiem-thu.md) | Testcontainers, H2, mock; WireMock, contract test; k6, JMeter, Gatling |
| 10 | [Nền tảng](10-nen-tang.md) | Java 17/21/25; Spring Boot 3.5/4; MVC + virtual threads, WebFlux; REST, gRPC, GraphQL; Maven, Gradle |
| 11 | [Frontend](11-frontend.md) | React, Angular, Vue; Ant Design, MUI, shadcn; TanStack Query, Redux; Vite, Next.js |

## Bảng tóm tắt quyết định

| Hạng mục | Chọn | Phương án thay thế chính | Lý do trong một câu |
| --- | --- | --- | --- |
| Kiến trúc | Modular monolith (Spring Modulith) | Microservices | Chuyển tiền nội bộ cần một transaction ACID; chỉ phân tán nơi nghiệp vụ vốn phân tán |
| Message broker | Kafka | RabbitMQ, IBM MQ | Dữ liệu là sự kiện nghiệp vụ cần nhiều bên đọc, giữ thứ tự theo tài khoản và đọc lại được |
| CSDL | PostgreSQL | Oracle | ACID, khóa dòng, `SKIP LOCKED`, constraint mạnh, miễn phí; kiến thức chuyển sang Oracle được |
| Migration | Flyway | Liquibase | SQL thuần, dễ review, đủ cho một loại CSDL |
| Truy cập dữ liệu | JPA + JdbcClient | MyBatis, jOOQ | JPA cho aggregate có bất biến; SQL thuần cho truy vấn đọc và SQL đặc thù |
| Chống tranh chấp | Khóa bi quan có thứ tự | Optimistic, SERIALIZABLE | Tranh chấp cao trên số dư; không retry hàng loạt; không deadlock |
| Sự kiện tin cậy | Transactional outbox | 2PC/XA, CDC | Kafka không hỗ trợ XA; outbox đơn giản, có lộ trình lên CDC |
| Giao dịch phân tán | Saga điều phối | Choreography, 2PC | Luồng liên ngân hàng cần timeout, tra soát, nhìn thấy trạng thái rõ ràng |
| Danh tính | Keycloak (OIDC) | Tự viết JWT, Spring Authorization Server | Không tự viết đăng nhập; chuẩn OIDC; chạy on-premise |
| Chịu lỗi | Resilience4j | Spring Retry, service mesh | Circuit breaker + bulkhead + timeout ở tầng ứng dụng, biết thao tác nào idempotent |
| Batch | Spring Batch | Quartz, @Scheduled | Chạy lại từ điểm lỗi mà không hạch toán trùng |
| Cache | Redis | Memcached, Hazelcast | Cần thao tác nguyên tử cho rate limit và bộ đếm, không chỉ cache |
| Observability | OpenTelemetry + Grafana | APM thương mại | Chuẩn mở, không khóa vào nhà cung cấp |
| Kiểm thử CSDL | Testcontainers | H2 | H2 không mô phỏng đúng khóa, trigger, MVCC của PostgreSQL |
| Load test | k6 | JMeter, Gatling | Kịch bản là code nằm trong repo, ngưỡng pass/fail chạy được trong CI |
| Mô hình web | Spring MVC (+ virtual threads) | WebFlux | Giữ JPA và code tuần tự; virtual threads cho khả năng mở rộng I/O |
| Frontend | React + Ant Design + TanStack Query | Angular | Hệ sinh thái lớn; frontend không phải trọng tâm |
