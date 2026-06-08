# Failure Scenarios

## 1. Purpose

This document describes important failure scenarios in the GPay Wallet System and how the system handles them.

The goal is to demonstrate that the system is designed not only for happy paths, but also for edge cases and partial failures.

---

# 2. Auth Service Failure Scenarios

## 2.1 Duplicate Email Registration

Scenario:

```txt
User tries to register with an email that already exists.
```

Risk:

```txt
Duplicate users may be created.
```

Handling:

```txt
users.email has UNIQUE constraint.
Service checks existing email before insert.
If duplicate, return 409 Conflict.
```

Expected response:

```json
{
  "error": "EMAIL_ALREADY_REGISTERED",
  "message": "Email is already registered"
}
```

---

## 2.2 Wrong Login Password

Scenario:

```txt
User submits wrong password.
```

Handling:

```txt
Service compares submitted password with BCrypt hash.
If invalid, return 401 Unauthorized.
```

Expected response:

```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "Email or password is incorrect"
}
```

---

## 2.3 Expired Access Token

Scenario:

```txt
User calls wallet or payment endpoint with expired access token.
```

Handling:

```txt
JWT filter rejects request.
Return 401 Unauthorized.
User must use refresh token to get new access token.
```

---

## 2.4 Invalid Refresh Token

Scenario:

```txt
User submits expired, revoked, or unknown refresh token.
```

Handling:

```txt
Auth Service stores refresh token as hash.
Service checks hash, expiry, and revoked_at.
Invalid token returns 401 Unauthorized.
```

---

# 3. Wallet Service Failure Scenarios

## 3.1 Insufficient Balance

Scenario:

```txt
Sender tries to transfer more than available balance.
```

Risk:

```txt
Balance could become negative.
```

Handling:

```txt
Wallet Service locks sender wallet row using SELECT FOR UPDATE.
Service validates balance before debit.
If insufficient, transfer is rejected.
No ledger entry is created.
No wallet balance is changed.
```

Expected response:

```json
{
  "error": "INSUFFICIENT_BALANCE",
  "message": "Wallet balance is not enough for this transfer"
}
```

---

## 3.2 Concurrent Transfer From Same Wallet

Scenario:

```txt
Two transfer requests from the same wallet arrive at the same time.
```

Risk:

```txt
Both requests may read the same balance and overspend.
```

Handling:

```txt
Wallet Service uses PostgreSQL row-level locking with SELECT FOR UPDATE.
Only one transaction can update a wallet row at a time.
The second transaction waits until the first transaction commits.
After waiting, the second transaction reads the latest balance.
```

Expected result:

```txt
Balance remains consistent.
Wallet balance does not become negative.
Ledger entries match final balance.
```

---

## 3.3 Deadlock Between Two Wallet Transfers

Scenario:

```txt
Transfer A locks wallet 1 then wallet 2.
Transfer B locks wallet 2 then wallet 1.
```

Risk:

```txt
Database deadlock.
```

Handling:

```txt
Wallet Service locks wallets in deterministic order by wallet ID.
Lower wallet ID is locked first.
Higher wallet ID is locked second.
```

Expected result:

```txt
Deadlock risk is reduced.
```

---

## 3.4 Transfer Partial Failure

Scenario:

```txt
Sender wallet is debited, but receiver wallet credit fails.
```

Risk:

```txt
Money disappears or balances become inconsistent.
```

Handling:

```txt
Debit, credit, ledger entries, and transfer record are executed inside one database transaction.
If any step fails, the whole transaction rolls back.
```

Expected result:

```txt
Either both debit and credit succeed, or neither happens.
```

---

## 3.5 Duplicate Transfer Request

Scenario:

```txt
Client retries POST /wallets/transfer with the same Idempotency-Key.
```

Risk:

```txt
Transfer may be processed twice.
```

Handling:

