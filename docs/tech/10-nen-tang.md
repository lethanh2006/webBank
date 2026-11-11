# 10. Nền tảng: Java, Spring Boot, mô hình web, kiểu API, công cụ build

## 1. Phiên bản Java

Ngân hàng chuẩn hóa trên các bản **LTS** có hỗ trợ dài hạn (8, 11, 17, 21, 25), từ các nhà cung cấp
như Oracle, Red Hat, Azul, Eclipse Temurin.

| Bản | Điểm đáng chú ý |
| --- | --- |
| 17 | Vẫn rất phổ biến; record, sealed class, text block |
| **21** | Virtual threads (JEP 444), pattern matching cho `switch`, record pattern, sequenced collections, ZGC phân thế hệ |
| 25 (LTS, 9/2025) | Scoped values, compact source files; từ JDK 24 virtual thread **không còn bị "ghim" khi gặp `synchronized`** (JEP 491) |

**Quyết định: Java 21.** Là LTS được mọi thư viện hỗ trợ, có virtual threads. Lên 25 là việc dễ khi
cần, và JEP 491 là một lý do chính đáng để lên.

## 2. Spring Boot 4.1 hay 3.5?

- **Spring Boot 4** đi kèm Spring Framework 7: Jakarta EE 11, null-safety với JSpecify, hỗ trợ API
  versioning sẵn có, `@Retryable` và `@ConcurrencyLimit` trong core. Auto-configuration được tách thành
  nhiều module nhỏ (`spring-boot-starter-webmvc` thay cho `spring-boot-starter-web`). Đi kèm Jackson 3
  (package đổi thành `tools.jackson`), Hibernate 7, JUnit 6.
- **Spring Boot 3.5** là dòng 3.x cuối cùng. Spring Initializr đã không còn cho tạo dự án 3.5 mới.
- **Quyết định: 4.1**, bản ổn định mới nhất. Vì ngân hàng thường đang chạy 2.7 hoặc 3.x, cần nắm các
  mốc nâng cấp: Boot 3 chuyển `javax.*` sang `jakarta.*` và yêu cầu Java 17; Boot 4 đổi sang Jackson 3
  và tách starter.

## 3. Spring MVC + virtual threads hay WebFlux (reactive)?

| | Spring MVC (thread-per-request) | WebFlux (reactive) | Spring MVC + virtual threads |
| --- | --- | --- | --- |
| Bản chất | Mỗi request một thread nền tảng (platform thread); gọi DB thì thread bị chặn chờ | Vài thread event loop (Netty); mọi I/O đều non-blocking, code viết bằng chuỗi toán tử (`Mono`, `Flux`) | Mỗi request một virtual thread; khi bị chặn I/O, virtual thread nhả thread hệ điều hành cho việc khác |
| Khả năng mở rộng khi I/O nhiều | Giới hạn bởi số thread (mỗi thread khoảng 1 MB stack) | Rất cao | Rất cao |
| Độ khó | Thấp | Cao: debug khó, stack trace khó đọc, toàn bộ stack phải non-blocking | Thấp |
| Truy cập DB | JDBC, JPA | **R2DBC, không dùng được JPA** | JDBC, JPA |

**Lưu ý về virtual threads:**

- Trên JDK 21–23, virtual thread bị "ghim" (pinning) vào thread hệ điều hành khi chạy trong khối
  `synchronized`, mất lợi ích.
- **Nút thắt thường là connection pool của DB**, không phải số thread. Virtual threads không tạo thêm
  kết nối DB; một triệu virtual thread vẫn phải xếp hàng chờ 10 kết nối. Vẫn cần giới hạn đồng thời
  (bulkhead).

**Quyết định: Spring MVC + JPA.** Ở giai đoạn 5 sẽ đo so sánh virtual threads với platform threads.
WebFlux hợp khi xây gateway hoặc dịch vụ streaming với hàng chục nghìn kết nối đồng thời.

## 4. Kiểu API: REST, gRPC, GraphQL hay SOAP?

