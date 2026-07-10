# GPay Wallet System

GPay Wallet System is a Java 21 Spring Boot monorepo for a wallet-style backend.
It models user authentication, wallet balances, wallet-to-wallet transfer,
top-up through a mock payment gateway, idempotency, payment rate limiting,
gateway webhooks, audit/activity logs, and trace id propagation.

The repository is intentionally kept as a monorepo for local development and
review. Service ownership is still separated: each service owns its own code,
database, Flyway migrations, and API boundary.

## Services

| Service | Path | Database | Host URL | Responsibility |
| --- | --- | --- | --- | --- |
| Auth Service | `services/auth-service` | `auth_db` | `http://localhost:8081` | User registration, login, refresh tokens, current user |
| Wallet Service | `services/wallet-service` | `wallet_db` | `http://localhost:8082` | Balances, mutations, transfers, internal wallet credit |
| Payment Service | `services/payment-service` | `payment_db` | `http://localhost:8083` | Top-up lifecycle, gateway webhook handling, rate limiting, bounded outbox retry (events move to `FAILED` after `PAYMENT_OUTBOX_MAX_ATTEMPTS`, once older than `PAYMENT_OUTBOX_MAX_AGE_MS`, or on a non-retryable 4xx) |
| Mock Gateway Service | `services/mock-gateway-service` | none | `http://localhost:8084` | Local gateway simulation for `SUCCESS`, `FAILED`, and `TIMEOUT` modes |
| Notification Service | `services/notification-service` | `notification_db` | `http://localhost:8085` | Consumes transfer/top-up result events from Kafka and sends transactional emails via SMTP (Mailpit locally), with idempotent per-event delivery attempts |

The repository also contains a shared `common` library module (`services/common`)
that holds cross-cutting infrastructure (currently trace ID handling) consumed by
the services through the root Gradle multi-module build. It is a library, not a
runnable service, and does not own a database.

Service-to-service communication uses HTTP APIs. Services must not query another
service database directly.

## Implemented Endpoints

Auth Service:

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
GET  /auth/me
```

Wallet Service:

```text
GET  /wallets/balance
GET  /wallets/mutations?page=0&size=20
POST /wallets/transfer
POST /internal/wallets/provision
```

Payment Service:

```text
POST /payments/top-up
POST /payments/webhook/gateway
```

Mock Gateway Service:

```text
POST /mock-gateway/top-up
```

The OpenAPI contract is in `openapi.yml`. The Postman collection is in
`postman/GPay.postman_collection.json`.

## Important Rules

- Money is represented as whole IDR.
- Money uses `BIGINT` in PostgreSQL and `Long` in Java.
- Public API JSON fields use `snake_case`.
- Mutating money endpoints require `Idempotency-Key`.
- Wallet provisioning on registration is best-effort: registration succeeds even when Wallet Service is unavailable, and a zero-balance wallet is provisioned on first wallet access.
- Secrets must come from environment variables.
- Flyway owns database schema migrations.
- Hibernate runs with `ddl-auto: validate`.

## Prerequisites

- Java 21
- Docker and Docker Compose
- Git

No Maven setup is required. Each service uses its own Gradle Wrapper.

## Local Startup With Docker Compose

Copy the example environment file, then replace placeholder secrets before using
the system beyond local testing:

```bash
cp .env.example .env
```

Start every service and dependency:

```bash
docker compose up --build
```

Run in the background:

```bash
docker compose up --build -d
```

Stop the stack:

```bash
docker compose down
```

PostgreSQL and Redis are exposed for local inspection:

```text
postgres -> localhost:5432
redis    -> localhost:6379
```

Application services are exposed on:

```text
auth-service         -> http://localhost:8081
wallet-service       -> http://localhost:8082
payment-service      -> http://localhost:8083
mock-gateway-service -> http://localhost:8084
notification-service -> http://localhost:8085
mailpit (web UI)     -> http://localhost:8025
```

Inside Docker Compose, services use Docker network names:

```text
payment-service -> http://mock-gateway-service:8084/mock-gateway/top-up
auth-service -> http://wallet-service:8082/internal/wallets/provision
mock-gateway-service -> http://payment-service:8083/payments/webhook/gateway
notification-service -> http://auth-service:8081/internal/users/{id}
notification-service -> mailpit:1025 (SMTP)
payment-service -> redis:6379
```

## Database Ownership

One PostgreSQL container is used for local development. The init script creates
one database per owning service:

```text
auth_db         -> auth-service only
wallet_db       -> wallet-service only
payment_db      -> payment-service only
notification_db -> notification-service only
```

Each service has its own Flyway migration folder under:

```text
services/<service-name>/src/main/resources/db/migration
```

## Environment Variables

`.env.example` contains local placeholder values. `.env` is ignored by Git and
must not be committed.

Core infrastructure:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
REDIS_HOST
REDIS_PORT
```

Auth Service:

