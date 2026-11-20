# Implementation Plans

Use this file only for multi-step or cross-module work. Codex can create, revise, execute and close plans autonomously. Keep short plans in `TASKS.md` instead.

## Plan template

### PLAN-XXX — Title
- Goal and business/test value:
- Scope and exclusions:
- Dependencies / approved decisions:
- Phases and concrete deliverables:
- Verification strategy and reproducible commands:
- Risk / rollback plan:
- Status and actual outcome:

## Active plans

### PLAN-001 — First auth/account slice
- Goal: run the monolith and verify FR-001/002 with real persistence and denied-role cases.
- Scope: login, own identity, admin create/list/activate accounts; no public signup or UI yet.
- Dependencies: QM-003/004/005 DONE; JWT and account policies in API_CONTRACT.
- Steps: pin/resolve Maven dependencies; migration/account repository; JWT/security;
  controllers/validation/errors; unit/security tests; real PostgreSQL integration;
  setup/evidence and review/commit. Optional development admin bootstrap is opt-in.
- Verification: Java 21 Maven test/verify; PostgreSQL Testcontainers for migration,
  normalized uniqueness, deactivation and API permissions; docs checker and Git diff.
- Risks: Docker engine absent; distinguish unit/mock checks from PostgreSQL evidence.
  No H2 substitution. No production data or external deployment involved.
- Status: COMPLETE on 2026-11-20. Executable auth/account slice, migration and
  3 unit + 24 HTTP/PostgreSQL integration tests PASS; documentation/config checks
  PASS. Standalone manual bootstrap remains NOT_RUN and is documented explicitly.