| | Bản chất | Hợp với |
| --- | --- | --- |
| **REST/JSON** | Tài nguyên + phương thức HTTP | API cho web, mobile, đối tác; ai cũng gọi được, dễ debug |
| gRPC | HTTP/2 + Protobuf nhị phân, có streaming, sinh code từ hợp đồng | Gọi nội bộ giữa các service cần hiệu năng; trình duyệt cần thêm grpc-web |
| GraphQL | Client tự chọn trường cần lấy | BFF cho mobile với nhiều màn hình; phải xử lý N+1 ở resolver, giới hạn độ phức tạp truy vấn, phân quyền theo từng trường |
| SOAP/XML | Hợp đồng WSDL, WS-Security | Vẫn còn nhiều trong tích hợp ngân hàng đời cũ. ISO 20022 là chuẩn thông điệp tài chính dạng XML mà SWIFT và nhiều hệ thống thanh toán đang chuyển sang |

**Quyết định: REST + OpenAPI**, với các quy ước:

- Version trên đường dẫn `/api/v1`.
- Lỗi theo **RFC 9457 Problem Details**, có thêm trường `code` để client xử lý theo mã.
- Header `Idempotency-Key` cho mọi thao tác ghi tiền.
- Phân trang bằng cursor (keyset).
- Tạo mới trả `201 Created`.

## 5. Maven hay Gradle?

| | Maven | Gradle |
| --- | --- | --- |
| Bản chất | XML khai báo, vòng đời cố định (compile, test, package) | DSL Groovy/Kotlin, đồ thị task |
| Mạnh | Dễ đoán, ổn định, **chuẩn phổ biến ở ngân hàng** | Build tăng dần và build cache nên nhanh với dự án lớn; linh hoạt |
| Yếu | Dài dòng, kém linh hoạt | Script build có thể phức tạp, khó đọc |

**Quyết định: Maven.** Phổ biến nhất trong môi trường ngân hàng, người review đọc được ngay. Maven
Wrapper (`./mvnw`) đảm bảo mọi người dùng cùng phiên bản Maven.

## 6. Đóng gói và chạy

- **Image container:** Buildpacks (`spring-boot:build-image`, không cần Dockerfile), Jib, hoặc
  Dockerfile nhiều tầng.
- **GraalVM native image:** khởi động nhanh, ít RAM, nhưng build lâu và phải cấu hình reflection. Hợp
  với serverless; ít cần với dịch vụ chạy lâu dài.
- **Điều phối:** Docker Compose cho môi trường dev; Kubernetes hoặc **OpenShift** (bản Kubernetes doanh
  nghiệp của Red Hat, được nhiều ngân hàng dùng) cho production. Endpoint
  `/actuator/health/liveness` và `/readiness` dùng làm probe.

## Câu hỏi phỏng vấn

**Virtual threads khác reactive thế nào? Khi nào chọn cái nào?**
Cả hai giải quyết cùng một bài toán: phục vụ nhiều request đang chờ I/O mà không cần nhiều thread hệ
điều hành. Virtual threads giữ code tuần tự quen thuộc và tương thích JDBC/JPA. Reactive cho khả năng
điều khiển backpressure và streaming tinh vi hơn, nhưng đổi bằng độ phức tạp.

**Pinning là gì?**
Virtual thread không nhả được thread hệ điều hành vì đang ở trong `synchronized` (JDK 21–23) hoặc
trong native frame, nên mất lợi ích của virtual thread.

**Auto-configuration của Spring Boot hoạt động thế nào?**
Spring Boot đọc danh sách các class auto-configuration trong file
`META-INF/spring/…AutoConfiguration.imports`. Mỗi class có các điều kiện (`@ConditionalOnClass`,
`@ConditionalOnMissingBean`, `@ConditionalOnProperty`) quyết định có tạo bean hay không. Khi người dùng
tự khai báo bean thì bean mặc định lùi lại.

**Vì sao `@Transactional` không chạy khi gọi phương thức trong cùng class?**
Spring bọc bean bằng proxy. Lời gọi nội bộ đi thẳng vào object gốc nên không qua proxy, không có
transaction.
