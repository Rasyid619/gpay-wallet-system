# Postman End-to-End Flow

This flow verifies the local Docker Compose API surface from registration
through wallet funding, transfer, idempotency, gateway outcomes, webhook
validation, and payment rate limiting.

Import the collection first:

```text
postman/GPay.postman_collection.json
```

Default collection URLs target Docker Compose host ports:

```text
auth_url=http://localhost:8081
wallet_url=http://localhost:8082
payment_url=http://localhost:8083
gateway_url=http://localhost:8084
```

Set these collection variables before running internal or webhook requests:

```text
internal_token=change-this-wallet-internal-token
gateway_webhook_secret=change-this-gateway-secret
```

Do not store real production secrets in the collection.

## Main Wallet Flow

1. Run `E2E Flow / 01 Register User A`.
   - Expected: `201 Created`.
   - The collection stores `user_a_id`.
   - Auth Service automatically provisions a zero-balance wallet in Wallet Service.

2. Run `E2E Flow / 02 Register User B`.
   - Expected: `201 Created`.
   - The collection stores `user_b_id`.

3. Run `E2E Flow / 03 Login User A`.
   - Expected: `200 OK`.
   - The collection stores `access_token`.

4. Run `E2E Flow / 04 Login User B`.
   - Expected: `200 OK`.
   - The collection stores `user_b_access_token`.

5. Run `E2E Flow / 05 Get User A Balance`.
   - Expected: `200 OK`, `balance = 0`.
   - The collection stores `topup_wallet_id` and `user_a_wallet_id`.

6. Run `E2E Flow / 06 Get User B Balance`.
   - Expected: `200 OK`, `balance = 0`.
   - The collection stores `receiver_wallet_id`.

7. Run `E2E Flow / 07 Top-Up SUCCESS For User A`.
   - Expected: `201 Created`, response status `PENDING`.
   - The collection stores `payment_transaction_id`.
   - The request sends `gateway_mode = SUCCESS`.
   - The mock gateway sends a successful webhook and Payment Service outbox credits Wallet Service.

8. Wait a few seconds, then run `E2E Flow / 08 Check User A Balance After Top-Up`.
   - Expected: `200 OK`, `balance = 100000`.
   - If the balance is still `0`, wait and run this request again; the outbox worker is asynchronous.

9. Run `E2E Flow / 09 Transfer From User A To User B`.
   - Expected: `200 OK`, `amount = 25000`.
   - The collection stores `transfer_id`.

10. Run `E2E Flow / 10 Check Both Balances`.
    - Expected user A balance: `75000`.
    - Expected user B balance: `25000`.

11. Run `E2E Flow / 11 Check User A Mutation History`.
    - Expected: at least two items.
    - Expected entries include:
      - `CREDIT` / `TOP_UP` for `100000`
      - `DEBIT` / `TRANSFER` for `25000`

12. Run `E2E Flow / 12 Retry Same Transfer Idempotency Key`.
    - Expected: `200 OK`.
    - Expected same `transfer_id` as step 9.
    - User A balance should remain `75000`.

13. Run `E2E Flow / 13 Transfer With Insufficient Balance`.
    - Expected: `400 Bad Request`.
    - Expected error: `INSUFFICIENT_BALANCE`.

## Payment Failure And Timeout

14. Run `E2E Flow / 14 Top-Up FAILED`.
    - Expected top-up response: `201 Created`, status `PENDING`.
    - The request sends `gateway_mode = FAILED`.
    - The gateway webhook later updates the payment transaction to `FAILED`.
    - Wallet balance should not increase.

15. Run `E2E Flow / 15 Top-Up TIMEOUT`.
    - Expected response: `201 Created`, status `PENDING`.
    - The request sends `gateway_mode = TIMEOUT`.
    - No gateway webhook is sent, so the payment transaction remains `PENDING`.
    - Wallet balance should not increase.

16. Run `E2E Flow / 16 Invalid Webhook Signature`.
    - Expected: `401 Unauthorized`.
    - Expected error: `INVALID_WEBHOOK_SIGNATURE`.

## Rate Limit

17. Run `E2E Flow / 17 Register Rate Limit User`.
18. Run `E2E Flow / 18 Login Rate Limit User`.
19. Run `E2E Flow / 19 Get Rate Limit User Balance`.
20. Run `E2E Flow / 20 Top-Up Rate Limit Attempt 1`.
21. Run `E2E Flow / 21 Top-Up Rate Limit Attempt 2`.
22. Run `E2E Flow / 22 Top-Up Rate Limit Attempt 3`.
23. Run `E2E Flow / 23 Top-Up Rate Limit Attempt 4`.
24. Run `E2E Flow / 24 Top-Up Rate Limit Attempt 5`.
25. Run `E2E Flow / 25 Top-Up Rate Limit Attempt 6`.
    - Attempts 1 through 5 should return `201 Created`.
    - Attempt 6 should return `429 Too Many Requests`.
    - Expected error: `RATE_LIMIT_EXCEEDED`.

## Notes

- The collection automatically adds `X-Trace-Id` when a request does not define one.
- Empty idempotency key variables are initialized with generated local values.
- To intentionally replay a request, keep the same idempotency key variable value.
- To start a fresh run, clear the relevant idempotency key variables or re-import the collection.
- `POST /payments/top-up` returns `PENDING` immediately. Check Wallet mutations to confirm successful credits.
