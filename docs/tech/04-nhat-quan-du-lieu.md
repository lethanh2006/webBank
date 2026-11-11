# 04. Nhất quán dữ liệu: khóa, isolation, idempotency, outbox, saga

Đây là phần người phỏng vấn ngân hàng đào sâu nhất, vì mọi lỗi ở đây đều là **mất tiền**.

## 1. Isolation level và các hiện tượng bất thường

| Hiện tượng | Ví dụ trong ngân hàng | READ COMMITTED | REPEATABLE READ (PostgreSQL) | SERIALIZABLE |
| --- | --- | --- | --- | --- |
| Dirty read: đọc dữ liệu chưa commit | Thấy số dư của một giao dịch sắp rollback | Chặn | Chặn | Chặn |
| Non-repeatable read: đọc lại một dòng thấy khác | Kiểm tra số dư hai lần trong một transaction ra hai số | **Có thể xảy ra** | Chặn | Chặn |
| Phantom: đọc lại tập dòng thấy thêm dòng mới | Tổng giao dịch trong ngày thay đổi giữa hai lần đếm | **Có thể xảy ra** | Chặn | Chặn |
| Lost update: hai bên cùng đọc rồi cùng ghi, mất một lần ghi | Hai lệnh cùng đọc số dư 100, cùng trừ 80 | **Có thể xảy ra** | Phát hiện, báo lỗi `40001` | Chặn |
| Write skew: hai bên đọc cùng dữ liệu, mỗi bên ghi một chỗ khác, cùng vi phạm một quy tắc | Hai lệnh cùng thấy "đã dùng 8/10 triệu hạn mức", mỗi lệnh ghi một giao dịch 2 triệu | **Có thể xảy ra** | **Có thể xảy ra** | Chặn |

Lưu ý: READ COMMITTED (mặc định của PostgreSQL và Oracle) **không** tự chống được lost update. Muốn
chống phải khóa tường minh, dùng câu update nguyên tử, hoặc nâng isolation.

## 2. Các kỹ thuật chống tranh chấp

| Kỹ thuật | Bản chất | Mạnh | Yếu | Dùng khi |
| --- | --- | --- | --- | --- |
| **Khóa bi quan** `SELECT … FOR UPDATE` | Khóa dòng đến hết transaction, người đến sau phải chờ | Đúng chắc chắn, dễ hiểu | Chờ đợi; có thể deadlock nếu khóa không theo thứ tự | Tranh chấp cao, dữ liệu là tiền |
| **Khóa lạc quan** `@Version` | Không khóa khi đọc; `UPDATE … WHERE version = ?`, không có dòng nào khớp thì báo xung đột | Không chờ, không deadlock | Tranh chấp cao thì retry liên tục (retry storm) | Dữ liệu ít người sửa cùng lúc |
| **Update nguyên tử có điều kiện** `UPDATE … SET x = x - :a WHERE id = ? AND x >= :a` | Đọc, kiểm tra và ghi trong một câu lệnh | Nhanh nhất, khóa giữ rất ngắn | Khó dùng khi cần nhiều bước logic | Bộ đếm, hạn mức |
| **SERIALIZABLE** | CSDL tự phát hiện xung đột, hủy một transaction | Code đơn giản nhất | Mọi chỗ phải retry lỗi `40001`; thông lượng giảm khi tranh chấp | Quy tắc phức tạp trên nhiều dòng |
| **Advisory lock** `pg_advisory_xact_lock(key)` | Khóa theo khóa logic do ứng dụng tự đặt | Linh hoạt (khóa theo khách hàng, theo ngày) | Dễ dùng sai, khó nhìn thấy | Khóa thứ không phải một dòng cụ thể |
| **Distributed lock** (Redis/Redisson, ZooKeeper) | Khóa có thời hạn (lease) đặt ở hệ thống ngoài | Phối hợp giữa các service không chung DB | Lease hết hạn khi tiến trình bị dừng (GC pause), nên hai bên cùng nghĩ mình giữ khóa; cần fencing token | Tránh làm trùng việc, **không** dùng để bảo vệ tiền |
| **Single writer** (một luồng hoặc partition cho mỗi tài khoản) | Tuần tự hóa mọi thao tác của một tài khoản qua một hàng đợi | Không cần khóa, thông lượng rất cao | Kiến trúc phức tạp | Sổ cái hiệu năng cực cao (LMAX, TigerBeetle) |

### Quyết định cho WebBank

- **Chuyển tiền:** khóa bi quan cả hai tài khoản, **luôn theo thứ tự id tăng dần**, có
  `lock_timeout`.
- **Dữ liệu ít tranh chấp** (hồ sơ, yêu cầu phê duyệt): `@Version`.
- **Hạn mức ngày:** update nguyên tử có điều kiện trên một dòng `limit_usage`, chặn được write skew.
- **Chốt chặn cuối cùng:** `CHECK (available_balance >= 0)` trong CSDL. Code có bug thì CSDL vẫn
  từ chối.

### Vì sao khóa theo thứ tự thì không deadlock?

