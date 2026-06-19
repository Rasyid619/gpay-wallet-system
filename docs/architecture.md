# Architecture

## 1. Overview

GPay Wallet System is designed as a microservice-based wallet application inside a monorepo.

The system supports:

* User authentication
* Wallet balance
* Wallet mutation history
* Wallet-to-wallet transfer
* Top-up through mock payment gateway
* Gateway webhook callback
* HMAC validation
* Idempotency
* Daily transfer limit
* Payment rate limiting
* Audit/activity logs
* TraceId propagation

The architecture is intentionally kept simple and deadline-focused while still preserving clear service ownership.

---

## 2. Monorepo Structure

```txt
gpay-wallet-system/
├── docker-compose.yml
├── README.md
├── AGENTS.md
├── CODEX.java.md
├── docs/
│   ├── PRD.md
│   ├── architecture.md
│   ├── failure-scenarios.md
│   └── trade-offs.md
├── postman/
│   └── GPay.postman_collection.json
├── infrastructure/
│   └── postgres/
│       └── init-multiple-databases.sh
└── services/
    ├── auth-service/
    ├── wallet-service/
    ├── payment-service/
    └── mock-gateway-service/
```

---

## 3. Services

## 3.1 Auth Service

Auth Service manages user identity.

Responsibilities:

* Register user
* Login user
* Generate JWT access token
* Generate refresh token
* Refresh access token
* Hash password
* Store refresh token hash

Endpoints:

```txt
POST /auth/register
POST /auth/login
POST /auth/refresh
GET  /auth/me
```

Database:

```txt
auth_db
```

Tables:

```txt
users
refresh_tokens
```

---

## 3.2 Wallet Service

Wallet Service owns wallet balance and money movement.

Responsibilities:

* Check balance
* Show mutation history
* Transfer balance between wallets
* Validate sufficient balance
* Enforce daily transfer limit
* Create ledger entries
* Handle idempotency
* Handle internal wallet credit from Payment Service
* Create activity logs

Endpoints:

```txt
GET  /wallets/balance
GET  /wallets/mutations
POST /wallets/transfer
```

Wallet credit from Payment Service arrives over Kafka (consumed by
`WalletCreditCommandConsumer`), not as an HTTP endpoint.

Database:

```txt
wallet_db
```

Tables:

```txt
wallets
ledger_entries
transfers
idempotency_keys
activity_logs
```

---

## 3.3 Payment Service

Payment Service owns the top-up lifecycle.

Responsibilities:

* Receive top-up request
* Call Mock Gateway
* Handle gateway timeout
* Receive webhook callback
* Validate HMAC signature
* Update payment status
* Apply payment rate limit
* Store idempotency response
* Create outbox event for wallet credit
* Retry wallet credit through outbox worker
* Create activity logs

Endpoints:

```txt
POST /payments/top-up
POST /payments/webhook/gateway
GET  /payments/{id}
```

Database:

```txt
payment_db
```

Tables:

```txt
topup_transactions
idempotency_keys
outbox_events
activity_logs
```

---

## 3.4 Mock Gateway Service

Mock Gateway Service simulates an external payment gateway.

Responsibilities:

* Receive top-up request from Payment Service
* Simulate SUCCESS
* Simulate FAILED
* Simulate TIMEOUT
* Send webhook callback to Payment Service
* Sign webhook payload using HMAC

Endpoint:

```txt
POST /mock-gateway/top-up
```

---

## 4. Database Architecture

The project uses one PostgreSQL Docker container with one database per service.

Databases:

```txt
auth_db
wallet_db
payment_db
```

Each service connects only to its own database.

Rules:

* Auth Service only accesses `auth_db`
* Wallet Service only accesses `wallet_db`
* Payment Service only accesses `payment_db`
* Services must not query another service database directly
* Cross-service communication must happen through HTTP APIs

This keeps service ownership clear while keeping local Docker Compose setup simple.

---

## 5. Migration Architecture

Each service owns its own Flyway migration.

```txt
auth-service    -> auth_db migrations
wallet-service  -> wallet_db migrations
payment-service -> payment_db migrations
```

Hibernate must not create or update tables automatically.

Use:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Flyway creates the tables. Hibernate validates the entity mapping.

---

## 6. Money Representation

Money is stored as integer amount.

Database type:

```txt
BIGINT
```

Java type:

```txt
Long
```

All amount values represent whole IDR.

Example:

```txt
Rp10,000 -> 10000
```

The project does not use `double`, `float`, `Double`, or `Float` for money.

Reason:

* Avoid floating point precision issues
* Keep transfer and balance calculation simple
* IDR does not require decimal amount for this test scope

---

## 7. Service Communication

Services communicate using HTTP.

