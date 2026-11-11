# 03. Cơ sở dữ liệu, migration và cách truy cập dữ liệu

## Bài toán

Lưu tiền đòi hỏi: transaction ACID đáng tin, khóa dòng, ràng buộc (constraint) để chặn dữ liệu sai
ngay trong CSDL, truy vấn lịch sử nhanh, và schema thay đổi được an toàn qua nhiều phiên bản.

## Phần 1. Hệ quản trị cơ sở dữ liệu

### PostgreSQL

- **MVCC:** mỗi lần cập nhật tạo một phiên bản dòng mới (có `xmin`/`xmax`); người đọc thấy snapshot
  của mình nên **đọc không chặn ghi, ghi không chặn đọc**. VACUUM dọn các phiên bản cũ.
- **Isolation:** mặc định READ COMMITTED. REPEATABLE READ là snapshot isolation (chặn được cả phantom,
  mạnh hơn chuẩn SQL). SERIALIZABLE là SSI (Serializable Snapshot Isolation): phát hiện các xung đột
  nguy hiểm và hủy transaction với lỗi `40001`.
- **Tính năng WebBank dùng:** `SELECT … FOR UPDATE`, `NOWAIT`, `SKIP LOCKED`; CHECK constraint;
  constraint trigger trì hoãn đến lúc commit; `INSERT … ON CONFLICT`; `lock_timeout`; JSONB;
  partitioning; partial index.
- Mã nguồn mở, không tốn license, cộng đồng mạnh.

### Oracle Database

- **Thống trị ở core banking** (nhiều core thương mại chạy trên Oracle). MVCC dựa trên undo segment,
  người đọc không bao giờ bị chặn.
- RAC (nhiều node cùng phục vụ một database), Data Guard (dự phòng thảm họa), partitioning (option có
  phí), PL/SQL. Chi phí license cao.
- **Chi tiết hay bị hỏi:** SERIALIZABLE của Oracle thực chất là snapshot isolation, **vẫn có thể bị
  write skew**. Oracle có `FOR UPDATE WAIT n` (PostgreSQL không có). Trong Oracle, chuỗi rỗng `''`
  được coi là `NULL`.

### MySQL (InnoDB)

- MVCC dựa trên undo log. Mặc định REPEATABLE READ, và các câu đọc có khóa dùng **gap lock /
  next-key lock** để chống phantom, nên `SELECT … FOR UPDATE` có thể khóa cả khoảng trống giữa các
  dòng và dễ gây deadlock hơn.
- Phổ biến ở các công ty internet, ví điện tử, thương mại điện tử. CHECK constraint chỉ được thực thi
  từ bản 8.0.16; DDL không nằm trong transaction.

### SQL Server

Dùng trong một số ứng dụng nội bộ ngân hàng. Mặc định READ COMMITTED **dựa trên khóa** (đọc có thể bị
ghi chặn) trừ khi bật `READ_COMMITTED_SNAPSHOT`.

### MongoDB (document)

- Schema linh hoạt; có transaction nhiều document từ bản 4.0 nhưng kèm chi phí và giới hạn thời gian.
- Dữ liệu sổ cái **bản chất là quan hệ** (tài khoản, bút toán, ràng buộc cân bằng), và MongoDB yếu về
  ràng buộc (không có foreign key, CHECK chỉ qua schema validation).
- Hợp với: danh mục sản phẩm, hồ sơ người dùng linh hoạt, log, nội dung.

### Distributed SQL / NewSQL (CockroachDB, YugabyteDB, TiDB, Spanner)

- Scale ngang mà vẫn ACID, nhờ đồng thuận (Raft/Paxos) và transaction phân tán. CockroachDB mặc định
  SERIALIZABLE. TiDB tương thích MySQL, YugabyteDB tương thích PostgreSQL.
- **Đánh đổi:** mỗi transaction chậm hơn vì cần vòng đồng thuận giữa các node; vận hành còn mới mẻ.
  Dùng khi một node không chịu nổi lượng ghi, hoặc cần active-active nhiều vùng.