Deadlock cần đủ 4 điều kiện Coffman: loại trừ lẫn nhau, giữ và chờ, không thể tước đoạt, **chờ vòng
tròn**. Lệnh A→B khóa A rồi chờ B, lệnh B→A khóa B rồi chờ A: đó là vòng tròn. Nếu mọi transaction
đều khóa tài khoản có id nhỏ trước thì không thể có vòng tròn, nên deadlock không xảy ra. PostgreSQL
vẫn có cơ chế phát hiện deadlock (sau `deadlock_timeout`, mặc định 1 giây, hủy một bên), nhưng đó là
lưới an toàn, không phải thiết kế.

### Tài khoản "nóng" (hot account)

Tài khoản trung gian hoặc tài khoản của một merchant lớn có thể nhận hàng nghìn giao dịch mỗi giây.
Mọi giao dịch đều chờ khóa trên cùng một dòng. Các hướng giải quyết:

- Chia nhỏ số dư thành N tài khoản con (sharded balance), mỗi giao dịch chọn ngẫu nhiên một tài khoản
  con; số dư thật bằng tổng các tài khoản con.
- Với tài khoản nội bộ, không duy trì số dư chạy theo từng giao dịch mà tính từ bảng posting theo chu
  kỳ.
- Gom nhiều giao dịch ghi có thành một bút toán.

## 3. Idempotency: chống xử lý trùng

| Cách | Bản chất | Đánh giá |
| --- | --- | --- |
| **Idempotency-Key do client sinh, lưu trong DB cùng transaction** | Unique `(phạm vi, key)`, lưu hash request và response | Đúng tuyệt đối: transaction commit thì cả giao dịch lẫn key cùng tồn tại, rollback thì cùng mất |
| Unique trên mã nghiệp vụ do client gửi | Ví dụ unique `client_reference` trên bảng transfer | Đơn giản, nhưng không trả lại được response cũ |
| Redis `SETNX` có TTL | Đánh dấu key trong Redis trước khi xử lý | Nhanh, nhưng **không nguyên tử với transaction DB**: Redis báo "mới", DB lỗi thì key kẹt; DB commit mà ghi Redis lỗi thì có thể trùng. Chỉ dùng làm bộ lọc sơ bộ |
| Inbox phía consumer | Bảng `processed_event(consumer, event_id)` ghi cùng transaction với tác động | Chuẩn cho consumer Kafka |

**WebBank làm như sau** (trong một transaction):

1. `INSERT INTO idempotency_key … ON CONFLICT DO NOTHING RETURNING id`.
2. Chèn được: thực hiện nghiệp vụ, lưu response, commit.
3. Không chèn được (key đã có): so sánh hash request. Khác thì trả `422`; giống thì trả lại response
   đã lưu, kèm header `Idempotent-Replayed: true`.
4. **Hai request trùng key đến cùng lúc:** request sau bị PostgreSQL bắt chờ trên unique index cho đến
   khi request trước commit, rồi rơi vào bước 3. Không cần khóa nào khác.
5. Nghiệp vụ lỗi (ví dụ không đủ tiền) thì cả transaction rollback, key cũng mất, nên client retry là
   thực hiện lại. An toàn vì chưa có đồng nào dịch chuyển.

## 4. Dual write: lưu DB rồi gửi Kafka sao cho không mất sự kiện

**Vấn đề:** commit DB xong mới gửi Kafka, sập ở giữa thì mất sự kiện. Gửi Kafka trước, DB rollback thì
có sự kiện "ma" cho một giao dịch không tồn tại.

| Cách | Bản chất | Mạnh | Yếu |
| --- | --- | --- | --- |
| 2PC / XA (JTA: Atomikos, Narayana) | Bộ điều phối hỏi các bên "sẵn sàng chưa", rồi ra lệnh commit đồng loạt | Nguyên tử thật sự | **Kafka không hỗ trợ XA**; giao thức blocking (điều phối viên chết thì các bên giữ khóa chờ mãi); chậm |
| **Transactional outbox + polling** | Ghi sự kiện vào bảng `outbox` cùng transaction nghiệp vụ; một relay đọc bảng và gửi Kafka | Đơn giản, chỉ cần DB | Độ trễ bằng chu kỳ poll; tốn tải DB; phải cẩn thận thứ tự khi có nhiều relay |
| Outbox + CDC (Debezium) | Debezium đọc WAL của PostgreSQL và đẩy lên Kafka | Độ trễ thấp, không poll, giữ đúng thứ tự commit | Cần Kafka Connect, Debezium, cấu hình logical replication |
| Event sourcing | Sự kiện chính là dữ liệu gốc; trạng thái được dựng lại từ sự kiện | Không có dual write | Phức tạp: projection, version sự kiện, snapshot, đọc nhất quán cuối cùng |
| "Listen to yourself" | Gửi Kafka trước, tự consume để cập nhật DB | Một lần ghi | DB cập nhật bất đồng bộ, không đọc ngay được dữ liệu vừa ghi; không hợp với số dư |
| Event Publication Registry (Spring Modulith) | Outbox do framework cung cấp | Ít code | Cơ chế ẩn sau framework, phải hiểu kỹ mới giải thích được |

