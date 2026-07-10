# Event Contracts And Async Delivery

This document describes the Kafka event contracts introduced for the
payment-to-wallet top-up credit flow and the trade-offs versus the previous
synchronous HTTP delivery.

## Topics

| Topic                       | Producer        | Consumer             | Purpose                                    |
| --------------------------- | --------------- | -------------------- | ------------------------------------------ |
| `wallet.credit.commands`    | payment-service | wallet-service       | Credit a wallet after a successful top-up  |
| `wallet.transfer.completed` | wallet-service  | notification-service | Email the sender after a successful transfer |
| `wallet.transfer.received`  | wallet-service  | notification-service | Email the receiver after a successful transfer |
| `wallet.transfer.failed`    | wallet-service  | notification-service | Email the sender after a rejected transfer |
| `payment.topup.succeeded`   | payment-service | notification-service | Email the user after a successful top-up   |
| `payment.topup.failed`      | payment-service | notification-service | Email the user after a failed top-up       |
| `dead-letter.events`        | wallet-service, notification-service | (operators) | Records the consumers cannot apply |

A single-node Kafka broker runs locally via `docker-compose.yml` in KRaft mode
(no ZooKeeper). Both services read `KAFKA_BOOTSTRAP_SERVERS` (default
`localhost:9092` for host runs, `kafka:29092` inside Compose).

## `wallet.credit.commands` message

- **Key**: wallet id (string). Keying by wallet preserves per-wallet ordering
  across partitions.
- **Headers**:
  - `Idempotency-Key`: `payment-outbox-{outboxEventId}`. Durable idempotency key
    reused by the wallet credit workflow.
  - `X-Trace-Id`: request trace id, propagated from the originating top-up so
    producer and consumer logs share one trace.
- **Value** (JSON, snake_case):

```json
{
  "wallet_id": "f1c0...",
  "payment_transaction_id": "9ab2...",
  "amount": 75000
}
```

`amount` is whole IDR (`Long`). The value schema is identical to the
wallet-service `InternalWalletCreditRequest` so the consumer deserializes
directly into it.

## `wallet.transfer.completed` / `wallet.transfer.received` / `wallet.transfer.failed` messages

- **Key**: sender wallet id (string), preserving per-wallet ordering.
- **Headers**:
  - `Idempotency-Key`: `wallet-outbox-{outboxEventId}`. The notification service
    parses the event id from this key and dedups delivery attempts on it.
  - `X-Trace-Id`: trace id propagated from the originating transfer request.
- **Value** (JSON, snake_case):

```json
{
  "transfer_id": "3fd1...",
  "sender_wallet_id": "f1c0...",
  "receiver_wallet_id": "82aa...",
  "user_id": "77b3...",
  "amount": 40000,
  "failure_reason": null
}
```

`amount` is whole IDR (`Long`). `user_id` is the auth-service user id of the
notification recipient: the transfer initiator on `wallet.transfer.completed`
and `wallet.transfer.failed`, and the receiving wallet's owner on
`wallet.transfer.received`. `failure_reason` is set only on
`wallet.transfer.failed` (for example `INSUFFICIENT_BALANCE` or
`DAILY_TRANSFER_LIMIT_EXCEEDED`).

A successful transfer enqueues two outbox events in the same transaction — a
sender-side `TRANSFER_COMPLETED` ("your transfer was sent") and a
receiver-side `TRANSFER_RECEIVED` ("you received Rp...") — while a failed
transfer enqueues only the sender's `TRANSFER_FAILED` event.

Producer reliability mirrors the payment outbox: wallet-service writes the
`PENDING` `outbox_events` rows in the same transaction as the transfer, and
`WalletOutboxWorker` publishes with `acks=all` + idempotent producer, marking
each row `PROCESSED` only after the broker ack and leaving it `PENDING` with
backoff on failure.

## `payment.topup.succeeded` / `payment.topup.failed` messages

- **Key**: wallet id (string), preserving per-wallet ordering.
- **Headers**:
  - `Idempotency-Key`: `payment-outbox-{outboxEventId}`. Same event-id dedup as
    the transfer topics.
  - `X-Trace-Id`: trace id propagated from the originating top-up.
- **Value** (JSON, snake_case):

```json
{
  "payment_transaction_id": "9ab2...",
  "user_id": "77b3...",
  "wallet_id": "f1c0...",
  "amount": 75000,
  "failure_reason": null
}
```

