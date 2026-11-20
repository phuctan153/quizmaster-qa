# Test Strategy — Planning Draft

> Not an executed test plan. Codex owns ongoing updates after features and contracts are agreed.

Initial traceability: `SCENARIOS.md` contains 36 scenarios; TC-001..007 PASS on
2026-11-20 with executable auth integration evidence; TC-035 PASS with QM-008
browser/component evidence; TC-008..034 and TC-036 NOT_RUN. Run
`python tools/check_docs.py` from repository root to check requirement/route/scenario
references, language and import layout. This check is not application test evidence.

## Priorities

1. API automation (~35% emphasis): contract, status/error handling, RBAC, negative and boundary tests, REST Assured + TestNG.
2. Database and business logic (~25%): PostgreSQL constraints, transactional submission, score correctness, retries, immutability, cross-user isolation.
3. Manual test design and exploratory testing (~20%): risk-based scenarios, bug reports and actual evidence.
4. UI E2E (~10%): only critical user journeys, Selenium + Java + TestNG.
5. CI/reporting (~10%): repeatability, actual command/result logging, GitHub Actions.

These percentages are priorities, not a strict allocation of test counts.

## Initial high-risk journeys

- Authentication and ownership-based access.
- Publish/version immutability.
- Student assignment and atomic attempt allocation (1–3).
- Start, autosave, resume, deadline edge and auto-finalize.
- Concurrent submit and duplicate request handling.
- Server-side score and immediate result disclosure (not necessarily answer-key disclosure).
- Database integrity and rollback under failures.
- Audit/notification correctness, data isolation.

## Test design techniques

Equivalence partitioning, boundary value analysis, decision tables, state transition testing, negative testing, idempotency/concurrency, permission matrices, integration verification and selected exploratory sessions.

## Testability requirements

- Isolated environment and deterministic seed/reset operations.
- Test users for each role and ownership boundary; no real private data.
- Test-controlled backend clock or bounded deterministic time tests.
- Stable resource IDs scoped to tests and reliable cleanup; tests independent and rerunnable.
- Logged commands, environment/build versions and report locations for every recorded execution.

## Evidence policy

Record `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN` exactly. Never claim a test executed from a planned or generated test case. Distinguish intentionally injected bugs from discoveries. Store small useful evidence or link stable CI reports; don't commit large transient outputs or secrets.

## Planned targets, not quotas

~100–150 API scenarios, ~60–100 automated API tests, ~20–30 DB validations, ~25–40 business logic scenarios, ~8–12 UI E2E journeys. Overlap is expected. Increase or decrease as risks and evidence justify.