```text
AUTH_DB_USERNAME
AUTH_DB_PASSWORD
AUTH_INTERNAL_TOKEN
JWT_SECRET
JWT_ACCESS_TOKEN_EXPIRATION_MINUTES
JWT_REFRESH_TOKEN_EXPIRATION_DAYS
AUTH_WALLET_INTERNAL_TOKEN
AUTH_WALLET_PROVISION_TIMEOUT_MS
```

Wallet Service:

```text
WALLET_DB_USERNAME
WALLET_DB_PASSWORD
JWT_SECRET
KAFKA_BOOTSTRAP_SERVERS
WALLET_INTERNAL_TOKEN
MAX_DAILY_TRANSFER_AMOUNT
WALLET_OUTBOX_RETRY_DELAY_MS
WALLET_OUTBOX_MAX_ATTEMPTS
WALLET_OUTBOX_MAX_AGE_MS
WALLET_OUTBOX_PROCESSING_TIMEOUT_MS
WALLET_OUTBOX_BATCH_SIZE
WALLET_OUTBOX_WORKER_FIXED_DELAY_MS
WALLET_OUTBOX_WORKER_INITIAL_DELAY_MS
```

Payment Service:

```text
PAYMENT_DB_USERNAME
PAYMENT_DB_PASSWORD
REDIS_HOST
REDIS_PORT
KAFKA_BOOTSTRAP_SERVERS
JWT_SECRET
PAYMENT_TOPUP_RATE_LIMIT_PER_MINUTE
PAYMENT_GATEWAY_TIMEOUT_MS
PAYMENT_GATEWAY_WEBHOOK_SECRET
PAYMENT_OUTBOX_RETRY_DELAY_MS
PAYMENT_OUTBOX_MAX_ATTEMPTS
PAYMENT_OUTBOX_MAX_AGE_MS
PAYMENT_OUTBOX_PROCESSING_TIMEOUT_MS
PAYMENT_OUTBOX_BATCH_SIZE
PAYMENT_OUTBOX_WORKER_FIXED_DELAY_MS
PAYMENT_OUTBOX_WORKER_INITIAL_DELAY_MS
```

Mock Gateway Service:

```text
GATEWAY_WEBHOOK_SECRET
MOCK_GATEWAY_TIMEOUT_DELAY_MS
```

Notification Service:

```text
KAFKA_BOOTSTRAP_SERVERS
NOTIFICATION_AUTH_INTERNAL_TOKEN
NOTIFICATION_AUTH_TIMEOUT_MS
NOTIFICATION_DB_PASSWORD
NOTIFICATION_DB_USERNAME
NOTIFICATION_MAIL_FROM_ADDRESS
NOTIFICATION_MAIL_HOST
NOTIFICATION_MAIL_PORT
```

Compose wires these service URLs directly:

```text
PAYMENT_GATEWAY_TOP_UP_URL=http://mock-gateway-service:8084/mock-gateway/top-up
AUTH_WALLET_PROVISION_URL=http://wallet-service:8082/internal/wallets/provision
PAYMENT_WEBHOOK_URL=http://payment-service:8083/payments/webhook/gateway
NOTIFICATION_AUTH_USER_LOOKUP_URL=http://auth-service:8081/internal/users
```

The payment-to-wallet top-up credit is delivered asynchronously over Kafka
(`wallet.credit.commands`), so both services need `KAFKA_BOOTSTRAP_SERVERS`.
Transfer and top-up result notifications are delivered the same way over
`wallet.transfer.*` and `payment.topup.*` to the notification service, which
sends emails viewable in Mailpit at `http://localhost:8025`. See
`docs/event-contracts.md` for the event contracts and delivery design.

Secret alignment required for local workflows:

- `JWT_SECRET` must be the same for Auth, Wallet, and Payment services.
- `WALLET_INTERNAL_TOKEN` must match `AUTH_WALLET_INTERNAL_TOKEN`.
- `GATEWAY_WEBHOOK_SECRET` must match `PAYMENT_GATEWAY_WEBHOOK_SECRET`.

## Running A Single Service From The Host

The repository is a single root Gradle multi-module build, so run Gradle from the
repository root and target a module with `:<service>:<task>`.

Start PostgreSQL, Redis, and Kafka first:

```bash
docker compose up -d postgres redis kafka
```

Auth Service:

```bash
AUTH_WALLET_PROVISION_URL=http://localhost:8082/internal/wallets/provision \
AUTH_WALLET_INTERNAL_TOKEN=change-this-wallet-internal-token \
AUTH_WALLET_PROVISION_TIMEOUT_MS=5000 \
./gradlew :auth-service:bootRun
```

Wallet Service:

```bash
WALLET_INTERNAL_TOKEN=change-this-wallet-internal-token \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
./gradlew :wallet-service:bootRun
```

Payment Service:

```bash
PAYMENT_GATEWAY_TOP_UP_URL=http://localhost:8084/mock-gateway/top-up \
PAYMENT_GATEWAY_TIMEOUT_MS=5000 \
PAYMENT_GATEWAY_WEBHOOK_SECRET=change-this-gateway-secret \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
PAYMENT_OUTBOX_RETRY_DELAY_MS=60000 \
PAYMENT_OUTBOX_MAX_ATTEMPTS=5 \
PAYMENT_OUTBOX_MAX_AGE_MS=86400000 \
PAYMENT_OUTBOX_PROCESSING_TIMEOUT_MS=300000 \
PAYMENT_OUTBOX_BATCH_SIZE=10 \
PAYMENT_OUTBOX_WORKER_FIXED_DELAY_MS=5000 \
PAYMENT_OUTBOX_WORKER_INITIAL_DELAY_MS=5000 \
./gradlew :payment-service:bootRun
```

Mock Gateway Service:

```bash
PAYMENT_WEBHOOK_URL=http://localhost:8083/payments/webhook/gateway \
GATEWAY_WEBHOOK_SECRET=change-this-gateway-secret \
MOCK_GATEWAY_TIMEOUT_DELAY_MS=6000 \
./gradlew :mock-gateway-service:bootRun
```

## Running Tests

Run tests from the repository root. Target a single module:

```bash
./gradlew :auth-service:test
./gradlew :wallet-service:test
./gradlew :payment-service:test
./gradlew :mock-gateway-service:test
./gradlew :common:test
```

Or run the whole build (all modules) at once:

```bash
./gradlew test
```

### Test Strategy

Each module ships two layers of automated tests:

- **Unit tests** with JUnit 5 and Mockito, mocking only the boundaries outside
  the unit under test (services, clients, signature helpers).
- **Integration tests** with Spring Boot (`@SpringBootTest` + `MockMvc`) that
  drive the real controller -> service -> repository (or webhook client) flow.

The database-owning services run their integration tests against a real
PostgreSQL container started with Testcontainers, with Flyway migrations applied
automatically — no manually running Docker Compose is required. Payment Service
additionally covers Redis-backed top-up rate limiting. Mock Gateway owns no
database, so its integration tests drive the controller -> service ->
`PaymentWebhookClient` flow against a local stub HTTP server, verifying the
webhook payload shape, HMAC-SHA256 signature, and `X-Trace-Id` propagation.

Integration coverage by service:

```text
auth-service         register, login, refresh, me
wallet-service       balance, mutations, transfer, internal credit, internal provision
payment-service      top-up, gateway webhook, rate limiting   (PostgreSQL + Redis)
mock-gateway-service top-up across SUCCESS, FAILED, and TIMEOUT modes
```

### Coverage Gates

JaCoCo branch coverage is reported per module and enforced by the build
(`jacocoTestCoverageVerification` runs as part of `build`). A module fails its
build when branch coverage falls below its threshold:

```text
auth-service           95%
wallet-service         95%
payment-service        95%
mock-gateway-service   95%
common (shared lib)    60%
```

## Postman Usage

Import:

```text
postman/GPay.postman_collection.json
```

Default collection variables target the Docker Compose host ports:

```text
auth_url=http://localhost:8081
wallet_url=http://localhost:8082
payment_url=http://localhost:8083
gateway_url=http://localhost:8084
```

For the full manual verification path, use the collection folder named
`E2E Flow` and follow `docs/postman-e2e-flow.md`. It covers:

1. Register user A and user B.
2. Login user A.
3. Top-up user A with `SUCCESS`.
4. Check balance, transfer to user B, and verify mutations.
5. Retry the transfer with the same `Idempotency-Key`.
6. Verify insufficient balance, failed top-up, timeout top-up, invalid webhook signature, and rate limiting.

For ad hoc manual workflows:

1. Run `POST /auth/register`.
2. Run `POST /auth/login`; the collection stores `access_token` and `refresh_token`.
3. Use wallet and payment requests with `Authorization: Bearer {{access_token}}`.
4. Set `receiver_wallet_id`, `credit_wallet_id`, `payment_transaction_id`, and `topup_wallet_id` as needed.
5. Set `internal_token` to the same value as `WALLET_INTERNAL_TOKEN`.
6. Set `gateway_webhook_secret` to the same value as `PAYMENT_GATEWAY_WEBHOOK_SECRET`.

The collection automatically adds `X-Trace-Id` when a request does not define
one. Empty idempotency key variables are initialized with generated local values;
clear a key variable to generate a new one, or keep it to replay the same
request.

Webhook requests calculate `gateway_timestamp` and `gateway_signature` before
sending. Do not store real secrets in the collection.

## Documentation

Project behavior and decisions are documented in:

- `docs/PRD.md`
- `docs/architecture.md`
- `docs/failure-scenarios.md`
- `docs/trade-offs.md`
- `docs/future-development.md`
- `docs/postman-e2e-flow.md`

API details are documented in:

- `openapi.yml`
- `postman/GPay.postman_collection.json`