**Quyết định:** outbox + polling, relay dùng `SELECT … FOR UPDATE SKIP LOCKED` để nhiều instance chạy
song song không lấy trùng bản ghi. Khi cần độ trễ thấp hơn thì chuyển sang Debezium mà không phải đổi
bảng outbox.

## 5. Saga: giao dịch xuyên hệ thống

Chuyển tiền liên ngân hàng chạm vào hệ thống của người khác, nên không thể có một transaction chung.
Saga là chuỗi các transaction cục bộ; nếu một bước thất bại thì chạy các bước **bù trừ**
(compensation).

| | Choreography | Orchestration |
| --- | --- | --- |
| Bản chất | Mỗi bên nghe sự kiện và tự phát sự kiện tiếp theo, không có trung tâm | Một bộ điều phối (state machine) ra lệnh cho từng bên |
| Mạnh | Lỏng lẻo, không có điểm trung tâm | Luồng tường minh; dễ đặt timeout, dễ theo dõi, dễ bù trừ |
| Yếu | Luồng ẩn, khó lần theo; dễ phụ thuộc vòng; thêm bước thì khó | Bộ điều phối là nơi tập trung logic |

**Quyết định:** orchestration với state machine lưu trong DB (`FUNDS_HELD → SUBMITTED → COMPLETED |
FAILED | UNKNOWN`).

**Các khái niệm phải nắm:**

- **Bù trừ khác rollback:** rollback như chưa từng xảy ra; bù trừ là một hành động nghiệp vụ mới
  (giải tỏa số tiền đã phong tỏa) và các trạng thái trung gian là có thật, người khác nhìn thấy được.
- **Pivot transaction (điểm không quay lại):** khi clearing đã chấp nhận lệnh thì không bù trừ được
  nữa, chỉ có thể đi tiếp.
- **Thiếu isolation giữa các bước:** dùng **semantic lock**. Phong tỏa (hold) làm giảm số dư khả dụng,
  nên số tiền đang chờ không thể bị tiêu lần nữa.
- **Timeout không có nghĩa là thất bại:** phải tra soát trước khi bù trừ.

## 6. Tự viết state machine hay dùng workflow engine?

| Lựa chọn | Bản chất | Hợp khi |
| --- | --- | --- |
| **Tự viết** (enum trạng thái, bảng chuyển trạng thái, scheduler xử lý timeout) | Toàn quyền, ít phụ thuộc | Ít trạng thái, cần hiểu từng dòng code |
| Spring State Machine | Thư viện state machine | Cấu hình nặng, lưu trạng thái vụng, dự án ít hoạt động |
| Temporal | "Durable execution": viết workflow như code thường, engine tự lưu trạng thái, tự retry, tự hẹn giờ | Saga dài, nhiều bước, cần độ bền cao; phải vận hành thêm Temporal server |
| Camunda / Zeebe (BPMN) | Quy trình nghiệp vụ dạng sơ đồ, có tác vụ cho con người | Quy trình có người duyệt (ví dụ cấp tín dụng), phổ biến trong ngân hàng |

**Quyết định:** tự viết, vì chỉ có 5–6 trạng thái và mục tiêu là hiểu bản chất. Nêu được Temporal hoặc
Camunda là bước tiếp theo khi quy trình phức tạp (ví dụ khởi tạo khoản vay có nhiều cấp duyệt).

## Câu hỏi phỏng vấn

**Optimistic hay pessimistic?**
Tranh chấp cao, chi phí xung đột lớn (tiền) thì dùng pessimistic. Tranh chấp thấp thì optimistic để
không phải chờ.

**Những bẫy của `@Transactional`?**
- Spring dùng proxy, nên gọi phương thức `@Transactional` từ chính class đó (self-invocation) thì
  không qua proxy, không có transaction.
- Mặc định chỉ rollback với `RuntimeException` và `Error`; checked exception vẫn commit nếu không khai
  báo `rollbackFor`.
- Bắt exception rồi nuốt đi bên trong transaction: transaction có thể đã bị đánh dấu rollback-only,
  đến lúc commit mới nổ `UnexpectedRollbackException`.
- `REQUIRES_NEW` lấy thêm một kết nối trong khi kết nối cũ vẫn bị giữ, dễ cạn pool khi tải cao.
- Gọi HTTP ra ngoài bên trong transaction: khóa bị giữ suốt thời gian chờ mạng.

**Vì sao không dùng 2PC với Kafka?**
Kafka không hỗ trợ XA. Kể cả với broker có XA, 2PC là giao thức blocking và làm chậm hệ thống.

**Kể một ví dụ write skew và cách chống.**
Kiểm tra hạn mức ngày. Chống bằng update nguyên tử có điều kiện trên một dòng tổng hợp, bằng khóa dòng
đó, hoặc bằng SERIALIZABLE.
