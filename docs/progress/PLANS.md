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

### PLAN-002 — Auth/account React journey
- Goal: FR-001/002 in the browser without broadening exam scope.
- Scope: login, profile, Admin create/list/status, forbidden routes, memory-only
  bearer token and logout/401. React/Vite, API proxy, component checks and real
  browser against an isolated fixture; no public hosting.
- Verification: 12 Vitest tests, build, audit, 27 backend regressions PASS. Live
  browser results and fixed mobile/401 defects are recorded in UI_JOURNEY.
- Status: COMPLETE on 2026-11-20. Bootstrap's previous NOT_RUN limitation was
  resolved for disposable standalone fixture use; persistent Compose remains NOT_RUN.

### PLAN-003 — Taxonomy API vertical slice
- Goal: deliver FR-003 and TC-008 with database-backed category/subject routes.
- Scope: admin create category/subject; admin/instructor paginated reads; normalized
  uniqueness, parent FK, role checks and structured conflicts. No taxonomy update/delete.
- Verification: `mvn -f backend/pom.xml -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE -B verify`; unit tests PASS, integration tests require the Docker Linux Engine. Focused PostgreSQL/HTTP tests are required before QM-009 can be marked DONE.
- Status: BLOCKED on 2026-11-25; migration, repository, service, controller, route security and integration scenarios added. The Testcontainers run cannot connect to Docker Desktop's Linux Engine pipe; acceptance evidence is incomplete.
