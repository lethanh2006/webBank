# 06. Chịu lỗi khi gọi hệ thống ngoài, batch và tác vụ định kỳ

## Phần 1. Chịu lỗi (resilience)

### Bài toán

WebBank gọi hệ thống bù trừ liên ngân hàng (mô phỏng). Hệ thống đó có thể chậm, lỗi, hoặc sập hẳn.
Nếu không phòng vệ, mỗi request chờ 30 giây sẽ giữ một thread và một kết nối DB; vài trăm request như
vậy là ứng dụng cạn tài nguyên và sập theo (lỗi dây chuyền).

### Các pattern

| Pattern | Bản chất |
| --- | --- |
| **Timeout** | Không bao giờ chờ vô hạn. Nhiều HTTP client mặc định read timeout là vô hạn |
| **Retry** | Thử lại lỗi tạm thời, với **exponential backoff + jitter**: chờ `base × 2^n` cộng một khoảng ngẫu nhiên, để các client không đồng loạt thử lại cùng lúc (thundering herd) |
| **Circuit breaker** | Đếm tỉ lệ lỗi hoặc chậm. Vượt ngưỡng thì **mở**: báo lỗi ngay, không gọi nữa. Sau một lúc chuyển **nửa mở**: cho vài request thử; thành công thì **đóng** lại |
| **Bulkhead** | Giới hạn số lời gọi đồng thời đến một hệ thống, để một đối tác chậm không chiếm hết tài nguyên |
| **Fallback** | Phương án thay thế khi lỗi: xếp hàng xử lý sau, trả thông báo thân thiện |

**Nguyên tắc sống còn:** chỉ retry **thao tác idempotent**. Retry một lệnh chuyển tiền không có khóa
chống trùng là có thể chuyển hai lần.

### Các lựa chọn

| Lựa chọn | Bản chất | Đánh giá |
| --- | --- | --- |
| Netflix Hystrix | Thư viện tiên phong về circuit breaker | Ngừng phát triển từ 2018, không dùng cho dự án mới |
| **Resilience4j** | Thư viện nhẹ, chia module (chỉ lấy cái cần), có annotation và metrics qua Micrometer | Lựa chọn tiêu chuẩn hiện nay; có `resilience4j-spring-boot4` |
| Spring Retry / Spring Framework 7 | Spring Framework 7 đã có sẵn `@Retryable`, `@ConcurrencyLimit` | Đủ cho retry đơn giản, không có circuit breaker đầy đủ |
| Alibaba Sentinel | Kiểm soát lưu lượng, circuit breaker, có dashboard | Phổ biến trong hệ sinh thái Spring Cloud Alibaba, ít gặp ở ngân hàng Việt Nam |
| Service mesh (Istio, Linkerd) | Sidecar proxy làm timeout, retry, circuit breaker cho mọi service | Không phụ thuộc ngôn ngữ, nhưng hạ tầng phức tạp và **không biết thao tác nào idempotent**: cấu hình retry ở mesh có thể retry cả lệnh POST chuyển tiền |

**Quyết định: Resilience4j.** Logic chịu lỗi nằm trong ứng dụng, nơi biết rõ thao tác nào được phép
retry (tra soát) và thao tác nào không (gửi lệnh mới không có mã tham chiếu).

## Phần 2. Batch và tác vụ định kỳ

### Bài toán

Cuối ngày cần tính lãi cho hàng triệu tài khoản, đối soát với file quyết toán, lập sao kê. Nếu job
chết giữa chừng thì phải **chạy tiếp từ chỗ dừng mà không hạch toán trùng**. Ngoài ra có các việc
định kỳ nhỏ: dọn key idempotency hết hạn, tra soát giao dịch UNKNOWN.

### Các lựa chọn

| Lựa chọn | Bản chất | Hợp với |
| --- | --- | --- |
| `@Scheduled` | Chạy phương thức theo lịch; không lưu trạng thái; **chạy trên mọi instance** | Việc nhỏ, idempotent (kèm ShedLock khi có nhiều instance) |
| Quartz | Lưu job trong DB, cluster (một node chạy mỗi job), xử lý lần chạy bị lỡ (misfire), lịch nghỉ lễ | Lập lịch phức tạp ngay trong ứng dụng |
| **Spring Batch** | Framework **xử lý lô** (không phải bộ lập lịch): Job gồm các Step; xử lý theo chunk (đọc–xử lý–ghi trong một transaction mỗi N bản ghi); JobRepository lưu tiến độ để chạy lại từ chunk cuối đã commit; chính sách skip/retry; partitioning để chạy song song | Xử lý khối lượng lớn cần chạy lại được |
| JobRunr | Hàng đợi background job có dashboard, lưu bền | Việc nền dạng "fire-and-forget" |
| Airflow, Control-M | Điều phối các job xuyên hệ thống theo đồ thị phụ thuộc. **Control-M rất phổ biến trong ngân hàng cho EOD** | Điều phối, không chứa logic nghiệp vụ |
| Kubernetes CronJob | Chạy một container theo lịch | Job tách biệt khỏi ứng dụng |

**Quyết định:**

- **Spring Batch cho EOD.** Một JobInstance được xác định bởi tên job và tham số định danh
  (`businessDate`). Đã chạy xong ngày 11/11 thì chạy lại sẽ bị từ chối; chết giữa chừng thì chạy lại
  tiếp từ chunk cuối cùng đã commit. Mỗi bút toán lãi có khóa duy nhất `(tài khoản, ngày)` làm chốt chặn
  cuối.
- **`@Scheduled` + ShedLock** cho các việc định kỳ nhỏ.
- Trong ngân hàng thật, Control-M thường đóng vai trò kích hoạt các job này theo đúng trình tự EOD.

### Không để job chạy trùng khi có nhiều instance

| Cách | Bản chất |
| --- | --- |
| ShedLock | Bảng khóa trong DB hoặc Redis; tại một thời điểm chỉ một instance chạy được job; khóa tự hết hạn sau `lockAtMostFor` |
| Quartz clustering | Các node tranh khóa qua bảng của Quartz |
| Leader election | Chỉ leader chạy (Kubernetes Lease, Spring Integration) |
| `FOR UPDATE SKIP LOCKED` | Không bầu leader mà chia việc: mỗi instance lấy một lô bản ghi chưa ai khóa. Dùng cho outbox relay, cho phép xử lý song song |

## Câu hỏi phỏng vấn

**Circuit breaker có những trạng thái nào, cấu hình dựa trên gì?**
Đóng, mở, nửa mở. Cấu hình theo tỉ lệ lỗi, tỉ lệ cuộc gọi chậm, cửa sổ đếm (theo số lần hoặc theo thời
gian), thời gian chờ ở trạng thái mở, số request thử ở trạng thái nửa mở.

**Vì sao cần jitter khi retry?**
Không có jitter thì mọi client lỗi cùng lúc sẽ retry cùng lúc, tạo ra các đợt sóng tải đập vào hệ
thống vừa hồi phục.

**Xử lý 10 triệu bản ghi trong batch thế nào?**
Chunk (ví dụ 1.000 bản ghi mỗi transaction), đọc bằng keyset hoặc cursor thay vì `OFFSET`,
partitioning theo dải id để chạy song song, ghi theo lô, chạy lại được nhờ JobRepository và khóa duy
nhất.

**Job chạy trên 3 instance thì làm sao chỉ một instance chạy?**
ShedLock, Quartz clustering hoặc leader election. Hoặc thiết kế để nhiều instance cùng chạy an toàn
bằng `SKIP LOCKED`.
