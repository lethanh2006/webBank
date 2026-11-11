# 05. Bảo mật: danh tính, token, xác thực mạnh, rate limit, bảo vệ dữ liệu

## Bài toán

Xác thực người dùng (authentication), phân quyền (authorization), xác thực bổ sung cho giao dịch lớn,
chống lạm dụng (dò OTP, dò số tài khoản), ghi vết kiểm toán, bảo vệ dữ liệu cá nhân.

## 1. Ai quản lý danh tính?

| Lựa chọn | Bản chất | Mạnh | Yếu |
| --- | --- | --- | --- |
| Tự viết (bảng user, mật khẩu, tự phát JWT) | Toàn bộ do ứng dụng làm | Toàn quyền | Phải tự làm băm mật khẩu, MFA, khóa tài khoản, quên mật khẩu, xoay khóa ký, tuân thủ OIDC. Rủi ro cao, bị kiểm toán bảo mật đánh giá thấp |
| **Keycloak** | IAM mã nguồn mở: OIDC, OAuth2, SAML, MFA, liên kết LDAP/Active Directory | Chuẩn mực, tự host (dữ liệu nằm on-premise), nối được với thư mục nhân viên của ngân hàng | Nặng (cần DB riêng), nâng cấp phải cẩn thận |
| Spring Authorization Server | Thư viện để tự dựng authorization server bằng Spring | Tùy biến sâu bằng Java | Phải tự làm giao diện đăng nhập, quản lý user, MFA |
| SaaS (Auth0/Okta, Entra ID, Cognito) | Dịch vụ thuê ngoài | Không phải vận hành | Dữ liệu ra nước ngoài, vướng yêu cầu của ngân hàng; trả phí theo số người dùng |
| WSO2 Identity Server, ForgeRock, Ping | IAM doanh nghiệp | Hỗ trợ thương mại | Phí, phức tạp |

**Quyết định: Keycloak.** Chuẩn OIDC, chạy on-premise, có sẵn MFA, khởi động bằng một container. Ứng
dụng không bao giờ thấy mật khẩu người dùng.

## 2. Loại token: JWT, opaque token hay session?

| | JWT (tự chứa, có chữ ký) | Opaque token + introspection (RFC 7662) | Session phía server (cookie) |
| --- | --- | --- | --- |
| Kiểm tra | Resource server tự kiểm chữ ký bằng public key (JWKS), không gọi IdP | Mỗi request gọi IdP hỏi token còn hợp lệ không | Tra session store |
| Thu hồi | Không thu hồi tức thì được; dùng thời hạn ngắn, xoay refresh token, deny-list khi khẩn cấp | Tức thì | Tức thì |
| Mở rộng | Rất tốt | Tải dồn về IdP (phải cache) | Cần session store chung (Redis) |
| Lưu ý | Payload ai cũng đọc được, không chứa dữ liệu cá nhân nhạy cảm | Thêm độ trễ | Cần chống CSRF |

**Quyết định:** access token JWT thời hạn 5 phút, refresh token được Keycloak xoay vòng (rotation).

## 3. Ứng dụng trên trình duyệt: SPA + PKCE hay BFF?

- **SPA + Authorization Code + PKCE:** SPA là public client, nhận token và giữ trong bộ nhớ JavaScript.
  PKCE chống việc kẻ gian chặn authorization code. Nhược điểm: nếu trang bị XSS, mã độc lấy được
  token.
- **BFF (Backend-for-Frontend):** backend là confidential client, tự làm OAuth; trình duyệt chỉ có
  cookie `HttpOnly` + `SameSite`, JavaScript không bao giờ chạm token. Đây là cách được khuyến nghị
  cho ứng dụng nhạy cảm theo hướng dẫn "OAuth 2.0 for Browser-Based Applications" của IETF. Đổi lại
  cần thêm một thành phần server và phải chống CSRF.
- **Implicit flow:** đã bị loại bỏ, không dùng.

