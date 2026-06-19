# GPay Wallet System PRD

## 1. Project Overview

GPay Wallet System is a wallet-based microservice application built for a technical test.

The system supports:

* User registration and login
* JWT access token and refresh token
* Wallet balance checking
* Wallet mutation history with pagination
* Top-up through mock payment gateway
* Payment gateway webhook callback
* HMAC webhook validation
* Wallet-to-wallet transfer
* Atomic wallet balance update
* Concurrent transfer safety
* Idempotency
* Daily transfer limit
* Payment endpoint rate limiting
* Audit/activity logs
* TraceId propagation across services
* Docker Compose setup
* PostgreSQL migration using Flyway
* Redis for rate limiting
* Postman collection for manual testing

The assessment focuses not only on working code, but also architecture decisions, edge case handling, failure scenarios, technical trade-offs, and documentation. The required stack includes Java 21, Spring Boot 3+, PostgreSQL or MongoDB, Redis, and Docker Compose.

---

# 2. Main Goal

Build a realistic but deadline-focused wallet microservice system.

The system should be simple enough to complete before the deadline, but strong enough to demonstrate:

* Clean microservice boundaries
* Safe balance handling
* Reliable payment processing
* Secure authentication
* Idempotency
* Rate limiting
* Traceable logs
* Clear technical documentation

---

# 3. Architecture Decision

## 3.1 Monorepo

Use one repository:

```txt
gpay-wallet-system
```

The repository contains multiple Spring Boot services.

```txt
services/auth-service
services/wallet-service
services/payment-service
services/mock-gateway-service
```

## Reason

A monorepo is used because this is a technical test. It keeps local development, Docker Compose, documentation, and review easier.

## Trade-off

Pros:

* Easier to run locally
* Easier to review
* Easier Docker Compose setup
* Good for technical test delivery

Cons:

* In a larger production team, service ownership may require stricter repository boundaries

---

# 4. Services

The system contains four services:

```txt
auth-service
wallet-service
payment-service
mock-gateway-service
```

---

## 4.1 Auth Service

Auth Service is responsible for user identity and token issuing.

### Responsibilities

* Register user
* Login user
* Generate access token
* Generate refresh token
* Refresh access token
* Hash password
* Store refresh token hash

### Endpoints

```txt
POST /auth/register
POST /auth/login
POST /auth/refresh
GET  /auth/me
```

### Rules

* Password must be hashed using BCrypt
* Plain password must never be stored
* Refresh token must be stored as hash
* Access token expiry: 15 minutes
* Refresh token expiry: 7 days
* Secrets must come from environment variables
* No hardcoded secrets

### Database

Auth Service owns:

```txt
auth_db
```

Tables:

```txt
users
refresh_tokens
```

---

## 4.2 Wallet Service

Wallet Service is responsible for wallet balance and money movement.

### Responsibilities

* Wallet balance
* Mutation history
* Wallet-to-wallet transfer
* Ledger entries
* Daily transfer limit
* Wallet idempotency
* Internal wallet credit from Payment Service
* Wallet activity logs

### Endpoints

```txt
GET  /wallets/balance
GET  /wallets/mutations?page=0&size=20
POST /wallets/transfer
```

Internal wallet credit from Payment Service is delivered over Kafka (consumed by
`WalletCreditCommandConsumer`), not as an HTTP endpoint.

### Rules

* All public endpoints must be protected by JWT
* Transfer must validate sufficient balance
* Transfer must be atomic
* Debit and credit must happen in one database transaction
* If one side fails, both sides must rollback
* Concurrent requests to the same wallet must not corrupt balance
* Use PostgreSQL row-level locking with `SELECT FOR UPDATE`
* Lock wallets in deterministic order by wallet ID
* Every successful debit/credit must create ledger entry
* Transfer must support idempotency key
* Transfer must respect daily transfer limit
* Wallet internal credit must be idempotent

The technical test explicitly requires balance consistency under concurrent requests, atomic transfer, and no race condition on concurrent transfer to the same wallet.

### Database

Wallet Service owns:

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

## 4.3 Payment Service

Payment Service is responsible for top-up lifecycle.