### So sánh

| Tiêu chí | PostgreSQL | Oracle | MySQL | MongoDB | NewSQL |
| --- | --- | --- | --- | --- | --- |
| ACID và constraint | Rất mạnh | Rất mạnh | Mạnh | Trung bình | Mạnh |
| Khóa dòng linh hoạt | `NOWAIT`, `SKIP LOCKED` | `WAIT n`, `SKIP LOCKED` | Có, kèm gap lock | Hạn chế | Có |
| Scale ngang phần ghi | Không có sẵn | RAC | Không có sẵn | Sharding | Có sẵn |
| Chi phí | Miễn phí | Rất cao | Miễn phí | Miễn phí / thương mại | Miễn phí / thương mại |
| Phổ biến ở ngân hàng Việt Nam | Tăng nhanh ở tầng digital | Rất cao ở core | Ở fintech, ví | Cho dữ liệu phi giao dịch | Thấp |
| Hợp với sổ cái | Rất hợp | Rất hợp | Hợp | Kém | Hợp khi cần scale lớn |

### Quyết định: PostgreSQL 17

Đủ mọi tính năng mà bài toán tiền cần (khóa dòng, constraint, trigger trì hoãn, `SKIP LOCKED` cho
outbox), miễn phí, chạy dễ trong Docker. Kiến thức áp dụng gần như nguyên vẹn sang Oracle: MVCC,
`FOR UPDATE`, sequence, partitioning. Cần nhớ các khác biệt: SERIALIZABLE của Oracle là snapshot,
Oracle không có `ON CONFLICT` (dùng `MERGE`), và `''` là `NULL`.

## Phần 2. Migration: Flyway, Liquibase hay `ddl-auto`?

| | Flyway | Liquibase | Hibernate `ddl-auto` |
| --- | --- | --- | --- |
| Bản chất | Các file SQL có version (`V1__`, `V2__`) chạy theo thứ tự; lưu checksum trong bảng lịch sử | Changelog (XML/YAML/SQL) gồm các changeset, có precondition, rollback | Hibernate tự sinh schema từ entity |
| Mạnh | Đơn giản; SQL thuần, DBA review được; dùng hết tính năng riêng của CSDL | Rollback có cấu trúc; một changelog cho nhiều loại CSDL; có công cụ diff | Nhanh khi làm prototype |
| Yếu | Rollback tự viết; mỗi CSDL một bộ SQL | Phức tạp hơn, XML dài dòng | **Không bao giờ dùng ở production**: không di chuyển được dữ liệu, có thể xóa hoặc sửa cột ngoài ý muốn, không review, không lịch sử |

**Quyết định: Flyway.** WebBank cần nhiều SQL đặc thù của PostgreSQL (trigger, constraint trì hoãn,
partition), viết thẳng bằng SQL là rõ ràng nhất. Hibernate chỉ chạy `validate` để kiểm tra entity
khớp schema. Chọn Liquibase khi phải hỗ trợ nhiều loại CSDL hoặc cần rollback có cấu trúc.

**Quy tắc vàng:** migration đã chạy thì **không bao giờ sửa** (checksum sẽ lệch); sai thì viết
migration mới để sửa tiếp. Muốn triển khai không downtime thì theo kiểu **expand–contract**: thêm cột
mới, code ghi cả hai cột, chuyển dữ liệu, đổi code đọc cột mới, cuối cùng mới xóa cột cũ.

## Phần 3. Truy cập dữ liệu: JPA, MyBatis, jOOQ hay JdbcClient?

### JPA / Hibernate (ORM)

- **Persistence context** (cache cấp 1): trong một transaction, mỗi dòng chỉ có một object.
- **Dirty checking:** sửa field của entity, đến lúc flush/commit Hibernate tự sinh `UPDATE`.
- Có optimistic lock (`@Version`), pessimistic lock (`LockModeType`), cascade, lazy loading.
- **Bẫy hay gặp:** N+1 query (duyệt danh sách rồi truy cập quan hệ lazy); `LazyInitializationException`;
  open-in-view giữ kết nối đến tận lúc render; flush ngầm trước khi query; khóa chính kiểu `IDENTITY`
  làm Hibernate **không gom lô INSERT được** (phải biết id ngay sau mỗi lần insert); query phức tạp
  viết bằng JPQL rất gượng.

