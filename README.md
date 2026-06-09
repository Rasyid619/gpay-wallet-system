# GPay Wallet System

GPay Wallet System is a Java 21 Spring Boot monorepo for a GPay-style wallet
backend. The system is split into service-owned modules for authentication,
wallet operations, payment top-up, and a mock payment gateway.

Each service owns its own data boundary. Services communicate through HTTP APIs
and do not query another service database directly.

## Current Status

This README reflects the repository state on June 9, 2026.

Implemented services:

- Auth Service on port `8081`
- Wallet Service on port `8082`
- Payment Service on port `8083`
- Mock Gateway Service foundation on port `8084`

Implemented infrastructure:

- PostgreSQL 16 for local service databases
- Redis 7 for payment top-up rate limiting
- Flyway migrations per database-owning service
- OpenAPI contract in `openapi.yml`
- Postman collection in `postman/GPay.postman_collection.json`

## What Has Been Done

### Project Foundation

- Monorepo structure under `services/`.
- Product, architecture, failure scenario, and trade-off docs under `docs/`.
- Java engineering guidance in `CODEX.java.md`.
- Docker Compose for PostgreSQL and Redis.
- One PostgreSQL container with one database per service:
  - `auth_db`
  - `wallet_db`
  - `payment_db`
- OpenAPI and Postman are updated as endpoints are implemented.

### Auth Service

Location:

```text
services/auth-service
```

