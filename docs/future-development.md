# Future Development Considerations

This document captures practical follow-up work that is intentionally outside
the current v1 scope. It should be treated as a backlog guide, not as behavior
that is already implemented.

## API And Product Scope

- Add `GET /payments/{id}` only if the product needs customer-visible payment
  lookup. The architecture notes mention it, but the current implemented
  Payment Service API exposes top-up creation and gateway webhook handling only.
- Add admin or support APIs separately from customer APIs so wallet ownership
  and authorization rules remain clear.
- Keep public API fields in `snake_case` and continue documenting changes in
  `openapi.yml` and the Postman collection.

## Operational Reliability

- Add health checks per service, including database and Redis readiness where
  relevant.
- Add structured log shipping and dashboards for transaction status, webhook
  outcomes, outbox retry counts, and rate-limit rejections.
- Add alerting for stuck payment outbox events and repeated wallet-credit
  delivery failures.
- Add request timeout, retry, and circuit breaker policies consistently for
  service-to-service HTTP calls.

## Data And Isolation

- Move from one local PostgreSQL container with multiple databases to separate
  production PostgreSQL instances if operational isolation is required.
- Use distinct production database users with the minimum required permissions
  per service.
- Add backup, restore, and migration rollback procedures for each service-owned
  database.
- Keep money as whole-IDR `BIGINT` unless product scope expands to decimal or
  multi-currency support.

## Security

- Replace local placeholder secrets with a managed secret store in deployed
  environments.
- Rotate JWT, internal service, and gateway webhook secrets with a documented
  migration plan.
- Add token revocation and session management requirements if the auth model
  grows beyond the current assessment scope.
- Review webhook timestamp replay protection if the mock gateway flow becomes
  production-facing.

## Testing

- Add end-to-end tests that run against Docker Compose for register, login,
  top-up, webhook, outbox credit, and wallet mutation verification.
- Add Newman or Postman CLI checks for the maintained collection.
- Keep focused concurrency tests for wallet transfers and idempotency behavior.
- Add contract tests around service-to-service HTTP clients before changing
  payload shapes.

## Maintainability

- Watch for repeated idempotency, trace-id, error-response, and HMAC helpers
  across services. Extract shared libraries only after duplication becomes
  stable and the ownership boundary is clear.
- Keep controllers thin, services responsible for business workflows, and
  repositories limited to persistence.
- Avoid broad refactors while adding assessment features; prefer small issues
  with clear acceptance criteria.