**Quyết định:** giai đoạn 2 dùng SPA + PKCE cho đơn giản, token chỉ giữ trong bộ nhớ (không
`localStorage`). Phải nói rõ trong phỏng vấn: ngân hàng thật nên dùng BFF, và đây là bước nâng cấp
tiếp theo của dự án.

## 4. Xác thực mạnh cho giao dịch (step-up)

| Phương thức | Bản chất | Điểm yếu |
| --- | --- | --- |
| SMS OTP | Mã gửi qua tin nhắn | Bị chiếm SIM (SIM swap), chặn sóng (SS7), lừa người dùng đọc mã; tốn phí SMS |
| TOTP (RFC 6238, ứng dụng authenticator) | `HMAC-SHA1(secret, thời gian / 30 giây)`, rút gọn còn 6 số | Vẫn bị lừa nhập mã vào trang giả (phishing) |
| Smart OTP (phổ biến ở ngân hàng Việt Nam) | OTP sinh trong app ngân hàng, gắn với thiết bị, có thể gắn với nội dung giao dịch | Phụ thuộc bảo mật thiết bị |
| Xác nhận push trong app | Bấm "đồng ý" trên thiết bị đã đăng ký | Bị tấn công "MFA fatigue" (spam yêu cầu); chống bằng bắt nhập số khớp |
| Sinh trắc học | Khuôn mặt, đối chiếu dữ liệu CCCD gắn chip theo QĐ 2345/QĐ-NHNN | Cần hạ tầng và dữ liệu đối chiếu |
| FIDO2 / passkey | Chữ ký khóa công khai gắn với domain | Chống phishing thật sự; xu hướng tương lai |

**Ký giao dịch (dynamic linking):** mã xác thực được tính từ **nội dung giao dịch** (số tiền, người
nhận). Mã chỉ hợp lệ cho đúng giao dịch đó, nên mã độc trong trình duyệt có đổi người nhận thì mã cũng
vô hiệu.

**Quyết định:** OTP gắn với hash của (tài khoản nguồn, tài khoản đích, số tiền), mô phỏng Smart OTP;
ngưỡng bắt buộc xác thực mạnh cấu hình được theo tinh thần QĐ 2345/QĐ-NHNN (trên 10 triệu đồng một
lần hoặc trên 20 triệu đồng một ngày). OTP lưu dạng hash, hết hạn sau 3 phút, sai tối đa 5 lần.

## 5. Rate limit

**Thuật toán:**

| Thuật toán | Bản chất | Đặc điểm |
| --- | --- | --- |
| Fixed window | Đếm số request trong từng khung (ví dụ mỗi phút) | Đơn giản; cho phép gấp đôi tải ở ranh giới hai khung |
| Sliding window log | Lưu thời điểm từng request | Chính xác, tốn bộ nhớ |
| Sliding window counter | Nội suy giữa khung hiện tại và khung trước | Gần đúng, rẻ |
| **Token bucket** | Thùng có sức chứa C, nạp lại r token mỗi giây, mỗi request lấy một token | Cho phép đột biến ngắn (tối đa C), giữ tốc độ trung bình r. Bucket4j dùng thuật toán này |
| Leaky bucket | Request xếp hàng và chảy ra với tốc độ cố định | Làm phẳng lưu lượng đầu ra |

**Đặt ở đâu:** ở rìa (CDN, WAF, Nginx, API gateway) để chặn thô theo IP; ở ứng dụng để chặn theo
người dùng và theo nghiệp vụ (xác minh OTP, tra cứu tên chủ tài khoản). Ngân hàng thật làm cả hai lớp.

**Lưu ở đâu:** bộ nhớ từng instance (mỗi instance đếm riêng, sai khi có nhiều instance) hoặc Redis
(dùng chung, nguyên tử qua Lua).

**Quyết định:** Bucket4j + Redis ở tầng ứng dụng cho các endpoint nhạy cảm.

## 6. Phân quyền

