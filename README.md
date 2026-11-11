# WebBank

Hệ thống ngân hàng số viết bằng **Java 21 + Spring Boot 4** và **React 19 + TypeScript**.
Dự án tập trung vào các bài toán khó của nghiệp vụ ngân hàng thay vì CRUD: tiền phải luôn đúng
(sổ cái kép), chống trừ tiền hai lần khi có request đồng thời, idempotency, giao dịch phân tán,
bảo mật và khả năng quan sát hệ thống.

Kế hoạch và lý do chọn công nghệ: [docs/ROADMAP.md](docs/ROADMAP.md)

## Cấu trúc

```
webbank/
├── backend/       Spring Boot 4.1 · Java 21 · Maven · PostgreSQL · Flyway
├── frontend/      React 19 · Vite 8 · TypeScript · Ant Design 6 · TanStack Query
├── docs/          Kế hoạch, quyết định kiến trúc (ADR)
└── compose.yaml   Hạ tầng local (PostgreSQL; sau này thêm Kafka, Redis, Keycloak...)
```

## Chạy local

Cần JDK 21, Node.js 22+ và Docker.

```bash
docker compose up -d                        # PostgreSQL ở cổng 5433
cd backend && ./mvnw spring-boot:run        # API ở cổng 8081
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Muốn chạy backend mà không cần compose thì dùng `./mvnw spring-boot:test-run`: Testcontainers sẽ
tự bật một PostgreSQL tạm.

| Thành phần   | Địa chỉ                                    |
| ------------ | ------------------------------------------ |
| Frontend     | http://localhost:5173                      |
| API          | http://localhost:8081/api/v1               |
| Swagger UI   | http://localhost:8081/swagger-ui.html      |
| Health check | http://localhost:8081/actuator/health      |
| PostgreSQL   | `localhost:5433`, db/user/pass: `webbank`  |

Ở môi trường dev, frontend gọi `/api/...` qua proxy của Vite nên không cần cấu hình CORS.

> **Lưu ý:** đường dẫn tới thư mục dự án không được chứa ký tự `#`. Vite dev server hiểu `#` là phần
> fragment của URL nên không phục vụ được module nào, và trình duyệt chỉ hiện trang trắng. Backend và
> `npm run build` không bị ảnh hưởng.

## Kiểm thử

```bash
cd backend && ./mvnw verify                    # cần Docker để chạy Testcontainers
cd frontend && npm run build && npm run lint
```
