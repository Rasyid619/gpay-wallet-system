# GPay Wallet System

GPay Wallet System is a Java 21 Spring Boot monorepo for a GPay-style wallet
application. The target architecture has separate services for auth, wallet,
payment, and a mock payment gateway, with each service owning its own database.

This README reflects the current repository state.

## Project Description

This project is a backend technical assessment that models a wallet system with
microservice boundaries. The system is designed around secure authentication,
safe wallet balance handling, durable idempotency, traceable activity logs, and
payment top-up processing through a mock external gateway.

The repository uses a monorepo layout to keep service code, database migrations,
local infrastructure, documentation, and Postman requests easy to review. Each
service is intended to own its own PostgreSQL database and communicate with
other services only through HTTP APIs.

Current implementation is focused on the foundation, Auth Service, and Wallet
Service persistence model. Payment and mock gateway workflows are documented as
target scope but have not been implemented yet.

## What Has Been Done

### Project foundation

- Monorepo structure is in place.
- Product and engineering documentation exists in `docs/`.
- Java conventions are documented in `CODEX.java.md`.
- Docker Compose is available for local PostgreSQL and Redis.
- PostgreSQL initialization creates the service databases:
  - `auth_db`
  - `wallet_db`
  - `payment_db`
- Postman collection exists at `postman/GPay.postman_collection.json`.

### Auth Service

Location:

```text
services/auth-service
```

Implemented:

- Spring Boot 3 service using Java 21 and Gradle Wrapper.
- Auth endpoints:
  - `POST /auth/register`
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `GET /auth/me`
- PostgreSQL persistence with Spring Data JPA.
- Flyway migration for:
  - `users`
  - `refresh_tokens`
  - `user_role` enum
- BCrypt password hashing.
- JWT access token generation.
- Refresh token generation, hashing, expiry, and revocation on refresh.
- Spring Security JWT filter.
- DTO-based API requests and responses.
- Global exception handling for validation, conflict, not found, and unauthorized cases.
- Unit tests for register, login, refresh token, and current-user behavior.

Default port:

```text
8081
```

### Wallet Service

Location:

```text
services/wallet-service
```

Implemented:

- Spring Boot 3 service using Java 21 and Gradle Wrapper.
- PostgreSQL persistence model entities for:
  - wallets
  - transfers
  - ledger entries
  - idempotency keys
  - activity logs
- Flyway migrations for wallet-owned tables and indexes.
- Money is stored as whole IDR using `BIGINT` in the database and `Long` in Java.
- Wallet schema includes non-negative balance checks, positive amount checks, transfer wallet distinctness checks, and source reference checks.
- `updated_at` trigger for wallet rows.

Default port:

```text
8082
```

### Infrastructure

Implemented:

- One PostgreSQL 16 Alpine container for local development.
- One Redis 7 Alpine container for local development.
- Init script for creating one database per service.

Docker services:

```text
postgres -> localhost:5432
redis    -> localhost:6379
```

## Not Yet Implemented

The following items are part of the target scope but are not fully implemented in the current codebase:

- Wallet controllers, services, repositories, JWT validation, transfer workflow, mutation history, and internal credit endpoint.
- Payment Service implementation.
- Mock Gateway Service implementation.
- Payment top-up rate limiting with Redis.
- Gateway webhook HMAC validation.
- Payment outbox and retry worker.
- Trace ID filter and WebClient trace propagation.
- Wallet transfer concurrency tests.
- Payment webhook, rate limit, and outbox tests.

## Repository Layout

```text
gpay-wallet-system/
├── docker-compose.yml
├── README.md
├── CODEX.java.md
├── docs/
├── infrastructure/
│   └── postgres/
├── postman/
└── services/
    ├── auth-service/
    └── wallet-service/
```

Target services that still need to be added:

```text
services/payment-service
services/mock-gateway-service
```

## Local Setup

Prerequisites:

- Java 21
- Docker and Docker Compose

Start local dependencies:

```bash
docker compose up -d postgres redis
```

The current development defaults are defined in each service's `application.yml`.
For production-like runs, provide secrets and database credentials through
environment variables.

Important environment variables:

