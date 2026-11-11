# 01. Kiến trúc: monolith, modular monolith hay microservices?

## Bài toán

WebBank có nhiều mảng nghiệp vụ: khách hàng, tài khoản, sổ cái, chuyển tiền, phê duyệt, xử lý cuối
ngày, thông báo. Câu hỏi là nên đóng gói và triển khai chúng thế nào. Đây gần như luôn là câu hỏi mở
màn của vòng phỏng vấn kỹ thuật: "Kể về kiến trúc dự án của bạn, tại sao lại chọn như vậy?"

## Bản chất các lựa chọn

### Layered monolith (monolith phân lớp)

Một ứng dụng duy nhất, chia theo **lớp kỹ thuật**: `controller/`, `service/`, `repository/` dùng
chung cho mọi nghiệp vụ.

- **Mạnh:** đơn giản nhất; một transaction bao được mọi thứ; debug dễ.
- **Yếu:** không có ranh giới giữa các mảng nghiệp vụ. Service chuyển tiền có thể gọi thẳng
  repository của khách hàng, và sau vài năm code trở thành "big ball of mud": sửa một chỗ vỡ ba chỗ,
  nhiều team không làm song song được.

### Modular monolith

Vẫn một ứng dụng, nhưng chia theo **mảng nghiệp vụ** (bounded context). Mỗi module:

- công bố một API (các class public ở package gốc của module) và **giấu** phần bên trong;
- chỉ phụ thuộc vào module khác qua API đó, và phụ thuộc không được tạo vòng;
- lý tưởng là sở hữu bảng dữ liệu riêng, không có foreign key chéo module.

Các module gọi nhau bằng lời gọi hàm (đồng bộ, có thể chung transaction) hoặc bằng sự kiện trong tiến
trình.

- **Mạnh:** giữ được transaction ACID ở chỗ cần; vận hành như một ứng dụng; ranh giới được kiểm tra
  tự động; tách một module thành service về sau chỉ còn là việc cơ học.
- **Yếu:** vẫn triển khai và scale cả khối; một module rò bộ nhớ làm sập cả ứng dụng; cần kỷ luật và
  công cụ để ranh giới không bị xói mòn.

### Microservices

Mỗi service là một ứng dụng riêng, **sở hữu dữ liệu riêng**, triển khai độc lập, giao tiếp qua mạng
(REST, gRPC, message broker).

- **Mạnh:** triển khai và scale độc lập; mỗi team tự chủ; lỗi của một service ít lan sang service
  khác; mỗi service có thể dùng công nghệ khác nhau.
- **Cái giá phải trả** (người phỏng vấn muốn nghe phần này):
  - Lời gọi qua mạng có thể chậm, lỗi, timeout ("8 ngộ nhận về hệ phân tán").
  - **Không còn transaction ACID xuyên service**, phải dùng saga và chấp nhận nhất quán cuối cùng
    (eventual consistency).
  - Cần tracing phân tán, service discovery, config tập trung, CI/CD cho từng service.
  - Test tích hợp khó hơn, dữ liệu bị nhân bản giữa các service.
  - Nếu chia sai ranh giới thì thành **distributed monolith**: các service phải triển khai cùng
    nhau, gọi nhau đồng bộ dày đặc, hoặc dùng chung database. Đây là kết quả tệ nhất: có mọi cái khó
    của hệ phân tán mà không có lợi ích nào.

### SOA / ESB: thực tế ở nhiều ngân hàng

Nhiều ngân hàng dùng một **core banking** thương mại (ví dụ Temenos T24, Oracle Flexcube) làm hệ
thống gốc (system of record). Các kênh (internet banking, mobile, ATM, POS) kết nối vào core qua tầng
tích hợp: ESB (IBM ACE/IIB, WSO2, MuleSoft) và hàng đợi (IBM MQ). Các ngân hàng số hóa thêm một **tầng
digital** gồm microservices, API gateway và Kafka, nối vào core qua adapter.

Biết bức tranh này giúp bạn hiểu: một lập trình viên mới thường làm ở tầng digital quanh core, không
viết lại core. WebBank mô phỏng một "core mini" để hiểu bản chất các bài toán mà core giải quyết.

## So sánh

| Tiêu chí | Layered monolith | Modular monolith | Microservices |
| --- | --- | --- | --- |
| Transaction ACID giữa các nghiệp vụ | Có | Có | Không, phải dùng saga |
| Ranh giới nghiệp vụ | Không có | Có, kiểm tra tự động | Có, bằng mạng |
| Độ phức tạp vận hành | Thấp | Thấp | Cao |
| Scale độc lập từng phần | Không | Không | Có |
| Nhiều team làm song song | Khó | Được (ở mức vừa phải) | Tốt |
| Phù hợp một người hoặc team nhỏ | Có | Có | Không |
| Rủi ro chính | Big ball of mud | Ranh giới bị xói mòn | Distributed monolith |