### Responsibilities

* Top-up request
* Mock gateway call
* Gateway timeout handling
* Gateway webhook handling
* HMAC webhook validation
* Payment transaction status
* Payment idempotency
* Redis payment rate limit
* Outbox retry for wallet credit
* Payment activity logs

### Endpoints

```txt
POST /payments/top-up
POST /payments/webhook/gateway
GET  /payments/{id}
```

### Rules

* Top-up endpoint must be protected by JWT
* Webhook endpoint is called by Mock Gateway
* Top-up request must call Mock Gateway
* If gateway does not respond within 5 seconds, transaction becomes `PENDING`
* Gateway callback can return `SUCCESS`, `FAILED`, or no response
* Webhook must be validated using HMAC signature
* Successful webhook creates wallet credit request
* Failed webhook must not credit wallet
* Wallet credit after successful top-up must be retryable
* Use outbox table and scheduled worker for wallet credit retry
* Payment endpoint must be rate-limited to 5 requests per minute per user
* Payment top-up must support idempotency key

The technical test requires top-up through a mock payment gateway, webhook callback, HMAC validation, pending status after 5 seconds without response, and rate limiting on payment endpoint.

### Database

Payment Service owns:

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

## 4.4 Mock Gateway Service

Mock Gateway Service simulates an external payment gateway.

### Responsibilities

* Receive top-up request from Payment Service
* Simulate successful payment
* Simulate failed payment
* Simulate timeout/no response
* Send webhook callback to Payment Service
* Sign webhook payload using HMAC

### Endpoint

```txt
POST /mock-gateway/top-up
```

### Modes

```txt
SUCCESS
FAILED
TIMEOUT
```

### Rules

* `SUCCESS` mode sends successful webhook
* `FAILED` mode sends failed webhook
* `TIMEOUT` mode delays or does not respond within 5 seconds
* Webhook payload must be signed using HMAC

---

# 5. Database Strategy

## 5.1 Database Isolation

Use one PostgreSQL Docker container with one database per service:

```txt
auth_db
wallet_db
payment_db
```

Each service must only connect to its own database.

Services must not query another service database directly.

## Reason

This keeps microservice data ownership clear while keeping Docker Compose simple.

## Trade-off

Pros:

* Clear service ownership
* More realistic than shared tables
* Still simple to run locally

Cons:

* All databases are hosted in one PostgreSQL container for local development
* Production may use separate database instances

---

# 6. Migration Strategy

Use Flyway migration inside each service.

```txt
auth-service    -> migrations for auth_db
wallet-service  -> migrations for wallet_db
payment-service -> migrations for payment_db
```

Use:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Flyway creates tables. Hibernate validates entity mapping.

Do not use:

```txt
ddl-auto=create
ddl-auto=update
manual table creation
one global migration folder
```

---

# 7. Money Amount Strategy

Use integer amount for wallet money.

Database type:

```txt
BIGINT
```

Java type:

```txt
Long
```

All amounts represent whole IDR.

Example:

```txt
Rp10,000 -> 10000
```

Do not use:

```txt
double
float
Double
Float
```

## Reason

Using integer amount avoids floating point precision issues and keeps wallet calculation simple.

---

# 8. Service Communication

Use HTTP communication.

Use Spring WebClient for service-to-service calls.

Communication map:

```txt
payment-service -> mock-gateway-service
mock-gateway-service -> payment-service webhook
payment-service -> wallet-service internal credit
```

Do not use RabbitMQ for v1.

## Reason

HTTP/WebClient is simpler, easier to test, and enough for this technical test.

## Trade-off

Pros:

* Simple implementation
* Easy Postman testing
* Easy local debugging
* Natural fit for webhook flow

Cons:

* Requires retry handling for service failure
* Less decoupled than message broker

---

# 9. Outbox Pattern

Payment Service uses outbox pattern for wallet credit after successful top-up.

## Flow