```text
AUTH_DB_URL
AUTH_DB_USERNAME
AUTH_DB_PASSWORD
JWT_SECRET
WALLET_DB_PASSWORD
WALLET_DB_URL
WALLET_DB_USERNAME
WALLET_INTERNAL_TOKEN
PAYMENT_GATEWAY_TOP_UP_URL
PAYMENT_GATEWAY_TIMEOUT_MS
PAYMENT_GATEWAY_WEBHOOK_SECRET
PAYMENT_WALLET_CREDIT_URL
PAYMENT_WALLET_INTERNAL_TOKEN
PAYMENT_OUTBOX_REQUEST_TIMEOUT_MS
PAYMENT_OUTBOX_RETRY_DELAY_MS
PAYMENT_OUTBOX_BATCH_SIZE
PAYMENT_OUTBOX_WORKER_FIXED_DELAY_MS
PAYMENT_OUTBOX_WORKER_INITIAL_DELAY_MS
PAYMENT_WEBHOOK_URL
GATEWAY_WEBHOOK_SECRET
MOCK_GATEWAY_TIMEOUT_DELAY_MS
```

`WALLET_INTERNAL_TOKEN` protects internal wallet-service endpoints such as
`POST /internal/wallets/credit`. Set the same value in wallet-service runtime
configuration and in trusted service-to-service clients. For local Postman
testing, set the collection variable `internal_token` to the same value.

`GATEWAY_WEBHOOK_SECRET` signs mock-gateway callbacks to Payment Service using
`HMAC_SHA256(secret, timestamp + "." + rawRequestBody)`. `PAYMENT_WEBHOOK_URL`
must be configured before using Mock Gateway `SUCCESS` or `FAILED` mode.
`MOCK_GATEWAY_TIMEOUT_DELAY_MS` controls the `TIMEOUT` mode delay.
`PAYMENT_GATEWAY_TOP_UP_URL` should point to Mock Gateway
`/mock-gateway/top-up`, and `PAYMENT_GATEWAY_TIMEOUT_MS` should be `5000` for
the required 5-second payment gateway timeout.
`PAYMENT_GATEWAY_WEBHOOK_SECRET` must match Mock Gateway's
`GATEWAY_WEBHOOK_SECRET` so Payment Service can validate gateway callbacks.
Successful gateway callbacks create one pending wallet-credit outbox event in
`payment_db`; duplicate successful callbacks do not create duplicate credit work.
`PAYMENT_WALLET_CREDIT_URL` should point to Wallet Service
`/internal/wallets/credit`, and `PAYMENT_WALLET_INTERNAL_TOKEN` must match
Wallet Service's `WALLET_INTERNAL_TOKEN`. The payment outbox worker sends this
token with each wallet-credit delivery and uses durable payment outbox event IDs
for wallet idempotency.

## Running Services

Auth Service:

```bash
cd services/auth-service
./gradlew bootRun
```

Wallet Service:

```bash
cd services/wallet-service
WALLET_INTERNAL_TOKEN=dev-internal-token-change-me ./gradlew bootRun
```

Payment Service:

```bash
cd services/payment-service
PAYMENT_GATEWAY_TOP_UP_URL=http://localhost:8084/mock-gateway/top-up \
PAYMENT_GATEWAY_TIMEOUT_MS=5000 \
PAYMENT_GATEWAY_WEBHOOK_SECRET=dev-gateway-webhook-secret-change-me \
PAYMENT_WALLET_CREDIT_URL=http://localhost:8082/internal/wallets/credit \
PAYMENT_WALLET_INTERNAL_TOKEN=dev-internal-token-change-me \
PAYMENT_OUTBOX_REQUEST_TIMEOUT_MS=5000 \
PAYMENT_OUTBOX_RETRY_DELAY_MS=60000 \
PAYMENT_OUTBOX_BATCH_SIZE=10 \
PAYMENT_OUTBOX_WORKER_FIXED_DELAY_MS=5000 \
PAYMENT_OUTBOX_WORKER_INITIAL_DELAY_MS=5000 \
./gradlew bootRun
```

Mock Gateway Service:

```bash
cd services/mock-gateway-service
PAYMENT_WEBHOOK_URL=http://localhost:8083/payments/webhook/gateway \
GATEWAY_WEBHOOK_SECRET=dev-gateway-webhook-secret-change-me \
MOCK_GATEWAY_TIMEOUT_DELAY_MS=6000 \
./gradlew bootRun
```

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

For larger changes:

```bash
./gradlew clean test
```

## Documentation

Project behavior and decisions are documented in:

- `docs/PRD.md`
- `docs/architecture.md`
- `docs/failure-scenarios.md`
- `docs/trade-offs.md`

## API Notes

- Public API JSON fields should use `snake_case`.
- Money amounts are whole IDR integers.
- Secrets must come from environment variables.
- Services must not query another service database directly.
- Cross-service calls should use HTTP APIs through Spring WebClient.