Implemented:

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /auth/me`
- BCrypt password hashing.
- JWT access token support.
- Refresh token hashing, expiry, and rotation.
- PostgreSQL persistence with Flyway migrations.
- DTO-based API responses.
- Spring Security JWT filter.
- Tests for core auth behavior.

### Wallet Service

Location:

```text
services/wallet-service
```

Implemented:

- `GET /wallets/balance`
- `GET /wallets/mutations`
- `POST /wallets/transfer`
- `POST /internal/wallets/credit`
- PostgreSQL-owned wallet schema with Flyway migrations.
- Wallet balances, transfers, ledger entries, idempotency keys, and activity logs.
- Money stored as whole IDR using `Long` in Java and `BIGINT` in PostgreSQL.
- Row-level locking for balance-changing workflows.
- Durable idempotency for money-moving requests.
- Internal token protection for internal wallet credit.
- Tests for balance, mutation history, transfer, and internal credit behavior.

### Payment Service

Location:

```text
services/payment-service
```

Implemented:

- Payment service foundation with `payment_db`.
- Flyway migration for payment-owned tables:
  - `topup_transactions`
  - `idempotency_keys`
  - `outbox_events`
  - `activity_logs`
- `POST /payments/top-up`
- JWT protection for top-up.
- Durable PostgreSQL idempotency for top-up requests.
- Redis rate limit for top-up: 5 requests per minute per user by default.
- Fail-closed behavior when Redis cannot verify the rate limit.
- OpenAPI and Postman coverage for payment top-up and rate limit cases.
- Tests for top-up creation, idempotency, and rate limiting.

Current limitation:

- Payment Service creates the top-up transaction, but the actual call to Mock
  Gateway is still planned work.

### Mock Gateway Service

Location:

```text
services/mock-gateway-service
```

Implemented:

- Standalone Spring Boot service foundation.
- `POST /mock-gateway/top-up`
- Supported simulation modes:
  - `SUCCESS`
  - `FAILED`
  - `TIMEOUT`
- `TIMEOUT` mode delays the response. The default delay is `6000` ms.
- OpenAPI and Postman coverage for the mock gateway endpoint.
- Tests for valid modes and validation errors.

Current limitation:

- Webhook callback delivery and HMAC signing are planned for the next payment
  gateway issues.

## Working Flow

This is the development flow being used for each GitHub issue:

1. Start from `main`.
2. Pull the latest base with `git pull --ff-only`.
3. Read the issue, required docs, and relevant service code.
4. Create a scoped branch named `rasyid-[issue-number]-[short-kebab-title]`.
5. Implement only the current issue.
6. Keep controllers thin and put business logic in services.
7. Update OpenAPI and Postman when endpoint behavior changes.
8. Run relevant Gradle tests from the changed service directory.
9. Commit with a Conventional Commit message.
10. Push the branch.
11. Create a PR linked to the issue and assign it to `Rasyid619`.

## How To Start Locally

Prerequisites:

- Java 21
- Docker
- Docker Compose

Start local infrastructure:

```bash
docker compose up -d postgres redis
```

## Environment Variables

The services have local defaults for development, but real runtime should set
the secrets and database credentials explicitly.

Shared:

```text
JWT_SECRET
```

Use the same `JWT_SECRET` for Auth, Wallet, and Payment services so Wallet and
Payment can validate access tokens issued by Auth.

Auth Service:

```text
AUTH_DB_URL
AUTH_DB_USERNAME
AUTH_DB_PASSWORD
JWT_ACCESS_TOKEN_EXPIRATION_MINUTES
JWT_REFRESH_TOKEN_EXPIRATION_DAYS
```

Wallet Service:

```text
WALLET_DB_URL
WALLET_DB_USERNAME
WALLET_DB_PASSWORD
WALLET_INTERNAL_TOKEN
MAX_DAILY_TRANSFER_AMOUNT
```

`WALLET_INTERNAL_TOKEN` is required for `POST /internal/wallets/credit`. Keep it
non-empty and set Postman's `internal_token` variable to the same value when
testing the internal credit endpoint.

Payment Service:

```text
PAYMENT_DB_URL
PAYMENT_DB_USERNAME
PAYMENT_DB_PASSWORD
REDIS_HOST
REDIS_PORT
PAYMENT_TOPUP_RATE_LIMIT_PER_MINUTE
```

Mock Gateway Service:

```text
MOCK_GATEWAY_TIMEOUT_DELAY_MS
```

Local development defaults:

```text
AUTH_DB_URL=jdbc:postgresql://localhost:5432/auth_db
AUTH_DB_USERNAME=gpay
AUTH_DB_PASSWORD=gpay
WALLET_DB_URL=jdbc:postgresql://localhost:5432/wallet_db
WALLET_DB_USERNAME=gpay
WALLET_DB_PASSWORD=gpay
PAYMENT_DB_URL=jdbc:postgresql://localhost:5432/payment_db
PAYMENT_DB_USERNAME=gpay
PAYMENT_DB_PASSWORD=gpay
REDIS_HOST=localhost
REDIS_PORT=6379
PAYMENT_TOPUP_RATE_LIMIT_PER_MINUTE=5
MAX_DAILY_TRANSFER_AMOUNT=10000000
MOCK_GATEWAY_TIMEOUT_DELAY_MS=6000
```

Run each service in a separate terminal.

Auth Service:

```bash
cd services/auth-service
JWT_SECRET=change-this-secret-minimum-32-characters-long ./gradlew bootRun
```

Wallet Service:

```bash
cd services/wallet-service
JWT_SECRET=change-this-secret-minimum-32-characters-long \
WALLET_INTERNAL_TOKEN=dev-internal-token-change-me \
./gradlew bootRun
```

Payment Service:

```bash
cd services/payment-service
JWT_SECRET=change-this-secret-minimum-32-characters-long ./gradlew bootRun
```

Mock Gateway Service:

```bash
cd services/mock-gateway-service
./gradlew bootRun
```

Optional mock gateway timeout override:

```bash
cd services/mock-gateway-service
MOCK_GATEWAY_TIMEOUT_DELAY_MS=6000 ./gradlew bootRun
```

Local ports:

```text
Auth Service:         http://localhost:8081
Wallet Service:       http://localhost:8082
Payment Service:      http://localhost:8083
Mock Gateway Service: http://localhost:8084
PostgreSQL:           localhost:5432
Redis:                localhost:6379
```

## How To Use The Flow

Typical manual flow with Postman:

1. Register a user with `POST /auth/register`.
2. Login with `POST /auth/login`.
3. Postman saves `access_token` and `refresh_token` into collection variables.
4. Use `access_token` for Wallet and Payment requests.
5. Use `Idempotency-Key` for money-moving endpoints.
6. Set `topup_wallet_id` before calling payment or mock gateway top-up requests.
7. Set `internal_token` to match `WALLET_INTERNAL_TOKEN` before testing
   `POST /internal/wallets/credit`.

Current top-up behavior:

- `POST /payments/top-up` creates a `PENDING` payment transaction.
- Redis enforces the payment top-up rate limit.
- `POST /mock-gateway/top-up` can manually simulate `SUCCESS`, `FAILED`, or
  `TIMEOUT`.
- Payment Service is not yet wired to call Mock Gateway automatically.

## Running Tests

Run tests from the changed service directory.

Auth Service:

```bash
cd services/auth-service
./gradlew test
```

Wallet Service:

```bash
cd services/wallet-service
./gradlew test
```

Payment Service:

```bash
cd services/payment-service
./gradlew test
```

Mock Gateway Service:

```bash
cd services/mock-gateway-service
./gradlew test
```

For larger service changes:

```bash
./gradlew clean test
```

## Planned Work

### Today - June 9, 2026

Completed today:

- Payment top-up Redis rate limiting was added and verified.
- Mock Gateway Service foundation was added.
- Mock Gateway OpenAPI and Postman requests were added.
- README was updated to describe the current working flow and local startup.

### Tomorrow - June 10, 2026

Planned focus:

- Mock Gateway webhook callback with HMAC signature.
- Payment Service call to Mock Gateway through WebClient.
- Gateway timeout handling so Payment Service keeps timed-out top-ups as
  `PENDING`.
- `POST /payments/webhook/gateway` with HMAC validation.

### June 11, 2026

Planned focus:

- Payment outbox event creation on successful webhook.
- Payment outbox worker to credit Wallet Service.
- Trace ID propagation across service calls.
- Activity log coverage.
- Docker Compose updates for all application services.
- Final Postman and README cleanup after the full payment flow is connected.

## Documentation

Project behavior and decisions are documented in:

- `docs/PRD.md`
- `docs/architecture.md`
- `docs/failure-scenarios.md`
- `docs/trade-offs.md`
- `CODEX.java.md`

## API Rules

- Public API JSON fields use `snake_case`.
- Money amounts are whole IDR integers.
- Secrets come from environment variables.
- Services do not query another service database directly.
- Cross-service calls use HTTP APIs.
