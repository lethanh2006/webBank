# 02. Message broker: vì sao Kafka mà không phải RabbitMQ, ActiveMQ, IBM MQ, Pulsar?

## Bài toán trong WebBank

Mỗi khi tiền dịch chuyển, hệ thống phát ra sự kiện như `TransferCompleted`. Có nhiều bên cần đọc sự
kiện này: gửi thông báo biến động số dư, ghi audit, phân tích chống gian lận, lập sao kê. Yêu cầu:

1. **Không mất sự kiện**, kể cả khi một máy chủ broker chết.
2. **Giữ thứ tự theo tài khoản**: biến động "trừ 500k" phải đến trước "cộng 200k" nếu nó xảy ra trước.
3. **Nhiều bên đọc độc lập** cùng một sự kiện. Thêm bên đọc mới không phải sửa bên phát.
4. **Đọc lại được (replay)**: consumer có bug thì sửa xong đọc lại; hệ thống chống gian lận mới cần
   dữ liệu 7 ngày gần nhất.
5. **Chịu được tải đột biến**: ngày trả lương, dịp Tết.
6. Phổ biến, dễ tuyển người, có hệ sinh thái (CDC, stream processing).

## Hai mô hình nền tảng: hàng đợi (queue) và nhật ký sự kiện (log)

Đây là phần bản chất quan trọng nhất. Mọi so sánh phía sau đều xuất phát từ đây.

### Message queue: RabbitMQ, ActiveMQ, IBM MQ

- Broker giữ message trong hàng đợi và **theo dõi trạng thái từng message**: đã giao chưa, đã ack
  chưa, có cần giao lại không ("broker thông minh, consumer đơn giản").
- Consumer ack thì broker **xóa message**. Không đọc lại được.
- Nhiều consumer trên cùng một hàng đợi là **competing consumers**: mỗi message chỉ đến một consumer.
  Đây là cách chia việc.
- Muốn nhiều bên cùng nhận (pub/sub) thì broker **sao chép** message vào nhiều hàng đợi, mỗi bên một
  hàng đợi. Bên nào tạo hàng đợi sau thì không nhận được message cũ.

### Event log: Kafka, Pulsar, Redis Streams

- Broker lưu một **nhật ký chỉ ghi thêm (append-only)**, chia thành nhiều partition. Message được giữ
  theo thời gian hoặc dung lượng cấu hình (retention), **bất kể đã có ai đọc hay chưa**.
- Mỗi consumer group **tự nhớ vị trí đã đọc (offset)** ("broker đơn giản, consumer thông minh").
- Nhiều consumer group đọc cùng một log, mỗi group một offset riêng: pub/sub mà không cần sao chép.
- Đọc lại = lùi offset.
- Thứ tự được đảm bảo **trong một partition**. Trong một group, mỗi partition chỉ giao cho một
  consumer, nên số consumer chạy song song tối đa bằng số partition.

### Push hay pull

| | Push (RabbitMQ) | Pull (Kafka) |
| --- | --- | --- |
| Cơ chế | Broker đẩy message đến consumer | Consumer chủ động lấy theo lô |
| Độ trễ | Rất thấp | Thấp (long polling tránh hỏi liên tục) |
| Kiểm soát tải | Broker phải tiết chế bằng prefetch (`basic.qos`) để không làm ngập consumer | Consumer tự quyết tốc độ, tự nhiên có backpressure |
| Thông lượng | Tốt | Rất cao nhờ lấy theo lô |

## Bản chất từng công nghệ

### Apache Kafka

- **Lưu trữ:** mỗi partition là chuỗi file segment trên đĩa, chỉ ghi nối tiếp vào cuối. Ghi tuần tự
  nhanh gần bằng ghi RAM; đọc tận dụng page cache của hệ điều hành; gửi qua mạng bằng zero-copy
  (`sendfile`); gom lô và nén (lz4, zstd). Đây là lý do Kafka nhanh.
- **Nhân bản:** mỗi partition có một leader và các follower. ISR (in-sync replicas) là tập replica
  đang theo kịp leader. Với `replication.factor=3`, `min.insync.replicas=2` và producer `acks=all`,
  một lần ghi chỉ được xác nhận khi ít nhất 2 bản sao đã nhận, nên mất một broker không mất dữ liệu.
- **Thứ tự:** message cùng key vào cùng partition (theo hash của key). Dùng số tài khoản làm key thì
  mọi sự kiện của một tài khoản giữ đúng thứ tự.
