# Notification Service

Consumes wallet transfer and payment top-up result events from Kafka and sends
transactional emails over SMTP. Locally, emails land in
[Mailpit](https://mailpit.axllent.org/) instead of a real mail provider.

## What it does

- Listens on five topics (see `docs/event-contracts.md` for full contracts):
  - `wallet.transfer.completed` / `wallet.transfer.received` /
    `wallet.transfer.failed` (from wallet-service; a successful transfer emails
    both the sender and the receiver)
  - `payment.topup.succeeded` / `payment.topup.failed` (from payment-service)
- Resolves the recipient email through auth-service's internal user lookup
  (`GET /internal/users/{id}` with `X-Internal-Token`).
- Renders HTML emails with Thymeleaf templates from
  `src/main/resources/templates/email/`. Amounts are whole IDR (`Long`)
  formatted as `Rp10,000`; each email carries a trace-id footer.
- Persists every delivery attempt in its own `notification_db`
  (`notification_attempts`), never touching another service's database.

## Idempotency

Producers set the Kafka header `Idempotency-Key: <service>-outbox-<eventId>`.
The consumer parses the event id from that key and stores it in the unique
`notification_attempts.event_id` column. A redelivered event whose attempt is
already `SENT` is skipped, so at-least-once delivery cannot double-send an
email.

## Retry and failure handling

- Attempt rows move `PENDING -> SENT` on success and record a `retry_count`.
- Transient failures (SMTP connection errors, auth lookup unavailability) throw
  `EmailDeliveryException`: the attempt stays `PENDING` with an incremented
  retry count and the Kafka `DefaultErrorHandler` retries (10 attempts, 2s
  apart). When the persisted retry budget (`NOTIFICATION_RETRY_MAX_ATTEMPTS`,
  default 10) is exhausted the attempt is marked `FAILED`.
- Non-retryable failures (malformed event, unknown user, invalid recipient
  address, broken template, SMTP authentication misconfiguration) throw
  `NonRetryableNotificationException`: the attempt is marked `FAILED`
  immediately and the record is routed to `dead-letter.events`.

## Local configuration

All configuration is environment-backed (see `src/main/resources/application.yml`
for defaults):

```text
KAFKA_BOOTSTRAP_SERVERS
NOTIFICATION_AUTH_INTERNAL_TOKEN
NOTIFICATION_AUTH_TIMEOUT_MS
NOTIFICATION_AUTH_USER_LOOKUP_URL
NOTIFICATION_DB_PASSWORD
NOTIFICATION_DB_URL
NOTIFICATION_DB_USERNAME
NOTIFICATION_KAFKA_CONSUMER_GROUP_ID
NOTIFICATION_MAIL_FROM_ADDRESS
NOTIFICATION_MAIL_HOST
NOTIFICATION_MAIL_PORT
NOTIFICATION_RETRY_MAX_ATTEMPTS
```

`NOTIFICATION_AUTH_INTERNAL_TOKEN` must match auth-service's
`AUTH_INTERNAL_TOKEN`.

## Running locally

The full stack (including Kafka, Mailpit, and this service) runs via Compose
from the repo root:

```bash
docker compose up --build
```

Then:

1. Register a user, top up, or transfer through the normal APIs (see the root
   README / Postman collection).
2. Producers write outbox rows in the same transaction as the money movement;
   their outbox workers publish to Kafka within ~5 seconds.
3. Open the Mailpit web UI at <http://localhost:8025> to see the sent emails
   (SMTP listens on `localhost:1025`).

Delivery attempts are inspectable in `notification_db`:

```sql
SELECT event_id, notification_type, recipient_email, status, retry_count, sent_at
FROM notification_attempts
ORDER BY created_at DESC;
```

## Testing

```bash
./gradlew :notification-service:test
```

Unit tests cover template rendering, idempotent skip, successful send, and the
retryable/non-retryable failure classification. Integration tests run against a
Testcontainers PostgreSQL with the Flyway migrations applied; no manual Docker
Compose is required (Docker itself must be running).