```txt
Wallet Service stores idempotency key, request hash, response status, and response body.
If duplicate request has same key and same payload, return stored response.
Do not execute transfer again.
```

Expected result:

```txt
Balance changes only once.
Response is identical to the first response.
```

---

## 3.6 Same Idempotency Key With Different Payload

Scenario:

```txt
Client sends same Idempotency-Key but different transfer amount or receiver.
```

Risk:

```txt
System may incorrectly treat a different operation as duplicate.
```

Handling:

```txt
Service compares request hash.
If hash is different, return 409 Conflict.
```

Expected response:

```json
{
  "error": "IDEMPOTENCY_KEY_REUSED",
  "message": "This idempotency key was already used with a different request payload"
}
```

---

## 3.7 Daily Transfer Limit Exceeded

Scenario:

```txt
User transfers more than configured daily limit.
```

Handling:

```txt
Wallet Service calculates total successful transfer amount for the sender for the current day.
If total + requested amount exceeds MAX_DAILY_TRANSFER_AMOUNT, reject request.
```

Expected response:

```json
{
  "error": "DAILY_TRANSFER_LIMIT_EXCEEDED",
  "message": "Daily transfer limit exceeded"
}
```

---

# 4. Payment Service Failure Scenarios

## 4.1 Gateway Timeout

Scenario:

```txt
Payment Service calls Mock Gateway, but gateway does not respond within 5 seconds.
```

Risk:

```txt
User does not know payment state.
```

Handling:

```txt
Payment Service marks top-up transaction as PENDING.
The transaction can later be updated by webhook if gateway sends callback.
```

Expected response:

```json
{
  "status": "PENDING",
  "message": "Top-up is pending because gateway did not respond in time"
}
```

---

## 4.2 Gateway Returns FAILED

Scenario:

```txt
Mock Gateway sends FAILED webhook.
```

Handling:

```txt
Payment Service validates HMAC.
Payment transaction is updated to FAILED.
No outbox wallet credit event is created.
Wallet balance is not changed.
```

Expected result:

```txt
Payment status is FAILED.
Wallet is not credited.
```

---

## 4.3 Gateway Sends SUCCESS Webhook

Scenario:

```txt
Mock Gateway sends SUCCESS webhook.
```

Handling:

```txt
Payment Service validates HMAC.
Payment transaction is updated to SUCCESS.
Payment Service creates outbox event CREDIT_WALLET_REQUESTED.
Outbox worker eventually credits wallet through Wallet Service.
```

Expected result:

```txt
Payment is SUCCESS.
Wallet is credited once.
```

---

## 4.4 Invalid Webhook Signature

Scenario:

```txt
Attacker or invalid client sends fake webhook.
```

Risk:

```txt
Fake payment success could credit wallet.
```

Handling:

```txt
Payment Service validates HMAC signature using gateway webhook secret.
If signature is invalid, reject request.
Do not update payment status.
Do not create outbox event.
```

Expected response:

```json
{
  "error": "INVALID_WEBHOOK_SIGNATURE",
  "message": "Webhook signature is invalid"
}
```

---

## 4.5 Duplicate Webhook

Scenario:

```txt
Gateway sends the same SUCCESS webhook more than once.
```

Risk:

```txt
Wallet may be credited multiple times.
```

Handling:

```txt
Payment Service checks current top-up transaction status.
Wallet Service internal credit is idempotent by transactionId.
Duplicate webhook does not result in duplicate wallet credit.
```

Expected result:

```txt
Wallet is credited only once.
```

---

## 4.6 Payment Service Crashes After SUCCESS Webhook

Scenario:

```txt
Payment Service receives SUCCESS webhook and crashes during processing.
```

Handling:

```txt
Payment status update and outbox insert should happen in one database transaction.
If the transaction commits, outbox worker can continue later.
If the transaction does not commit, webhook can be retried safely.
```

Expected result:

```txt
No lost wallet credit event.
No duplicate wallet credit.
```

---

