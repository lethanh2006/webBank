# WebBank: digital banking backend in Java / Spring Boot

A small core-banking backend (accounts, double-entry ledger, internal transfers) built to show how
money stays correct under concurrent requests and client retries. It is not a CRUD demo: the
interesting parts are the ledger, the locking strategy and idempotent transfers, each backed by tests
against a real PostgreSQL.

- **Live demo:** https://webbank-lethanh.vercel.app (choose a demo customer at the top right).
  The API runs on a free plan that sleeps when idle; the first request can take about a minute.
- **API docs (Swagger UI):** https://webbank-lethanh-api.onrender.com/swagger-ui.html
- **Tech:** Java 21, Spring Boot 4.1 (Spring MVC, Spring Data JPA / Hibernate 7), PostgreSQL 17,
  Flyway, JUnit 6, Testcontainers, Spring Modulith, Docker, GitHub Actions; React + TypeScript UI.

## Backend highlights

| Problem | What the code does | Proof |
| --- | --- | --- |
| Lost history, rounding errors | Double-entry ledger: every money movement is a balanced journal entry of immutable postings; corrections are reversal entries. `Money` value object on `BigDecimal` with ISO 4217 scale; JSON amounts are strings | DB triggers reject UPDATE/DELETE on postings and reject unbalanced entries at COMMIT; invariant checks after every test |
| Two transfers spending the same balance | `SELECT … FOR UPDATE` on both accounts, always in ascending id order, with a 5 s `lock_timeout`; `CHECK (available_balance >= 0)` as last line of defence | 100 concurrent transfers of 10,000 from 500,000: exactly 50 succeed |
| Deadlocks (A→B while B→A) | Global lock order removes circular waits | 200 opposite-direction transfers, no deadlock (removing the ordering makes PostgreSQL report deadlocks) |
| Client retries after a timeout | `Idempotency-Key` header: key, request hash and response stored in the same transaction as the transfer; concurrent duplicates wait on the unique index and replay the stored response | 20 concurrent requests with one key move money once |
| Reading other customers' data (IDOR) | Ownership is part of every query; foreign accounts answer 404 | API tests per endpoint |
| Slow statements on large accounts | Keyset (cursor) pagination using the `(account_id, id DESC)` index instead of `OFFSET` | Paging test: no duplicates or gaps |

Other details: RFC 9457 problem details with stable error codes and a request id, Luhn check digit on
account numbers, `open-in-view` disabled, schema owned by Flyway (`ddl-auto=validate`), module
boundaries verified by Spring Modulith, JSON logs in production.

## Architecture

```
React SPA (Vercel) --/api (same-origin rewrite)--> Spring Boot API (Render, Docker) --> PostgreSQL (Neon)

com.webbank
├── customer     customers, current-customer resolution
├── ledger       accounts, journal entries, postings, balances, statement queries
├── transfer     internal transfers
├── idempotency  Idempotency-Key storage and replay
├── shared       Money, error model, request id, business calendar
└── demo         demo data and identity ("demo" profile only)
```

## REST API (v1)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/accounts` | Accounts of the current customer |
| GET | `/api/v1/accounts/{accountNo}` | One account (404 if not owned) |
| GET | `/api/v1/accounts/{accountNo}/transactions?cursor=&size=` | Statement, newest first |
| GET | `/api/v1/account-holders/{accountNo}` | Holder name shown before confirming a transfer |
| POST | `/api/v1/transfers` (header `Idempotency-Key`) | Internal transfer, 201 + `Location` |
| GET | `/api/v1/transfers/{reference}` | One transfer of the current customer |

Authentication is simplified for the demo: the caller is identified by the `X-Demo-Customer-Id`
header (enabled only by the `demo` profile).

## Run locally

Requires JDK 21, Node.js 22+ and Docker.

Start PostgreSQL, then run the backend and frontend in separate terminals:

```bash
docker compose up -d --wait postgres          # PostgreSQL on port 5433
```

```bash
cd backend
./mvnw spring-boot:run                        # API on port 8081, Swagger at /swagger-ui.html
```

```bash
cd frontend
npm ci
npm start                                   # build + preview at http://localhost:4173
```

The default local `dev` profile includes `demo`: the first start creates three customers and four
funded accounts through real ledger postings. The UI selects the first customer automatically.
Use the switcher to see another customer's account number, then return to the sender to transfer.
All money in this project is simulated.

`./mvnw spring-boot:test-run` starts the API with a throwaway PostgreSQL from Testcontainers instead.
For hot reload, use `npm run dev` at http://localhost:5173 from a path without `#`.
`npm start` and the browser tests serve the built app, so they also work in paths containing `#`.

## Tests

```bash
cd backend && ./mvnw verify    # unit tests (Surefire) + integration tests on PostgreSQL (Failsafe, needs Docker)
cd frontend && npm run lint && npm run build
```

For browser tests, keep the local API running on port 8081:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

There are **44 backend tests** (19 in Surefire, 25 in Failsafe) plus **9 browser tests**.
The browser suite verifies empty/error recovery, cold-start retries, customer switching, and a real
transfer followed by idempotent replay, balance checks and statement inspection. They also cover
pending submissions, failed background refreshes, restored retry keys and expired drafts. The real-API test
uses the local API and changes the local demo balances by 1,000 VND. Reviewed drafts are kept per
customer in session storage; ambiguous submissions retain their key for safe retries. CI runs on every push and pull
request, starts its own local API/PostgreSQL, and runs both suites.

If demo customers cannot load, the UI explains the state and offers **Thử lại**. A successful ping
alone does not establish that demo data is available. See [the CV verification and demo guide](docs/CV-VERIFICATION.md).

## Documentation (Vietnamese)

- [docs/tech/](docs/tech/README.md): why each technology was chosen over its alternatives
  (Kafka vs RabbitMQ, PostgreSQL vs Oracle, locking strategies, outbox, saga...).
- [docs/ROADMAP.md](docs/ROADMAP.md): technical roadmap and interview notes.