- **Ngữ nghĩa giao nhận:**
  - Commit offset **sau** khi xử lý: at-least-once, có thể xử lý trùng.
  - Commit **trước** khi xử lý: at-most-once, có thể mất.
  - Idempotent producer (mặc định bật từ Kafka 3.0) loại bỏ message trùng do producer retry.
  - Transaction của Kafka cho exactly-once khi đọc từ Kafka, xử lý rồi ghi lại Kafka (Kafka Streams).
    Khi có tác động ra ngoài (ghi DB, gửi SMS) thì vẫn cần **consumer idempotent**.
- **KRaft:** từ Kafka 4.0 không còn ZooKeeper; metadata do nhóm controller quản lý bằng Raft, vận
  hành đơn giản hơn trước nhiều.
- **Hệ sinh thái:** Kafka Connect, Debezium (CDC), Kafka Streams, Flink (xử lý luồng cho chống gian
  lận), Schema Registry (Avro, Protobuf) để tiến hóa cấu trúc sự kiện.
- **Điểm yếu:**
  - Không ack từng message. Một message lỗi chặn cả partition (head-of-line blocking), nên phải tự làm
    retry topic và dead letter topic.
  - Không có sẵn delay queue hay priority queue.
  - Số partition là quyết định dung lượng. Tăng được nhưng sẽ đổi ánh xạ key → partition, làm hỏng thứ
    tự của các key đang có. Không giảm được.
  - Rebalance consumer group tạm dừng việc đọc.
  - Thừa thãi với lưu lượng nhỏ.
  - Các bản Kafka 4.x đang bổ sung "share groups" (KIP-932, "Queues for Kafka") để có ngữ nghĩa giống
    hàng đợi, nhưng đây là tính năng mới, chưa phổ biến.

### RabbitMQ

- **AMQP 0-9-1:** producer gửi đến **exchange**; exchange định tuyến theo **binding** đến các
  **queue**: direct (khớp chính xác), topic (mẫu như `transfer.*.vnd`), fanout (gửi tất cả), headers.
  Định tuyến rất linh hoạt.
- Ack, nack hoặc requeue **từng message**; dead letter exchange; TTL; priority queue; delayed message
  (plugin); request-reply (RPC) qua `reply_to` và `correlation_id`.
- **Độ bền:** quorum queue (nhân bản bằng Raft) là lựa chọn khuyến nghị; classic mirrored queue đã bị
  bỏ từ RabbitMQ 4.0. Publisher confirms để biết chắc broker đã nhận.
- **Streams** (từ 3.9): kiểu log có replay, câu trả lời của RabbitMQ cho Kafka, nhưng hệ sinh thái nhỏ
  hơn.
- **Thứ tự:** FIFO trong một queue, nhưng có nhiều consumer hoặc có requeue thì không còn đảm bảo
  (phải dùng chế độ single active consumer).
- RabbitMQ được thiết kế cho hàng đợi **gần như rỗng**. Khi message dồn ứ nhiều, hiệu năng giảm.
- **Hợp nhất với:** hàng đợi công việc (task queue), định tuyến phức tạp, độ trễ thấp, RPC, team nhỏ
  cần thứ dễ vận hành.

### ActiveMQ Classic / Artemis (JMS)

- Cài đặt chuẩn **JMS (Jakarta Messaging)**: queue (point-to-point), topic (pub/sub), durable
  subscription, message selector, **transaction XA** (2PC cùng với DB qua JTA).
- Phổ biến trong các hệ thống Java doanh nghiệp đời cũ chạy trên application server (WebLogic,
  WebSphere, JBoss). Artemis là thế hệ mới, hiệu năng tốt hơn Classic.
- **Yếu:** thông lượng và khả năng mở rộng thấp hơn Kafka; cộng đồng kém sôi động; XA nặng nề.

### IBM MQ: rất phổ biến trong ngân hàng

- Queue manager cấp doanh nghiệp: transaction (syncpoint), giao nhận **đúng một lần** trong phạm vi
  MQ, kể cả XA với DB2/Oracle; chạy được trên mainframe z/OS và tích hợp với CICS và core banking;
  bảo mật mạnh (TLS, channel authentication); có hỗ trợ thương mại. Đó là lý do ngân hàng dùng nó cho
  tích hợp core: thanh toán, cổng SWIFT, hệ thống thẻ.
- **Yếu:** chi phí license, phụ thuộc nhà cung cấp, không thiết kế cho streaming, replay hay số lượng
  lớn bên đọc phục vụ phân tích.
