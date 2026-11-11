# 07. Cache và Redis

## Bài toán

Cache thứ gì trong ngân hàng?

- **Nên cache:** dữ liệu tham chiếu ít thay đổi (danh sách ngân hàng, biểu phí, cấu hình sản phẩm),
  tỷ giá có TTL ngắn.
- **Dùng Redis nhưng không phải để cache:** bộ đếm rate limit, quy tắc velocity chống gian lận,
  trạng thái tạm thời có hạn (OTP challenge).
- **Không bao giờ cache:** số dư dùng để quyết định cho phép giao dịch. Số dư cũ một giây cũng có thể
  dẫn đến chi vượt. Đọc số dư theo khóa chính đã rất rẻ, không đáng đánh đổi.

## Các lựa chọn

| Lựa chọn | Bản chất | Mạnh | Yếu |
| --- | --- | --- | --- |
| Caffeine (cache trong tiến trình) | Map trong heap của JVM, thuật toán loại bỏ W-TinyLFU | Nhanh nhất (nano giây), không qua mạng | Mỗi instance một bản, khó làm mất hiệu lực đồng loạt; tốn heap |
| **Redis** | Server lưu cấu trúc dữ liệu trong RAM (string, hash, list, set, sorted set, stream); lệnh thực thi tuần tự nên mỗi lệnh là nguyên tử; script Lua; TTL; lưu bền bằng RDB/AOF; nhân bản qua Sentinel hoặc Cluster | Đa năng: cache, bộ đếm, rate limit, khóa, hàng đợi nhẹ | Giới hạn bởi RAM; nhân bản bất đồng bộ nên failover có thể mất dữ liệu vừa ghi |
| Memcached | Key-value thuần, đa luồng, không lưu bền | Đơn giản, nhanh cho cache thuần | Không có cấu trúc dữ liệu, không có thao tác nguyên tử phong phú |
| Hazelcast, Apache Ignite | Data grid phân tán trong bộ nhớ: map phân tán, near cache, tính toán phân tán | Nhúng được vào ứng dụng | Phức tạp, ít phổ biến hơn |

Về giấy phép: năm 2024 Redis đổi giấy phép, cộng đồng tách ra bản **Valkey** (Linux Foundation) tương
thích hoàn toàn; Redis 8 đã bổ sung lại lựa chọn giấy phép mã nguồn mở AGPL. Cần biết để trả lời nếu
được hỏi.

**Quyết định: Redis**, vì WebBank cần bộ đếm nguyên tử cho rate limit và velocity, không chỉ cần
cache. Có thể thêm Caffeine làm cache tầng 1 cho dữ liệu tham chiếu rất nóng.

## Các pattern cache

| Pattern | Bản chất |
| --- | --- |
| **Cache-aside** (phổ biến nhất) | Đọc cache; không có thì đọc DB rồi ghi vào cache. Khi cập nhật thì ghi DB rồi **xóa** key cache |
| Read-through / write-through | Thư viện cache tự đọc hoặc tự ghi DB |
| Write-behind | Ghi cache trước, ghi DB bất đồng bộ sau. Nhanh nhưng có thể mất dữ liệu, không dùng cho tiền |

**Ba sự cố kinh điển:**

- **Cache stampede:** một key nóng hết hạn, hàng nghìn request cùng đâm vào DB. Chống bằng cách chỉ cho
  một request nạp lại (khóa hoặc gộp request), làm mới sớm trước khi hết hạn.
- **Cache penetration:** truy vấn liên tục các key không tồn tại nên luôn trượt cache. Chống bằng cache
  cả kết quả rỗng, hoặc Bloom filter.
- **Cache avalanche:** nhiều key hết hạn cùng lúc. Chống bằng TTL có jitter.

## Distributed lock bằng Redis: dùng được đến đâu?

Redisson `RLock` hay thuật toán Redlock là khóa có **thời hạn** (lease). Tiến trình giữ khóa có thể bị
dừng (GC pause, mạng chậm) lâu hơn thời hạn; khóa hết hạn, tiến trình khác lấy được; tiến trình đầu
tỉnh lại vẫn tưởng mình đang giữ khóa. Muốn đúng tuyệt đối phải có **fencing token** (số tăng dần kèm
mỗi lần lấy khóa, bên lưu trữ từ chối token cũ). Đây là nội dung cuộc tranh luận nổi tiếng giữa Martin
Kleppmann và tác giả Redis.

**Kết luận:** khóa Redis dùng để **tránh làm trùng việc** (hiệu quả), không dùng để **bảo vệ tính
đúng đắn của tiền**. Tiền được bảo vệ bằng transaction và constraint của DB.

## Câu hỏi phỏng vấn

**Vì sao Redis nhanh?**
Dữ liệu trong RAM; vòng lặp sự kiện đơn luồng nên không tốn chi phí khóa; cấu trúc dữ liệu tối ưu;
I/O multiplexing (từ Redis 6 có thêm I/O thread cho phần mạng).

**Redis lưu bền thế nào?**
RDB chụp snapshot định kỳ; AOF ghi log từng lệnh (`appendfsync always`, `everysec` hoặc `no`). Dùng
kết hợp cả hai.

**Khi đầy bộ nhớ thì sao?**
Áp dụng chính sách loại bỏ (`maxmemory-policy`): `allkeys-lru`, `volatile-ttl`, `noeviction`…

**Redis Cluster chia dữ liệu thế nào?**
16.384 hash slot, slot = CRC16(key) mod 16384. Lệnh thao tác nhiều key phải cùng slot, dùng hash tag
`{customer:42}` để ép các key về cùng slot.

**Giữ cache và DB nhất quán thế nào?**
Cache-aside: ghi DB rồi xóa cache, cộng TTL ngắn làm lưới an toàn. Nếu cần chặt hơn thì xóa cache theo
sự kiện CDC.
