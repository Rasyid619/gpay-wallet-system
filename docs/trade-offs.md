# Technical Trade-Offs

## 1. Purpose

This document explains the main technical decisions and trade-offs in the GPay Wallet System.

The goal is to show why certain technologies and patterns were selected, and what limitations are accepted for this technical test.

---

# 2. Monorepo vs Multi-Repo

## Decision

Use monorepo.

```txt
gpay-wallet-system/
└── services/
    ├── auth-service/
    ├── wallet-service/
    ├── payment-service/
    └── mock-gateway-service/
```

## Reason

This is a technical test with a limited deadline. A monorepo makes the project easier to run, review, and document.

## Pros

* Easier local development
* Easier Docker Compose setup
* Easier code review
* Easier to keep documentation in one place
* Good fit for technical test submission

## Cons

* Less strict repository-level service isolation
* In a larger team, code ownership can become less clear
* CI/CD per service may require extra configuration

## Future Improvement

For production, each service can be moved to its own repository if the team needs stricter ownership and independent release pipelines.

---

# 3. One PostgreSQL Container vs Separate Database Instances

## Decision

Use one PostgreSQL Docker container with one database per service.

Databases:

```txt
auth_db
wallet_db
payment_db
```

## Reason

This preserves service database ownership while keeping Docker Compose simple.

## Pros

* Clear service data ownership
* Better than one shared database schema
* Simple local setup
* Lower Docker resource usage
* Easy to inspect locally

## Cons

* Databases still share one PostgreSQL server/container
* Not fully isolated at infrastructure level
* Production may need separate instances, users, backups, and scaling policies

## Future Improvement

Use separate PostgreSQL instances per service in production.

---

# 4. Flyway vs Hibernate Auto DDL

## Decision

Use Flyway migration per service.

Use:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

## Reason

Database schema should be explicit, reviewable, and versioned.

## Pros

* Versioned migrations
* Clear database history
* Safer than automatic schema update
* Easy for reviewers to inspect
* Good backend engineering practice

## Cons

* More files to maintain
* Entity changes require migration updates
* Slightly slower initial development than `ddl-auto=update`

## Rejected Option

Rejected:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

Reason:

```txt
It can silently modify schema and is less predictable.
```

---

# 5. BIGINT Money vs BigDecimal

## Decision

Use `BIGINT` for wallet money amount.

Database:

```txt
BIGINT
```

Java:

```txt
Long
```

All amounts represent whole IDR.

Example:

```txt
Rp10,000 -> 10000
```

## Reason

The project uses IDR and does not require decimal currency handling. Integer money avoids floating point and decimal scale issues.

## Pros

* Exact calculation
* Simple comparison
* Simple transfer logic
* No BigDecimal scale issues
* Easy to test
* Good for IDR whole-number amounts

## Cons

* Less flexible for currencies that require decimals
* Requires clear documentation that amount is whole IDR
* Multi-currency support would require redesign

## Rejected Options

Rejected:

```txt
double
float
Double
Float
```

Reason:

```txt
Floating point types are unsafe for money.
```

Also not used for this test:

```txt
NUMERIC(19,2) + BigDecimal
```

Reason:

```txt
Valid option, but BIGINT is simpler and safer for IDR-only deadline scope.
```

---

# 6. HTTP/WebClient vs RabbitMQ

## Decision

Use HTTP/WebClient for service communication.

Communication:

```txt
payment-service -> mock-gateway-service
mock-gateway-service -> payment-service webhook
payment-service -> wallet-service internal credit
```

RabbitMQ is not used in v1.

## Reason

The system is deadline-focused, and the required flow naturally uses HTTP for gateway call and webhook callback.

## Pros

* Simple implementation
* Easy to test with Postman
* Easy local debugging
* Less infrastructure
* Natural fit for gateway and webhook

## Cons

* Tighter runtime coupling
* Requires timeout and retry handling
* Less decoupled than message broker

## Future Improvement

RabbitMQ can be added later for asynchronous events such as:

```txt
TopUpSucceeded
WalletCreditRequested
ActivityLogCreated
```

---

# 7. Outbox Table vs RabbitMQ