### MyBatis (SQL mapper)

- Tự viết SQL (XML hoặc annotation), MyBatis map kết quả sang object. Toàn quyền với SQL.
- **Rất phổ biến ở các công ty Việt Nam**, nhất là nơi dùng Oracle và SQL phức tạp.
- Dài dòng với các thao tác CRUD đơn giản; không có dirty checking hay persistence context.

### jOOQ

- Sinh code từ schema, viết SQL bằng DSL Java **có kiểm tra kiểu lúc compile**; hỗ trợ tính năng
  riêng của từng CSDL.
- Bản dùng với Oracle/SQL Server phải trả phí; miễn phí với CSDL mã nguồn mở.

### JdbcTemplate / JdbcClient (Spring 6.1+)

- Lớp mỏng trên JDBC: SQL tường minh, map kết quả bằng tay hoặc tự động sang record.
- Hợp nhất cho báo cáo, thao tác hàng loạt, SQL đặc thù (`ON CONFLICT`, `SKIP LOCKED`, `LATERAL`,
  window function).

### Spring Data JDBC

Lưu aggregate theo kiểu DDD, không có lazy loading hay dirty checking, đơn giản và dễ đoán hơn JPA.

### Quyết định: JPA cho phía ghi, JdbcClient cho phía đọc và SQL đặc thù

- **JPA** cho các aggregate có bất biến cần bảo vệ (`Account`, `JournalEntry`, `Transfer`): dirty
  checking, `@Version`, khóa bi quan qua `LockModeType`.
- **JdbcClient** cho lịch sử giao dịch (join `LATERAL`, phân trang keyset), idempotency
  (`INSERT … ON CONFLICT`), outbox relay (`FOR UPDATE SKIP LOCKED`).
- Thông điệp gửi người phỏng vấn: **biết khi nào không nên dùng ORM**. Đây cũng là một dạng CQRS nhẹ:
  mô hình ghi và mô hình đọc tách nhau.

## Câu hỏi phỏng vấn

**MVCC là gì? Vì sao đọc không chặn ghi?**
Mỗi lần ghi tạo phiên bản mới, người đọc thấy snapshot tại thời điểm bắt đầu câu lệnh (READ COMMITTED)
hoặc transaction (REPEATABLE READ), nên không cần khóa để đọc.

**N+1 là gì, xử lý thế nào?**
Một query lấy N dòng, sau đó N query nữa để lấy quan hệ. Xử lý bằng `JOIN FETCH`, `@EntityGraph`,
`@BatchSize`, hoặc viết query DTO riêng.

**Vì sao tắt `open-in-view`?**
Bật nó thì session và kết nối DB bị giữ đến khi render xong response; lazy loading lén chạy query ở tầng
web, khó thấy N+1, cạn connection pool khi tải cao.

**Vì sao phân trang `OFFSET` chậm?**
`OFFSET 100000` vẫn phải đọc rồi bỏ 100.000 dòng. Keyset pagination (`WHERE id < :lastId ORDER BY id
DESC LIMIT 20`) đi thẳng vào index nên luôn nhanh, và không bị lặp hay sót dòng khi có dữ liệu mới
chèn vào.

**Index B-tree hoạt động thế nào? Thứ tự cột trong composite index có quan trọng không?**
Cây cân bằng, các lá được sắp xếp và liên kết với nhau. Thứ tự cột rất quan trọng: index
`(account_id, id)` phục vụ được `WHERE account_id = ? ORDER BY id`, nhưng không phục vụ được
`WHERE id = ?` một mình.

**Đổi tên một cột trên hệ thống đang chạy mà không downtime thế nào?**
Expand–contract như mô tả ở phần 2.