## 4.7 Wallet Service Down During Wallet Credit

Scenario:

```txt
Payment Service receives SUCCESS webhook but Wallet Service is down.
```

Risk:

```txt
Payment is success but wallet is not credited.
```

Handling:

```txt
Payment Service stores CREDIT_WALLET_REQUESTED outbox event.
Outbox worker retries calling Wallet Service.
If call fails, retry_count is increased and next_retry_at is updated.
```

Expected result:

```txt
Wallet credit is eventually retried.
Wallet credit is not lost.
```

---

## 4.8 Duplicate Top-Up Request

Scenario:

```txt
Client retries POST /payments/top-up with same Idempotency-Key.
```

Handling:

```txt
Payment Service stores request hash and response.
Duplicate request with same key and same payload returns stored response.
Payment transaction is not created twice.
Gateway is not called twice.
```

Expected result:

```txt
Only one top-up transaction exists.
```

---

## 4.9 Payment Rate Limit Exceeded

Scenario:

```txt
User sends more than 5 top-up requests in one minute.
```

Handling:

```txt
Payment Service increments Redis counter per user per minute.
If count is greater than 5, return rate limit error.
```

Expected response:

```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Maximum 5 payment requests per minute allowed"
}
```

---

# 5. Mock Gateway Failure Scenarios

## 5.1 Mock Gateway Timeout Mode

Scenario:

```txt
Mock Gateway receives request with TIMEOUT mode.
```

Handling:

```txt
Gateway delays response or does not respond before 5 seconds.
Payment Service should mark transaction as PENDING.
```

---

## 5.2 Mock Gateway Cannot Call Payment Webhook

Scenario:

```txt
Mock Gateway tries to send webhook but Payment Service is down.
```

Handling:

```txt
For test scope, this can be logged.
Payment Service timeout behavior should already place transaction into PENDING.
```

Possible future improvement:

```txt
Mock Gateway could retry webhook delivery.
```

---

# 6. Infrastructure Failure Scenarios

## 6.1 Redis Down

Scenario:

```txt
Payment Service cannot connect to Redis.
```

Risk:

```txt
Rate limiting cannot be checked.
```

MVP handling:

```txt
Payment top-up returns 503 Service Unavailable because rate limit check cannot be performed safely.
```

Reason:

```txt
For payment endpoint, failing closed is safer than allowing unlimited requests.
```

---

## 6.2 PostgreSQL Down

Scenario:

```txt
Service cannot connect to its database.
```

Handling:

```txt
Request fails with 503 or 500 depending on exception handling.
No partial state is committed.
```

---

## 6.3 Service-to-Service HTTP Timeout

Scenario:

```txt
Payment Service calls Wallet Service and the call times out.
```

Handling:

```txt
If the call is from outbox worker, mark outbox event as failed attempt and retry later.
```

---

# 7. Logging and Audit Failure Handling

## 7.1 Missing TraceId

Scenario:

```txt
Client request does not contain X-Trace-Id.
```

Handling:

```txt
Service generates new traceId.
TraceId is added to MDC logs.
WebClient forwards traceId to downstream services.
```

---

## 7.2 Activity Log Insert Fails

Scenario:

```txt
Business operation succeeds but activity log insert fails.
```

MVP handling:

```txt
For money movement operations, activity log insert is part of the transaction.
If activity log insert fails, transaction rolls back.
```

Reason:

```txt
The technical test requires auditability, so transaction audit should be consistent with business operation.
```

---

# 8. Summary

Critical protections:

```txt
Wallet consistency:
- SELECT FOR UPDATE
- Database transaction
- Deterministic wallet lock order

Duplicate request safety:
- Idempotency-Key
- Request hash
- Stored response

Payment reliability:
- HMAC validation
- PENDING status on gateway timeout
- Outbox retry for wallet credit

Operational traceability:
- X-Trace-Id
- MDC logging
- Activity logs
```