```txt
1. Payment Service receives SUCCESS webhook
2. Payment Service updates topup transaction to SUCCESS
3. Payment Service saves outbox event CREDIT_WALLET_REQUESTED
4. Scheduled worker reads pending outbox events
5. Worker calls Wallet Service internal credit endpoint
6. If call succeeds, outbox is marked PROCESSED
7. If call fails, retry_count is increased and next_retry_at is updated
```

Wallet Service internal credit must be idempotent using transaction ID.

## Reason

If Payment Service receives successful webhook but Wallet Service is temporarily down, wallet credit request must not be lost.

---

# 10. Idempotency

All mutating endpoints must support `Idempotency-Key`.

Apply idempotency to:

```txt
POST /wallets/transfer
POST /payments/top-up
```

Wallet credit applies the same idempotency keys, carried on the Kafka
wallet-credit command rather than an HTTP header.

## Behavior

First request:

```txt
Process normally
Save request hash
Save response status
Save response body
Return response
```

Duplicate request with same key and same payload:

```txt
Return exact same response
Do not process again
```

Same key with different payload:

```txt
Return 409 Conflict
```

The technical test requires that requests with the same identifier must not be processed twice and duplicate response must be identical to the first response.

---

# 11. Security

## JWT

Access token:

```txt
15 minutes
```

Refresh token:

```txt
7 days
```

Wallet and payment endpoints must require valid access token.

## Password

Use BCrypt.

Plain password must never be stored.

## Refresh Token

Store only refresh token hash.

## Webhook HMAC

Gateway webhook must include:

```txt
X-Gateway-Signature
X-Gateway-Timestamp
```

Signature format:

```txt
HMAC_SHA256(secret, timestamp + "." + rawRequestBody)
```

Payment Service validates signature before processing webhook.

## Secrets

All secrets must use environment variables.

Examples:

```txt
JWT_SECRET
GATEWAY_WEBHOOK_SECRET
POSTGRES_PASSWORD
```

No secrets should be hardcoded in source code.

---

# 12. Rate Limiting

Payment endpoint limit:

```txt
Maximum 5 requests per minute per user
```

Use Redis.

Redis key:

```txt
rate-limit:payment:{userId}:{yyyyMMddHHmm}
```

Logic:

```txt
INCR key
EXPIRE key 60 seconds
If count > 5, reject request
```

Response:

```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Maximum 5 payment requests per minute allowed"
}
```

---

# 13. Daily Transfer Limit

Daily transfer amount limit must be configurable.

Environment variable:

```txt
MAX_DAILY_TRANSFER_AMOUNT=10000000
```

Wallet Service checks total successful transfer amount by sender for the current day.

If:

```txt
todayTransferTotal + requestedAmount > MAX_DAILY_TRANSFER_AMOUNT
```

Then reject the transfer.

---

# 14. Observability and Auditability

## TraceId

Every request must have a traceId.

Use:

```txt
X-Trace-Id
MDC logging
WebClient header propagation
```

If the request does not contain `X-Trace-Id`, service generates one.

## Logs

Important transaction logs must include:

```txt
traceId
userId
transactionId
durationMs
serviceName
action
status
```

The technical test asks that every request has traceId across service logs, and transaction logs include userId, transactionId, and duration.

## Activity Logs

Each transaction-related service must write activity logs.

Minimum activity logs:

Wallet Service:

```txt
TRANSFER_SUCCESS
TRANSFER_FAILED
WALLET_CREDIT_SUCCESS
WALLET_CREDIT_FAILED
```

Payment Service:

```txt
TOPUP_CREATED
TOPUP_PENDING
WEBHOOK_SUCCESS
WEBHOOK_FAILED
OUTBOX_PROCESSED
OUTBOX_FAILED
```

---

# 15. Testing Strategy

Do not make tests as complex as the previous property billing project.

Use focused tests only.

## Test Priorities

```txt
1. Wallet transfer correctness
2. Idempotency
3. HMAC webhook validation
4. Redis rate limit
5. Auth basic behavior
```

## Automated Test Target

Total target:

```txt
18 to 24 focused tests
```

## Auth Service Tests

```txt
Register success
Duplicate email fails
Login success
Wrong password fails
Refresh token success
```

## Wallet Service Tests

