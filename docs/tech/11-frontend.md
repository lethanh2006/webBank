# 11. Frontend

Frontend không phải trọng tâm chấm điểm của vị trí Java backend, nhưng vẫn nên giải thích được lựa
chọn, và biết các vấn đề bảo mật đặc thù của giao diện ngân hàng.

## 1. Framework: React, Angular hay Vue?

| | Angular | React | Vue |
| --- | --- | --- | --- |
| Bản chất | **Framework đầy đủ**: DI, router, forms, HttpClient, RxJS, cấu trúc dự án định sẵn; dùng TypeScript từ đầu; Signals từ bản 16 | **Thư viện UI**; router, quản lý state, form do cộng đồng cung cấp | Framework "lũy tiến", học nhanh |
| Mạnh | Đồng nhất cho team lớn, giống tư duy backend Java (DI, service) | Hệ sinh thái và nguồn nhân lực lớn nhất, linh hoạt | Dễ học, cú pháp gọn |
| Yếu | Nặng, học lâu | Phải tự chọn và ghép thư viện | Ít gặp hơn trong ngân hàng ở Việt Nam |
| Ở ngân hàng Việt Nam | Phổ biến cho cả cổng nội bộ và kênh khách hàng | Phổ biến | Ít |

**Quyết định: React.** Hệ sinh thái lớn nhất, và frontend không phải trọng tâm. Nếu ngân hàng ứng tuyển
ghi rõ Angular trong JD thì nên đổi sớm, vì phần backend không bị ảnh hưởng.

## 2. Bộ component

| Lựa chọn | Đánh giá |
| --- | --- |
| **Ant Design** | Bộ component doanh nghiệp đầy đủ (Table, Form, Steps, DatePicker), có sẵn tiếng Việt (`vi_VN`), phong cách nhất quán |
| MUI | Theo Material Design của Google, mạnh nhưng mang đậm "chất Google" |
| shadcn/ui + Tailwind | Chép component vào dự án, toàn quyền tùy biến, giao diện hiện đại; phải tự lắp ghép nhiều hơn |
| PrimeReact / PrimeNG | Nhiều component, phổ biến với Angular |

**Quyết định: Ant Design**, vì có sẵn đủ các thành phần màn hình ngân hàng cần.

## 3. Quản lý dữ liệu từ server

| Lựa chọn | Bản chất | Đánh giá |
| --- | --- | --- |
| **TanStack Query** | Cache dữ liệu server theo key: gộp các request trùng, tự làm mới, retry, phân trang và cuộn vô hạn | Giao diện ngân hàng chủ yếu là dữ liệu server (số dư, lịch sử), rất hợp |
| Redux Toolkit + RTK Query | Store toàn cục + data fetching | Hợp khi có nhiều state phía client phức tạp; nhiều boilerplate hơn |
| SWR | Tương tự TanStack Query, nhẹ hơn | Ít tính năng hơn |
| `useEffect` + `fetch` | Tự viết | Dễ dính race condition, không có cache, phải tự xử lý loading và lỗi |

**Cấu hình quan trọng:** **không tự retry các thao tác ghi** (mutation). Retry chuyển tiền phải do
người dùng chủ động và gửi kèm cùng `Idempotency-Key`.

## 4. Công cụ build

| Lựa chọn | Đánh giá |
| --- | --- |
| **Vite** | Dev server dựa trên ES module nên khởi động tức thì; Vite 8 build bằng Rolldown (viết bằng Rust) |
| webpack | Trưởng thành, cấu hình được mọi thứ, chậm hơn |
| Create React App | Đã ngừng phát triển (2025), không dùng |
| Next.js | Render phía server (SSR, React Server Components), cần cho trang công khai cần SEO. Internet banking nằm sau đăng nhập, không cần SEO, nên SSR chỉ thêm một server phải vận hành |

## 5. HTTP client và lint

- **axios hay `fetch`:** axios có interceptor (gắn `X-Request-Id`, chuẩn hóa lỗi Problem Details),
  timeout và hủy request dễ dàng. `fetch` có sẵn trong trình duyệt nhưng phải tự viết các phần đó.
- **Oxlint hay ESLint:** Oxlint viết bằng Rust, nhanh hơn rất nhiều, là mặc định của template Vite
  hiện tại; ESLint có hệ sinh thái plugin lớn hơn.

## 6. Bảo mật và đúng đắn ở giao diện ngân hàng

- **Số tiền là chuỗi, không phải số thực.** `0.1 + 0.2 !== 0.3` trong JavaScript. Backend trả số tiền
  dạng chuỗi; giao diện chỉ định dạng để hiển thị, không tính toán tiền.
- **Chống bấm hai lần:** khóa nút khi đang gửi. Nếu vẫn gửi trùng thì `Idempotency-Key` ở backend chặn
  lại.
- **XSS:** React tự escape dữ liệu; không dùng `dangerouslySetInnerHTML`; cấu hình Content Security
  Policy.
- **Token:** giữ trong bộ nhớ, không dùng `localStorage` (xem [05](05-bao-mat.md)).
- Tự đăng xuất khi không hoạt động; hiển thị tên người nhận để người dùng xác nhận trước khi chuyển
  tiền.

## Câu hỏi phỏng vấn

**Vì sao số tiền phải là chuỗi?**
`Number` của JavaScript là số thực dấu phẩy động 64 bit: có sai số làm tròn với số thập phân, và chỉ
biểu diễn chính xác số nguyên đến 2^53.

**CSR khác SSR thế nào? Internet banking nên dùng gì?**
CSR render trên trình duyệt; SSR render trên server rồi gửi HTML. Internet banking nằm sau đăng nhập,
không cần SEO, nên CSR là đủ và đơn giản hơn.

**Làm sao để người dùng bấm "Xác nhận" hai lần không bị chuyển tiền hai lần?**
Khóa nút ở giao diện, và quan trọng hơn là gửi cùng một `Idempotency-Key` cho cùng một ý định chuyển
tiền để backend chặn bản trùng.