- **RBAC** (theo vai trò: `CUSTOMER`, `TELLER`, `SUPERVISOR`): đơn giản nhưng thô.
- **Kiểm tra quyền sở hữu (ABAC đơn giản):** tài nguyên thuộc về ai. Đây là cách chống **BOLA/IDOR**,
  lỗi số 1 trong OWASP API Security Top 10: chỉ đổi id trên URL là xem được tài khoản người khác.
- **Policy engine** (OPA, Cedar, Keycloak Authorization Services): khi quy tắc phức tạp.

**Quyết định:** RBAC + kiểm tra sở hữu trong truy vấn (`WHERE account_no = ? AND customer_id = ?`).
Tài nguyên không thuộc về mình thì trả `404` chứ không phải `403`, để không lộ thông tin "tài khoản
này tồn tại".

## 7. Bảo vệ dữ liệu

- **Băm (một chiều)** dùng cho mật khẩu (bcrypt/argon2, cố ý chậm để chống dò) và OTP. **Mã hóa (hai
  chiều)** dùng cho dữ liệu cần đọc lại như số CCCD.
- **AES-GCM hay AES-CBC:** GCM là mã hóa có xác thực (vừa bí mật vừa toàn vẹn), bắt buộc nonce không
  lặp. CBC phải kèm MAC riêng và từng bị tấn công padding oracle.
- **Quản lý khóa:** biến môi trường (dev), Vault/KMS, và HSM (ngân hàng dùng HSM cho khóa và xử lý mã
  PIN).
- **Mã hóa cột hay TDE:** TDE mã hóa ở mức đĩa, chống mất ổ đĩa nhưng không chống người có quyền truy
  cập CSDL. Mã hóa cột ở ứng dụng chống được cả DBA tò mò.
- Che dữ liệu trong log (`1234****5678`); số thẻ dùng tokenization theo PCI DSS.

## 8. Audit log

| Cách | Đánh giá |
| --- | --- |
| **Bảng chỉ ghi thêm + hash chain + thu quyền `UPDATE`/`DELETE`** | Phát hiện được sửa hoặc xóa; nằm ngay trong CSDL giao dịch |
| Ghi ra log tập trung (ELK) | Dễ tìm kiếm nhưng sửa được |
| Lưu trữ WORM (ghi một lần) | Chống sửa tuyệt đối, chi phí cao |
| Audit ở mức CSDL (pgaudit, Oracle Audit Vault) | Ghi mọi câu lệnh, thiếu ngữ cảnh nghiệp vụ |

**Quyết định:** bảng chỉ ghi thêm có hash chain; khi triển khai thật thì đẩy thêm sang SIEM.

## Câu hỏi phỏng vấn

**OAuth2 khác OIDC thế nào?**
OAuth2 là ủy quyền (access token để gọi API). OIDC xây trên OAuth2 để xác thực, thêm ID token cho biết
người dùng là ai.

**PKCE giải quyết vấn đề gì?**
Public client không giữ được secret. PKCE gắn authorization code với một `code_verifier` ngẫu nhiên mà
chỉ client ban đầu biết, nên kẻ chặn được code cũng không đổi ra token được.

**Lưu token ở đâu trên trình duyệt?**
Tốt nhất là không lưu (BFF). Nếu là SPA thì giữ trong bộ nhớ, không dùng `localStorage` vì bất kỳ
script nào (XSS) cũng đọc được.

**Thu hồi JWT thế nào?**
Thời hạn ngắn, xoay refresh token, deny-list theo `jti` cho trường hợp khẩn cấp, hoặc chuyển sang
introspection.

**CSRF khác XSS thế nào?**
CSRF lừa trình duyệt gửi request kèm cookie; chống bằng `SameSite`, CSRF token. XSS chạy mã độc trong
trang; chống bằng escape dữ liệu, CSP, không dùng `innerHTML` với dữ liệu người dùng.

**TOTP hoạt động thế nào?**
Hai bên chia sẻ một secret. Mã = HMAC(secret, số khoảng 30 giây kể từ epoch), cắt gọn còn 6 số. Server
chấp nhận lệch ±1 khoảng để bù lệch đồng hồ.