- **Thực tế:** nhiều ngân hàng dùng **cả hai**: IBM MQ cho tích hợp giao dịch với core, Kafka cho
  streaming sự kiện, kênh digital và đường ống dữ liệu.

### Apache Pulsar

- **Tách lớp phục vụ và lớp lưu trữ:** broker không giữ trạng thái; dữ liệu nằm ở Apache BookKeeper
  (các bookie). Nhờ vậy scale hai lớp độc lập và phục hồi nhanh (không phải di chuyển dữ liệu partition
  khi thêm broker); có tiered storage xuống S3; multi-tenancy (tenant/namespace) và geo-replication
  dựng sẵn.
- Hỗ trợ cả streaming lẫn hàng đợi qua các kiểu subscription: exclusive, failover, shared,
  key_shared.
- **Yếu:** nhiều thành phần (broker, bookie, metadata store) nên vận hành phức tạp; cộng đồng và
  nguồn nhân lực ở Việt Nam ít; ít ví dụ áp dụng trong ngân hàng.

### Redis Streams

- Cấu trúc log trong Redis: `XADD`, consumer group với `XREADGROUP`, danh sách chờ ack (PEL),
  `XACK`, `XCLAIM` để giao lại.
- **Mạnh:** nhẹ, đã có Redis thì không cần thêm hạ tầng.
- **Yếu:** giới hạn bởi RAM; độ bền phụ thuộc AOF/RDB (mặc định `appendfsync everysec` có thể mất
  khoảng 1 giây dữ liệu khi sập); nhân bản bất đồng bộ nên failover có thể mất dữ liệu. Chỉ hợp với
  dữ liệu không quan trọng, quy mô nhỏ.

### NATS JetStream

- NATS lõi là pub/sub rất nhẹ, at-most-once. JetStream thêm lưu trữ bền, at-least-once, replay, ack;
  nhân bản bằng Raft; chỉ một file chạy, vận hành đơn giản; hợp với edge/IoT và microservices.
- **Yếu:** ít được dùng trong ngân hàng; hệ sinh thái Java doanh nghiệp (CDC, connector) mỏng hơn.

### Dịch vụ cloud (SQS/SNS, Kinesis, Google Pub/Sub, Azure Service Bus/Event Hubs)

Không phải vận hành, nhưng nhiều ngân hàng Việt Nam chạy on-premise hoặc private cloud vì yêu cầu về
dữ liệu và an ninh. Nên biết tương đương: SQS ≈ queue, SNS ≈ fanout, Kinesis/Event Hubs ≈ log kiểu
Kafka.

## Bảng so sánh

| Tiêu chí | Kafka | RabbitMQ | ActiveMQ Artemis | IBM MQ | Pulsar | Redis Streams | NATS JetStream |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Mô hình | Log phân vùng | Exchange + queue | Queue/topic JMS | Queue | Log + queue | Log trong RAM | Stream |
| Push/pull | Pull | Push (prefetch) | Cả hai | Cả hai | Push có flow control | Pull | Cả hai |
| Đọc lại (replay) | Có | Không (trừ Streams) | Không | Không | Có | Có (giới hạn RAM) | Có |
| Thứ tự | Theo partition | Theo queue, một consumer | Theo queue | Theo queue | Theo partition / key | Theo stream | Theo stream |
| Thông lượng | Rất cao | Trung bình đến cao | Trung bình | Trung bình đến cao | Rất cao | Cao | Cao |
| Định tuyến phức tạp | Yếu | Mạnh | Selector | Trung bình | Trung bình | Yếu | Wildcard theo subject |
| Delay / priority | Không có sẵn | Có | Có | Có | Delay có | Không | Hạn chế |
| Transaction | Chỉ trong Kafka | Publisher confirms | XA | XA, syncpoint | Có | Không | Hạn chế |
| Vận hành | Trung bình (KRaft dễ hơn) | Dễ | Dễ đến trung bình | Cần chuyên gia, có phí | Khó | Rất dễ | Dễ |
| Phổ biến ở ngân hàng Việt Nam | Rất cao | Cao | Thấp (hệ cũ) | Cao (tích hợp core) | Thấp | Thấp | Thấp |

## Quyết định cho WebBank: Kafka

Đối chiếu với yêu cầu:

| Yêu cầu | Kafka đáp ứng bằng |
| --- | --- |
| 1. Không mất sự kiện | `acks=all`, `min.insync.replicas=2`, replication factor 3; phía phát dùng outbox |
| 2. Thứ tự theo tài khoản | Key = số tài khoản, nên cùng partition |
| 3. Nhiều bên đọc độc lập | Mỗi bên một consumer group, mỗi group một offset |
| 4. Đọc lại | Retention theo thời gian, lùi offset |
| 5. Tải đột biến | Log trên đĩa hấp thụ đỉnh tải; consumer đọc theo tốc độ của mình |
| 6. Phổ biến, hệ sinh thái | Có mặt trong hầu hết JD ngân hàng; Debezium, Kafka Streams, Flink |

Bản chất của quyết định: dữ liệu của WebBank là **sự kiện nghiệp vụ** (sự thật đã xảy ra) mà nhiều hệ
thống cần đọc theo thứ tự và có thể đọc lại. Đó là bài toán của **log**, không phải bài toán **hàng đợi
công việc**.

**Một đánh đổi cần nói thật:** gửi SMS/email thông báo thực chất là "công việc" (task), và RabbitMQ
cũng hợp, thậm chí retry có delay còn dễ hơn. WebBank vẫn dùng Kafka cho phần này (consumer kèm retry
topic) để chỉ phải vận hành một broker. Nói được đánh đổi này trong phỏng vấn ghi điểm hơn là khẳng
định "Kafka tốt nhất".

## Khi nào nên chọn khác

- **RabbitMQ:** hàng đợi công việc (xuất PDF sao kê, gửi email) cần retry có delay, priority, TTL
  từng message; RPC request-reply; định tuyến phức tạp; lưu lượng vừa phải và team chưa có kinh nghiệm
  Kafka.
- **IBM MQ:** tích hợp với core banking hoặc mainframe, cần XA với DB2, hoặc đó là chuẩn của ngân hàng.
- **Pulsar:** cần multi-tenancy và geo-replication dựng sẵn, có đội vận hành mạnh.
- **Redis Streams / NATS:** dữ liệu không quan trọng, cần nhẹ và nhanh, không muốn thêm hạ tầng lớn.

## Dùng Kafka cho đúng (áp dụng ở giai đoạn 3)

- **Producer:** `acks=all`, bật idempotence, key là số tài khoản; ghi qua outbox để không mất sự kiện
  (xem [04](04-nhat-quan-du-lieu.md)).
- **Consumer:** commit offset sau khi xử lý; xử lý idempotent (bảng `processed_event`);
  `@RetryableTopic` có backoff và dead letter topic; giám sát consumer lag.
- **Sự kiện:** có `eventId`, `eventType`, `version`; chỉ thêm trường mới, không đổi nghĩa trường cũ.
- **Partition:** chọn từ đầu theo mức song song mong muốn (ví dụ 12), vì tăng về sau sẽ xáo trộn thứ tự.

## Câu hỏi phỏng vấn

**Kafka khác RabbitMQ ở bản chất thế nào?**
Kafka là log: giữ message theo retention, consumer tự quản offset, đọc lại được, pull theo lô.
RabbitMQ là queue: broker theo dõi từng message, xóa khi ack, push đến consumer, định tuyến mạnh.

**Kafka đảm bảo thứ tự thế nào? Khi nào mất thứ tự?**
Chỉ trong một partition. Thứ tự bị mất khi: tăng số partition (key đổi partition); xử lý một
partition bằng nhiều thread song song; producer retry khi không bật idempotence mà có nhiều request
đang bay (idempotent producer giữ được thứ tự với tối đa 5 request đang bay).

**Làm sao để không mất message?**
Phía producer: `acks=all`, `min.insync.replicas=2`, replication factor 3, outbox để không mất ở bước
"lưu DB rồi gửi". Phía consumer: commit offset sau khi xử lý xong.

**Kafka có exactly-once không?**
Có trong phạm vi Kafka-đến-Kafka (idempotent producer cộng transaction). Khi có tác động ra ngoài
(ghi DB, gửi SMS) thì thực tế là at-least-once cộng consumer idempotent.

**Một message lỗi mãi không xử lý được (poison pill)?**
Retry với backoff qua các retry topic, quá số lần thì đẩy vào dead letter topic, cảnh báo, sửa lỗi rồi
replay từ DLT. Không retry mãi trên partition chính vì sẽ chặn mọi message phía sau.

**Vì sao Kafka nhanh?**
Ghi tuần tự, page cache, zero-copy, gom lô, nén, song song theo partition.

**Consumer lag tăng thì xử lý thế nào?**
Tăng số consumer (tối đa bằng số partition); tối ưu xử lý (gom lô, bỏ lời gọi đồng bộ chậm); kiểm tra
rebalance liên tục; kiểm tra hệ thống phía sau có đang chậm không.