Communication map:

```txt
Client -> Auth Service
Client -> Wallet Service
Client -> Payment Service

Payment Service -> Mock Gateway Service
Mock Gateway Service -> Payment Service webhook
Payment Service -> Wallet Service internal credit
```

Service-to-service HTTP calls use Spring WebClient.

RabbitMQ is not used in v1.

---

## 8. Transfer Architecture

Wallet transfer is handled inside Wallet Service.

Reason:

Transfer must be atomic. Sender debit and receiver credit must happen in one local database transaction.

Transfer flow:

```txt
1. Validate JWT
2. Read sender userId from token
3. Validate request
4. Check Idempotency-Key
5. Load sender wallet
6. Load receiver wallet
7. Lock wallet rows using SELECT FOR UPDATE
8. Validate sufficient balance
9. Validate daily transfer limit
10. Debit sender wallet
11. Credit receiver wallet
12. Insert debit ledger entry
13. Insert credit ledger entry
14. Insert transfer record
15. Save idempotency response
16. Create activity log
17. Commit transaction
```

Concurrency control:

```sql
SELECT *
FROM wallets
WHERE id = ?
FOR UPDATE;
```

When two wallets are locked, the service locks them in deterministic order by wallet ID to reduce deadlock risk.

---

## 9. Top-Up Architecture

Top-up is handled by Payment Service.

Top-up flow:

```txt
1. User calls POST /payments/top-up
2. Payment Service validates JWT
3. Payment Service checks Idempotency-Key
4. Payment Service checks Redis rate limit
5. Payment Service creates top-up transaction
6. Payment Service calls Mock Gateway
7. If gateway does not respond within 5 seconds, transaction becomes PENDING
8. Mock Gateway sends webhook callback
9. Payment Service validates HMAC
10. SUCCESS webhook creates outbox event
11. Outbox worker publishes a wallet-credit command to Kafka
12. Wallet Service consumes the command and credits wallet idempotently
```

---

## 10. Outbox Architecture

Payment Service uses outbox table for wallet credit after successful top-up.

Reason:

A successful payment webhook must not be lost if Wallet Service is temporarily unavailable.

Outbox flow:

```txt
1. Payment Service receives SUCCESS webhook
2. Payment Service updates top-up transaction to SUCCESS
3. Payment Service inserts CREDIT_WALLET_REQUESTED outbox event
4. Scheduled worker reads pending outbox events
5. Worker publishes the wallet-credit command to Kafka
6. If the broker acks, mark outbox event as PROCESSED
7. If publishing fails, increase retry_count and update next_retry_at
```

Wallet Service credit consumption must be idempotent by transaction ID.

---

## 11. Idempotency Architecture

Mutating endpoints require:

```txt
Idempotency-Key
```

Applied to:

```txt
POST /wallets/transfer
POST /payments/top-up
```

Wallet credit replays use the same idempotency keys, carried on the Kafka
wallet-credit command rather than an HTTP header.

Behavior:

```txt
First request:
- Process normally
- Store request hash
- Store response status
- Store response body

Duplicate request with same key and same payload:
- Return stored response
- Do not process again

Duplicate key with different payload:
- Return 409 Conflict
```

---

## 12. Security Architecture

Authentication:

* JWT access token
* Refresh token

Rules:

* Access token expires in 15 minutes
* Refresh token expires in 7 days
* Password is hashed with BCrypt
* Refresh token is stored as hash
* Wallet and payment endpoints require valid access token
* Webhook endpoint validates HMAC signature
* Secrets come from environment variables

Webhook signature:

```txt
HMAC_SHA256(secret, timestamp + "." + rawRequestBody)
```

Headers:

```txt
X-Gateway-Signature
X-Gateway-Timestamp
```

---

## 13. Rate Limiting Architecture

Payment top-up endpoint is limited to:

```txt
5 requests per minute per user
```

Redis key format:

```txt
rate-limit:payment:{userId}:{yyyyMMddHHmm}
```

Logic:

```txt
INCR key
EXPIRE key 60 seconds
Reject if count > 5
```

---

## 14. Observability Architecture

Every request should have a traceId.

Use:

```txt
X-Trace-Id
MDC logging
WebClient header propagation
```

If request does not include `X-Trace-Id`, the service generates one.

Important transaction logs include:

```txt
traceId
userId
transactionId
durationMs
serviceName
action
status
```

Activity logs are stored in service-owned `activity_logs` tables.

---

## 15. Local Runtime Architecture

Docker Compose runs:

```txt
postgres
redis
auth-service
wallet-service
payment-service
mock-gateway-service
```

Initial development may start with only:

```txt
postgres
redis
auth-service
```

Full compose setup is completed after all services exist.

