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
```

`WALLET_INTERNAL_TOKEN` protects internal wallet-service endpoints such as
`POST /internal/wallets/credit`. Set the same value in wallet-service runtime
configuration and in trusted service-to-service clients. For local Postman
testing, set the collection variable `internal_token` to the same value.

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