```txt
Transfer success
Insufficient balance fails
Sender balance decreases
Receiver balance increases
Debit ledger is created
Credit ledger is created
Duplicate idempotency key does not transfer twice
Daily transfer limit exceeded fails
Concurrent transfer does not make balance negative
```

## Payment Service Tests

```txt
Top-up request creates transaction
Gateway timeout marks transaction as PENDING
Duplicate idempotency key returns same response
Valid HMAC webhook is accepted
Invalid HMAC webhook is rejected
SUCCESS webhook creates outbox event
FAILED webhook does not create wallet credit event
6th payment request in one minute is rejected
```

## Manual Postman Testing

Postman must cover:

```txt
Register user A
Register user B
Login user A
Login user B
Check wallet balance
Top-up SUCCESS
Check balance after top-up
Transfer user A to user B
Check mutation history
Duplicate transfer idempotency key
Insufficient balance transfer
Payment rate limit
Top-up FAILED
Top-up TIMEOUT/PENDING
Invalid webhook signature
```

---

# 16. Development Milestones

## Milestone 1 — Project Foundation

Scope:

```txt
Root project
Docker Compose
PostgreSQL
Redis
docs/PRD.md
AGENTS.md
CODEX.java.md
```

Acceptance criteria:

```txt
docker compose up -d works
auth_db exists
wallet_db exists
payment_db exists
Redis runs
PRD exists
Codex instruction files exist
```

---

## Milestone 2 — Auth Database Migration

Scope:

```txt
auth-service
```

Acceptance criteria:

```txt
Auth Service connects to auth_db
Flyway migration runs
users table exists
refresh_tokens table exists
flyway_schema_history exists
Hibernate ddl-auto validate passes
```

---

## Milestone 3 — Auth Register Endpoint

Endpoint:

```txt
POST /auth/register
```

Acceptance criteria:

```txt
Valid request returns 201 Created
Duplicate email returns 409 Conflict
Invalid email returns 400 Bad Request
Password is hashed
Response does not expose password hash
Basic tests exist
```

---

## Milestone 4 — Auth Login Endpoint

Endpoint:

```txt
POST /auth/login
```

Acceptance criteria:

```txt
Valid login returns access token and refresh token
Wrong password returns 401 Unauthorized
Access token expiry is 15 minutes
Refresh token expiry is 7 days
Refresh token is stored hashed
Basic tests exist
```

---

## Milestone 5 — Auth Refresh and Me Endpoint

Endpoints:

```txt
POST /auth/refresh
GET /auth/me
```

Acceptance criteria:

```txt
Valid refresh token returns new tokens
Old refresh token is revoked if using rotation
Invalid refresh token fails
/auth/me returns current user data
JWT filter works
```

---

## Milestone 6 — Wallet Database Migration

Scope:

```txt
wallet-service
```

Acceptance criteria:

```txt
Wallet Service connects to wallet_db
Flyway migration runs
wallets table exists
ledger_entries table exists
transfers table exists
idempotency_keys table exists
activity_logs table exists
```

---

## Milestone 7 — Wallet Balance and Mutation Endpoints

Endpoints:

```txt
GET /wallets/balance
GET /wallets/mutations
```

Acceptance criteria:

```txt
Protected by JWT
Balance endpoint returns current wallet balance
Mutation endpoint supports pagination
```

---

## Milestone 8 — Wallet Transfer Endpoint

Endpoint:

```txt
POST /wallets/transfer
```

Acceptance criteria:

```txt
Protected by JWT
Valid transfer succeeds
Insufficient balance fails
Debit and credit are atomic
Ledger entries are created
SELECT FOR UPDATE is used
Concurrent transfer does not corrupt balance
Daily transfer limit works
Idempotency works
Activity log is created
Focused tests exist
```

---

## Milestone 9 — Payment Database Migration

Scope:

```txt
payment-service
```

Acceptance criteria:

```txt
Payment Service connects to payment_db
Flyway migration runs
topup_transactions table exists
idempotency_keys table exists
outbox_events table exists
activity_logs table exists
```

---

## Milestone 10 — Payment Top-Up Endpoint

Endpoint:

```txt
POST /payments/top-up
```

