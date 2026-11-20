# QuizMaster QA — Living Task Board

> Maintained autonomously by Codex after each meaningful task change. `DONE` requires evidence, not merely generated files.

## Task lifecycle

`BACKLOG → READY → IN_PROGRESS → VERIFYING → DONE`; use `BLOCKED` with reason and unblock condition. Only one primary `IN_PROGRESS` task at a time unless parallel tasks are genuinely independent. Split tasks that are too large to verify in one focused unit. Update this file without waiting for human prompts.

## Current status

- Phase: **03 — First backend slice (design baseline completed)**
- Current task: **QM-009 (READY)** — taxonomy API vertical slice
- Last verified task: **QM-008**
- Approved product decisions: **DEC-01..DEC-08, DEC-10**
- Implementation status: **Auth/account API and React UI verified; examination modules and CI pending**

| ID | Task | Priority | Status | Dependencies | Acceptance / evidence |
|---|---|---|---|---|---|
| QM-001 | Initialize and review repo documentation | P0 | DONE | None | 12 imported files hash-verified and readable; baseline cross-reviewed; see worklog |
| QM-002 | Detail SRS and acceptance criteria | P0 | DONE | QM-001 | SRS: 14 unique FRs covering BR-01..10; assumptions and negative boundaries reviewed |
| QM-003 | Define RBAC, lifecycles and concurrency invariants | P0 | DONE | QM-002 | ACCESS_AND_STATES: role/owner matrix, denied transitions, seven race invariants reviewed against SRS |
| QM-004 | Design schema, seed/reset and time testability | P0 | DONE | QM-002, QM-003 | ARCHITECTURE_AND_DATA: logical ERD, constraints, disposable seed/reset and clock/expiry plan reviewed; runtime DB checks NOT_RUN |
| QM-005 | Define API contracts and error model | P0 | DONE | QM-002, QM-003 | API_CONTRACT: 35 unique routes, DTOs, statuses, errors, ownership and auth contract; static checks PASS |
| QM-006 | QA test strategy and initial scenario catalogue | P0 | DONE | QM-002, QM-005 | 36 NOT_RUN scenarios map all 14 FRs; tools/check_docs.py PASS; no execution coverage claimed |
| QM-007 | Backend foundation and auth vertical slice | P1 | DONE | QM-003, QM-004, QM-005 | Executable Spring Boot app, migration, 3 unit + 24 real HTTP/PostgreSQL tests PASS; see worklog and LOCAL_SETUP |
| QM-008 | Frontend foundation and first user journey | P1 | DONE | QM-007 | React auth/Admin UI; 12 UI/client tests PASS; real browser/API/DB journey PASS; 27 backend regression tests PASS; build/audit PASS |
| QM-009 | Categories and subjects API vertical slice | P1 | READY | QM-006, QM-007 | Four contracted taxonomy routes, migration/constraints, RBAC/validation/duplicate/parent tests against PostgreSQL; docs and regression evidence |
| QM-010+ | CI, automation hardening and portfolio delivery | P1 | BACKLOG | Working slices | Stable checks, reproducible setup, honest reporting and README |

### QM-008 — Verified first UI slice
- Priority: P1; dependencies: QM-007.
- Goal: expose the verified auth/account journey through a small React UI.
- Acceptance: login + identity for all roles; Admin account creation/status/list;
  denied-role routes; token clearing on logout/401; API proxy keeps bearer calls
  same-origin; no credential persistence or logging. UI has no exam placeholders
  presented as working features.
- Validation: build, component/access tests and real browser-to-backend journey;
  keep `mvn -f backend/pom.xml verify` and docs checks passing.
- Evidence: 12 Vitest tests, real browser checks, 27 backend regressions, Vite build
  and npm audit PASS; see worklog and UI_JOURNEY for actual execution.
- Next: QM-009 taxonomy API, then question-bank slice; examination UI remains planned.

### QM-009 — Next executable slice
- Priority: P1; dependencies: QM-006, QM-007; FR-003, TC-008.
- Goal: Admin creates categories/subjects; Admin and Instructor read paginated lists.
- Acceptance: trim names; reject blanks/overlong/normalized duplicates; enforce
  parent FK; reject Student access and non-admin writes; unknown parent 404;
  duplicate 409; strict JSON; no duplicate rows under concurrent creation.
- Validation: real PostgreSQL migration/HTTP/DB tests; existing backend tests,
  frontend suite, docs check and clean diff remain green.
- Evidence: NOT_RUN; implementation has not started.

## Codex task creation rules

1. Refine/split this roadmap independently as needed; assign the next unused `QM-###` ID without collisions. The `QM-009+` and `QM-010+` entries are roadmap placeholders, not allocated unique task IDs.
2. For each new task record objective, dependencies, acceptance criteria, expected validation and priority.
3. Promote dependent tasks to `READY` when prerequisites are genuinely satisfied; don't equate a drafted doc with approved product policy.
4. Scope changes affecting an approved decision require a `PENDING` decision and human approval.
5. Mark a task `DONE` only after inspection/verification, note the commands/results in `docs/progress/WORKLOG.md` and optionally reference commit hash.
6. If tooling is unavailable, record `NOT_RUN` checks and create a follow-up task instead of claiming success.

## Task update template

```md
### QM-XXX — Short title
- Status: READY | IN_PROGRESS | VERIFYING | DONE | BLOCKED
- Priority: P0 | P1 | P2
- Dependencies: ...
- Goal: ...
- Acceptance criteria: ...
- Verification: command + observed result, or NOT_RUN with reason
- Evidence: file path / report / commit, or none yet
- Next: ...
```