## Decision

Use outbox table and scheduled retry worker for wallet credit after successful top-up.

## Reason

If Payment Service receives SUCCESS webhook but Wallet Service is unavailable, the wallet credit request must not be lost.

## Pros

* Reliable retry without extra broker
* Easy to inspect from database
* Simple enough for technical test
* Works well with service-owned database

## Cons

* Polling worker is less real-time than message broker
* More manual retry logic
* Less scalable than dedicated queue

## Future Improvement

Replace polling outbox worker with RabbitMQ or Kafka consumer/publisher.

---

# 8. Transfer Inside Wallet Service vs Separate Transfer Service

## Decision

Transfer is handled inside Wallet Service.

## Reason

Transfer requires atomic debit and credit. Keeping transfer inside Wallet Service allows one local database transaction.

## Pros

* Strong consistency
* Simple rollback
* Easy concurrency control
* No distributed transaction needed
* Easier to reason about

## Cons

* Wallet Service owns more business logic
* Transfer behavior is tightly coupled with wallet balance

## Rejected Option

Rejected:

```txt
Separate Transfer Service
```

Reason:

```txt
It would require distributed transaction or Saga for debit and credit, which is unnecessary for this test.
```

---

# 9. PostgreSQL Row Locking vs Optimistic Locking

## Decision

Use PostgreSQL row-level locking with `SELECT FOR UPDATE`.

## Reason

Wallet balance is sensitive. Concurrent transfer must not corrupt balance.

## Pros

* Strong consistency
* Easy to understand
* Prevents overspending
* Good fit for financial balance updates

## Cons

* Concurrent requests to same wallet wait
* Lower throughput for very hot wallets
* Requires careful lock ordering to avoid deadlocks

## Future Improvement

For high-scale production, evaluate:

```txt
Optimistic locking
Ledger-first architecture
Balance snapshot projection
Queue-based wallet command processing
```

---

# 10. Idempotency Table vs Redis-Only Idempotency

## Decision

Store idempotency key and response in PostgreSQL.

## Reason

Idempotency for money movement must be durable.

## Pros

* Durable
* Survives Redis restart
* Can return exact original response
* Safer for transfer and top-up

## Cons

* More database writes
* Requires request hash and response serialization
* Requires cleanup strategy later

## Rejected Option

Rejected:

```txt
Redis-only idempotency
```

Reason:

```txt
Redis is not the source of truth for money-related request safety.
```

## Future Improvement

Add expiration cleanup job for old idempotency records.

---

# 11. Redis for Rate Limiting

## Decision

Use Redis for payment endpoint rate limiting.

Limit:

```txt
5 requests per minute per user
```

## Reason

Redis supports fast counters and TTL.

## Pros

* Fast
* Simple TTL support
* Good fit for rate limiting
* Avoids database write overhead for each request

## Cons

* Redis outage affects payment endpoint
* Needs clear failure behavior
* In distributed deployment, Redis must be shared

## Failure Policy

If Redis is unavailable, Payment Service should fail closed for top-up requests.

Reason:

```txt
Payment endpoint should not allow unlimited requests if rate limit cannot be verified.
```

---

# 12. Simple TraceId Logging vs Full OpenTelemetry Stack

## Decision

Use `X-Trace-Id`, MDC logging, and WebClient header propagation for v1.

## Reason

This satisfies traceability needs while staying simple for the deadline.

## Pros

* Simple implementation
* Easy to see traceId in logs
* No extra infrastructure required
* Works across service calls

## Cons

* No full distributed trace UI
* Less powerful than OpenTelemetry + Zipkin/Tempo
* Manual discipline needed for logging fields

## Future Improvement

Add OpenTelemetry and Zipkin/Tempo for full distributed tracing visualization.

---

# 13. Focused Tests vs Full Coverage

## Decision

Use focused tests for risky behavior.

Target:

```txt
18 to 24 automated tests
```

## Reason

The project scope is large and deadline is limited. Tests should focus on high-risk areas.

## Test Priority

```txt
1. Wallet transfer correctness
2. Idempotency
3. HMAC webhook validation
4. Rate limiting
5. Auth basic behavior
```