Acceptance criteria:

```txt
Protected by JWT
Creates topup transaction
Calls Mock Gateway
Gateway timeout after 5 seconds marks transaction PENDING
Idempotency works
Redis rate limit works
```

---

## Milestone 11 — Mock Gateway Service

Endpoint:

```txt
POST /mock-gateway/top-up
```

Acceptance criteria:

```txt
SUCCESS mode works
FAILED mode works
TIMEOUT mode works
Webhook callback is sent to Payment Service
Webhook payload is signed with HMAC
```

---

## Milestone 12 — Payment Webhook and Outbox

Endpoint:

```txt
POST /payments/webhook/gateway
```

Acceptance criteria:

```txt
Valid HMAC accepted
Invalid HMAC rejected
SUCCESS webhook updates transaction to SUCCESS
SUCCESS webhook creates outbox event
FAILED webhook updates transaction to FAILED
FAILED webhook does not credit wallet
Outbox worker retries wallet credit
```

---

## Milestone 13 — Wallet Internal Credit

Delivery:

```txt
Kafka wallet-credit command (consumed by WalletCreditCommandConsumer)
```

Acceptance criteria:

```txt
Published by Payment Service
Credits wallet
Creates ledger entry
Is idempotent by transactionId
Does not duplicate balance on retry
```

---

## Milestone 14 — TraceId and Activity Logs

Acceptance criteria:

```txt
Every request has traceId
Logs include traceId
WebClient forwards X-Trace-Id
Transaction logs include userId, transactionId, durationMs
Activity logs are inserted
```

---

## Milestone 15 — Docker Compose, README, and Postman

Acceptance criteria:

```txt
docker compose up --build works
All services start
README explains architecture
README explains how to run
README explains trade-offs
README explains failure scenarios
Postman collection exists
No hardcoded secrets
```

---

# 17. GitHub Issues Plan

Create issues in this order:

```txt
#1 Project foundation and docs
#2 Auth database migration
#3 Auth register endpoint
#4 Auth login endpoint
#5 Auth refresh and me endpoint
#6 Wallet database migration
#7 Wallet balance endpoint
#8 Wallet mutation history endpoint
#9 Wallet transfer endpoint
#10 Wallet transfer idempotency and daily limit
#11 Payment database migration
#12 Payment top-up endpoint
#13 Payment Redis rate limit
#14 Mock gateway service
#15 Payment webhook HMAC
#16 Payment outbox worker
#17 Wallet internal credit endpoint
#18 TraceId and activity logs
#19 Docker Compose all services
#20 Postman collection
#21 README finalization
```

---

# 18. Work Priority

Do not build everything at once.

Recommended order:

```txt
1. Project foundation
2. Auth Service
3. Wallet Service
4. Payment Service
5. Mock Gateway Service
6. Observability
7. Documentation
```

For the first development session, only complete:

```txt
Project foundation
Auth database migration
Auth register endpoint
```

---

# 19. Out of Scope for V1

Do not implement:

```txt
RabbitMQ
Kafka
API Gateway
Kubernetes
Terraform
Multiple currencies
Decimal money handling
Admin dashboard
Frontend
Complex shared libraries
Full distributed tracing stack
100% test coverage
```

These are intentionally skipped to keep the project deadline-focused.

---

# 20. Definition of Done

The project is complete when:

```txt
Docker Compose starts all services
User can register
User can login
User can refresh token
Protected endpoints reject invalid token
User can check wallet balance
User can view mutation history with pagination
User can top-up
Top-up SUCCESS credits wallet
Top-up FAILED does not credit wallet
Top-up TIMEOUT becomes PENDING
Webhook HMAC validation works
User can transfer balance to another user
Transfer is atomic
Concurrent transfer does not corrupt balance
Insufficient balance transfer fails
Duplicate idempotency key does not process twice
Duplicate idempotency response matches first response
Daily transfer limit works
Payment endpoint rate limit works
Activity logs are created
TraceId appears in logs
README explains architecture and trade-offs
Postman collection is included
No secrets are hardcoded
Source code is pushed to GitHub/GitLab
```

