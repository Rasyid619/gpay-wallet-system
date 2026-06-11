# Contributing

Thanks for contributing to GPay Wallet System. This document describes how
changes get into `main`.

## Branching & merging

- `main` is **protected**. Direct pushes are not allowed; all changes land through
  a pull request.
- Every pull request requires **review and approval from the code owner**
  (see [`.github/CODEOWNERS`](.github/CODEOWNERS)) before it can be merged.
- Stale approvals are dismissed when new commits are pushed, and open review
  conversations must be resolved before merge.
- Branch from the latest `main` using `rasyid-<issue-number>-<short-kebab-title>`,
  e.g. `rasyid-47-refactor-trace-id-infra`.

## Commits & pull requests

- Use [Conventional Commits](https://www.conventionalcommits.org/) for commit
  messages (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:` …).
- Keep each pull request scoped to a single issue; link it with `Closes #<issue>`.
- Fill out the pull request template and describe what changed, how it was tested,
  and any remaining risk.

## Engineering conventions

- Java 21, Spring Boot 3+, Gradle Wrapper. Flyway owns schema migrations.
- Services are isolated by database — a service never reads or writes another
  service's database; cross-service calls go through HTTP APIs.
- Keep controllers thin, business logic in services, repositories limited to data
  access, and expose DTOs rather than JPA entities.
- Money is whole IDR (`BIGINT` in PostgreSQL / `Long` in Java); public JSON fields
  use `snake_case`; mutating money endpoints require an `Idempotency-Key`.
- Secrets come from environment variables only — never commit real secrets.
- Document API changes in `openapi.yml` and update the Postman collection.

## Before opening a pull request

- Run the affected service's tests with the Gradle wrapper (`./gradlew test`) and
  make sure the build passes with no formatting/lint violations.
- See `README.md` for the exact build and run commands.