## Quyết định cho WebBank: modular monolith

1. **Tiền phải đúng.** Chuyển tiền nội bộ cập nhật hai tài khoản và ghi sổ cái. Trong một
   transaction ACID, việc này đúng ngay từ đầu. Nếu tách tài khoản và sổ cái thành hai service, ta phải
   dùng saga, có trạng thái trung gian, phải bù trừ khi lỗi: thêm rất nhiều cách để sai.
2. **Chỉ phân tán ở nơi nghiệp vụ vốn phân tán:** chuyển tiền liên ngân hàng (đi qua hệ thống của
   người khác, nên bắt buộc dùng saga) và thông báo (bất đồng bộ qua Kafka). Như vậy dự án vẫn thể hiện
   đủ các kỹ thuật phân tán, nhưng đặt đúng chỗ.
3. **Ranh giới được kiểm tra bằng test:** `ApplicationModules.verify()` của Spring Modulith làm fail
   build nếu module A dùng class nội bộ của module B hoặc có phụ thuộc vòng.
4. **Đường lui để tách service:** mỗi module sở hữu bảng riêng, không có foreign key chéo module,
   giao tiếp qua API hoặc sự kiện. Muốn tách module `notification` thành service riêng thì chỉ cần
   thay lời gọi trong tiến trình bằng Kafka.

## Kiểm soát ranh giới module: Spring Modulith, ArchUnit hay Maven multi-module?

| Cách | Bản chất | Mạnh | Yếu |
| --- | --- | --- | --- |
| Spring Modulith | Mỗi package con trực tiếp của package gốc là một module; package gốc của module là API; package con là nội bộ; test `verify()` kiểm tra | Ít nghi thức, có test tích hợp theo module (`@ApplicationModuleTest`), sinh sơ đồ C4 | Kiểm tra lúc chạy test, không phải lúc compile |
| ArchUnit | Viết luật kiến trúc bằng Java ("controller không được gọi repository") | Linh hoạt, áp dụng cho mọi dự án | Phải tự viết và bảo trì luật |
| Maven multi-module | Mỗi module là một artifact riêng; compiler chặn phụ thuộc không khai báo | Ranh giới cứng nhất, ở thời điểm compile | Nhiều file `pom.xml`, refactor chậm |
| JPMS (`module-info.java`) | Module của Java | Đóng gói mạnh nhất | Framework hỗ trợ kém, ít dùng trong ứng dụng Spring |

WebBank chọn Spring Modulith vì ít nghi thức nhất mà vẫn có kiểm tra tự động. Khi dự án lớn lên,
chuyển sang Maven multi-module là bước tiếp theo tự nhiên.

## Khi nào nên chọn microservices?

- Có nhiều team (từ 3–4 team trở lên). Theo **định luật Conway**, kiến trúc phản ánh cơ cấu tổ chức.
- Các phần có tải rất khác nhau: cấp phép giao dịch thẻ cần hàng nghìn TPS, còn back-office chỉ vài
  chục người dùng.
- Cần phát hành độc lập với nhịp độ khác nhau.
- **Lý do riêng của ngân hàng:** tách phần xử lý dữ liệu thẻ thành vùng riêng để thu hẹp phạm vi
  kiểm toán PCI DSS. Chỉ service nào chạm vào số thẻ mới thuộc phạm vi kiểm toán.

## Câu hỏi phỏng vấn

**Tại sao không dùng microservices?**
Vì chuyển tiền nội bộ cần ACID. Tách ra thì phải dùng saga cho việc mà một transaction làm đúng ngay.
Dự án chỉ phân tán ở luồng liên ngân hàng và thông báo, nơi nghiệp vụ vốn đã phân tán. Ranh giới module
được kiểm tra tự động nên tách service sau này rất rẻ.

**Distributed monolith là gì? Dấu hiệu nhận biết?**
Các service phải triển khai cùng nhau; một request đi qua chuỗi dài các lời gọi đồng bộ; nhiều service
dùng chung một database; đổi một service thì service khác vỡ.

**Database per service, vậy báo cáo cần join dữ liệu nhiều service thì sao?**
Không join xuyên database lúc chạy. Dùng read model (CQRS) được cập nhật bằng sự kiện, đổ dữ liệu về
data warehouse qua CDC/Kafka, hoặc API composition cho truy vấn nhỏ.

**Khi nào bạn sẽ tách một module của WebBank thành service riêng?**
Khi module đó cần scale khác biệt (ví dụ notification lúc cao điểm), cần phát hành độc lập, hoặc giao
cho một team khác. Thứ tự tách hợp lý: notification trước (vốn đã giao tiếp bất đồng bộ), sổ cái sau
cùng.