## Pros

* Proves important business behavior
* Realistic for deadline
* Avoids spending time on low-value tests
* Still gives reviewer confidence

## Cons

* Not full coverage
* Some integration behavior relies on Postman testing
* Some controller-level cases may not be automated

## Future Improvement

Add Testcontainers and full end-to-end integration tests.

---

# 14. No API Gateway in V1

## Decision

Do not implement API Gateway.

## Reason

The technical test does not require API Gateway. Direct service ports are simpler.

Ports:

```txt
auth-service:          8081
wallet-service:        8082
payment-service:       8083
mock-gateway-service:  8084
```

## Pros

* Faster development
* Easier debugging
* Less configuration

## Cons

* Client must know service ports
* Cross-cutting concerns are duplicated
* Not ideal for production

## Future Improvement

Add API Gateway for:

```txt
Central routing
Authentication forwarding
Rate limiting
Request logging
```

---

# 15. No Shared Library in V1

## Decision

Avoid shared libraries in initial implementation.

## Reason

Shared libraries can slow down development and introduce coupling.

## Pros

* Faster implementation
* Less Gradle complexity
* Easier service independence

## Cons

* Some duplicated code
* JWT validation and error response may repeat
* TraceId filter may repeat

## Future Improvement

Extract common code after services are stable:

```txt
common-security
common-web
common-tracing
common-idempotency
```

---

# 16. Payment Schema Strictness vs Flexible Text Schema

## Decision

Use a strict payment schema with PostgreSQL enums for fixed lifecycle states,
`JSONB` for stored payloads, and explicit payment-owned tables:

```txt
topup_transactions
idempotency_keys
outbox_events
activity_logs
```

Status and type columns use enums:

```txt
payment_status
outbox_event_type
outbox_event_status
```

## Reason

Payment state transitions are safety-sensitive. The database should reject
unknown statuses and event types even if application code has a bug. This keeps
the schema aligned with the documented payment lifecycle while still leaving
payload fields flexible enough for later endpoint, webhook, and outbox work.

## Pros

* Prevents invalid payment and outbox states at the database level
* Keeps Flyway migration behavior explicit and reviewable
* Uses `JSONB` for payloads that need structured storage and later inspection
* Keeps idempotency durable in PostgreSQL
* Supports later top-up, webhook, and wallet-credit outbox issues without adding endpoint behavior early

## Cons

* Enum changes require a migration
* Slightly less flexible than plain `TEXT` status fields
* More schema detail is needed before all payment workflows are implemented

## Rejected Option

Rejected:

```txt
TEXT-only statuses and payloads
```

Reason:

```txt
This is simpler initially, but it allows invalid lifecycle values and makes
reviewing payment state correctness harder.
```

## Current Design Notes

The foundation schema keeps:

* `idempotency_key` on `topup_transactions` for top-up lookup and auditing
* `(user_id, idempotency_key)` uniqueness in `idempotency_keys`
* `aggregate_id` in `outbox_events` so the outbox remains event-oriented
* `request_payload`, `response_payload`, and `duration_ms` in `activity_logs`
* `BIGINT` money fields and `Long` Java mappings

---

# 17. Summary of Main Decisions

```txt
Monorepo:
- Chosen for simple technical test delivery

One PostgreSQL container, multiple databases:
- Chosen for logical service isolation with simple local setup

Flyway:
- Chosen for versioned and reviewable migrations

BIGINT money:
- Chosen to avoid precision issues

HTTP/WebClient:
- Chosen for simplicity and webhook fit

Outbox:
- Chosen for reliable wallet credit retry without RabbitMQ

Payment schema strictness:
- Chosen to reject invalid lifecycle states at the database level

Transfer inside Wallet Service:
- Chosen for atomic local transaction

SELECT FOR UPDATE:
- Chosen for strong consistency

PostgreSQL idempotency:
- Chosen for durable duplicate protection

Redis rate limiting:
- Chosen for fast request counters

TraceId + MDC:
- Chosen for simple cross-service traceability

Focused tests:
- Chosen for deadline-realistic confidence
```