`amount` is whole IDR (`Long`). `failure_reason` is set only on
`payment.topup.failed`. On a successful top-up the gateway webhook enqueues the
`TOPUP_SUCCEEDED` event alongside (not replacing) the existing
`CREDIT_WALLET_REQUESTED` wallet-credit event, in the same database
transaction. `PaymentTopupEventWorker` drains both top-up event types with the
same claim/ack/retry rules as the wallet-credit worker.

### Consumer (notification-service)

- `TransferNotificationConsumer` and `PaymentNotificationConsumer` restore the
  trace id from `X-Trace-Id` into MDC, parse the event id from
  `Idempotency-Key`, and delegate to `NotificationService`.
- Delivery is idempotent by event id: a `notification_attempts` row with a
  unique `event_id` records PENDING/SENT/FAILED and a retry count; redelivered
  events whose attempt is already SENT are skipped.
- The recipient email is resolved through auth-service's internal user lookup
  (`GET /internal/users/{id}`, `X-Internal-Token`).
- Failure handling uses a `DefaultErrorHandler` with a
  `DeadLetterPublishingRecoverer`: transient faults (SMTP connectivity, auth
  lookup unavailability) retry 10 times, 2s apart; malformed events, unknown
  users, invalid recipient addresses, and SMTP authentication misconfiguration
  go straight to `dead-letter.events`.

## Delivery and reliability

### Producer (payment-service)

- The transactional outbox is preserved. A successful top-up writes a `PENDING`
  `OutboxEvent` in the same database transaction as the transaction state change.
- `PaymentOutboxWorker` polls due `PENDING` events, claims a row (row lock +
  `PROCESSING`), and publishes to `wallet.credit.commands`.
- The producer uses `acks=all` with idempotent producer enabled, and the worker
  blocks on the send future. The outbox row is marked `PROCESSED` only after the
  broker acknowledges the publish.
- On publish failure the worker calls `recordFailedAttempt(...)`, leaving the
  event `PENDING` for retry with the existing backoff. Attempt-budget and
  max-age rules still route exhausted events to `FAILED`.

### Consumer (wallet-service)

- `WalletCreditCommandConsumer` (`@KafkaListener`) restores the trace id from the
  `X-Trace-Id` header into MDC, then calls the existing
  `InternalWalletCreditService.credit(...)` with the `Idempotency-Key` header and
  the configured internal token.
- Credits are idempotent: duplicate deliveries are ignored by the existing
  idempotency-key check and the `payment_transaction_id` ledger dedup, so
  at-least-once delivery cannot double-credit a wallet.
- Failure handling uses a `DefaultErrorHandler` with a
  `DeadLetterPublishingRecoverer`:
  - Transient failures (e.g. a database lock timeout or brief connection drop)
    are retried with a generous budget (10 attempts, 2s apart) so the credit
    recovers within the live flow before any dead-lettering.
  - Non-retryable failures (missing/invalid payload, wallet not found,
    idempotency/payment-transaction conflicts, internal-auth failure) and
    deserialization errors go straight to `dead-letter.events`.
  - `dead-letter.events` is a money-movement queue: any record landing here is
    an un-credited top-up and must be monitored/alerted and manually replayed,
    not treated as a passive log.

## HTTP vs Kafka trade-off

The previous design had the outbox worker POST to
`wallet-service /internal/wallets/credit` over HTTP (WebClient). That path has
been **replaced** by Kafka; there is exactly one delivery path.

| Concern          | HTTP (previous)                                  | Kafka (current)                                            |
| ---------------- | ------------------------------------------------ | ---------------------------------------------------------- |
| Coupling         | Payment must reach wallet synchronously          | Decoupled via broker; wallet can be down briefly           |
| Back-pressure    | Worker thread blocks on wallet latency           | Broker buffers; consumer drains at its own pace            |
| Ordering         | None guaranteed                                  | Per-wallet ordering via partition key                      |
| Retry/durability | Outbox retry + wallet idempotency                | Outbox retry until broker ack + consumer retry/DLT         |
| Failure surface  | Wallet 4xx/5xx mapped to retryable/non-retryable | Producer failure keeps event `PENDING`; consumer uses DLT  |
| Operational cost | No broker to run                                 | Requires Kafka broker and topic/DLT monitoring             |

Idempotency is the shared safety net in both designs: the outbox guarantees
at-least-once delivery, and the wallet credit is idempotent by
`Idempotency-Key` + `payment_transaction_id`, so retries and replays converge to
a single credit.
