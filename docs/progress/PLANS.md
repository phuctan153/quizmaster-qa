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
- Verification: `mvn -f backend/pom.xml -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE -B verify` → BUILD SUCCESS; 3 unit + 26 PostgreSQL/HTTP integration tests PASS, PostgreSQL 18.7; docs and whitespace checks PASS.
- Status: COMPLETE on 2026-11-25; all four routes, migration, authorization, constraints, duplicate and concurrency behavior verified. Initial Docker access issue was resolved by running the check with Docker permissions.

### PLAN-004 — Instructor question-bank CRUD slice
- Goal: implement and verify the FR-004 core question workflow before CSV import.
- Scope: Instructor-owned create/list/read/update endpoints, schema, validation,
  subject FK and optimistic revision checks. Excludes CSV import and exam snapshots.
- Dependencies / approved decisions: QM-009; RULE-QUE-001 and RULE-QUE-004; no
  new business decisions required by the existing SRS/API contract.
- Phases: question schema and DTOs; transactional persistence/service; ownership
  and role controls; API validation and revision conflict tests; docs/evidence.
- Verification: Maven unit + PostgreSQL Testcontainers HTTP suite, doc checker,
  diff review. Include two-instructor IDOR and stale concurrent update coverage.
- Risk / rollback: additive V3 migration only; no existing production data.
- Status: COMPLETE on 2026-11-25; migration V3 and question routes verified by 3 unit and 28 HTTP/PostgreSQL integration tests, including concurrent revision checks.

### PLAN-005 — Atomic CSV question import
- Goal: implement FR-005 / TC-011 / TC-012 using the approved SRS limits and existing API contract.
- Scope: multipart upload parsing, field/row validation, limits and transactional import; no question-bank UI.
- Dependencies: QM-010 complete. UTF-8, exact columns, 1 MiB and 500-row limits are already documented in SRS/API_CONTRACT.
- Verification: parser tests and real PostgreSQL/HTTP tests for correct quoting, malformed/oversized imports and whole-batch rollback; documentation checker and diff review.
- Risk / rollback: one additive endpoint; transaction rollback keeps invalid batches out of existing question rows.
- Status: COMPLETE on 2026-11-25; 6 unit + 30 HTTP/PostgreSQL integration tests PASS, including whole-file validation, transactional insertion, quoting, row errors and file/row boundaries.

### PLAN-006 — Exam draft and publish lifecycle
- Goal: implement FR-006/007 core draft and immutable publication behavior.
- Scope: Instructor-owned draft create/read/update and atomic publish snapshot; defer close/version successor, assignments and attempts.
- Dependencies / approved decisions: QM-010 and QM-011; DEC-04/05 and RULE-EXAM-003/004. Preserve fixed question membership and immutable published content.
- Verification: PostgreSQL integration and HTTP tests for role/owner access, configuration boundaries, question ownership, revision races, invalid publish rollback and snapshot isolation.
- Risk / rollback: additive migration, no existing deployment data; use immutable snapshot rows instead of mutable question joins during exam-taking.
- Status: COMPLETE on 2026-11-25; 6 unit + 33 HTTP/PostgreSQL integration tests PASS, including integer boundaries, revision races, invalid-publish rollback and snapshot isolation after source edits.

### PLAN-007 — Close published exams
- Goal: implement FR-007 transition to stop future starts on an owned published exam.
- Scope: owner-only PUBLISHED→CLOSED, draft rejection, idempotent repeat close; successor versions excluded.
- Dependencies / approved decisions: QM-012, DEC-04/05 and current state model.
- Verification: real PostgreSQL and HTTP tests for role/owner denial, legal/illegal state, repeat close and transaction/concurrency behavior.
- Risk / rollback: additive state transition only; retain existing snapshots and exam data.
- Status: COMPLETE on 2026-11-25; 6 unit + 34 HTTP/PostgreSQL integration tests PASS, including concurrent idempotent close and denied state transitions.

### PLAN-008 — Successor exam versions
- Goal: implement FR-007 new-version creation from a published or closed source.
- Scope: immutable source version, incremented family version number, copied settings and question/option snapshot as the new editable draft's starting content.
- Dependencies / approved decisions: QM-012 and QM-013; DEC-04/05, RULE-EXAM-003/004. This is a reversible technical interpretation of existing SRS version-copy behavior.
- Verification: real PostgreSQL/HTTP checks for source unchanged, copied prompt/options/answer key/settings, owner denial, version-number uniqueness and concurrent duplicate version requests.
- Risk / rollback: additive V5 migration; no data rewrite. Draft representation must keep copied snapshot content independent of subsequent bank edits.
- Status: IN_PROGRESS on 2026-11-25.
