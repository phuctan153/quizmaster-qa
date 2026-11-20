# Development and QA Worklog

> Codex maintains this file autonomously. Entries reflect **observed work**, not intended work. Append a succinct entry at each meaningful milestone or before session exit. Preserve past history; correct errors with an explicit note.

## Bootstrap — starter documents prepared

- Repository operating instructions, scope, requirements, decision log, test strategy and initial task board were authored as **starter material**.
- No application source code or automation execution is included.
- No successful tests or Git commits are claimed.
- Next action for Codex: inspect actual workspace and Git status; reconcile/merge starter files without destroying existing work; begin QM-001 and verify documentation consistency.

## Entry template

### YYYY-MM-DD — QM-XXX — status
- Actual changes:
- Files changed:
- Verification commands and observed results (PASS / FAIL / NOT_RUN / BLOCKED):
- Defects / risks / decisions pending:
- Git commit(s), if made:
- Next 1–3 concrete actions:
- Resume/build/test commands:

### 2026-11-20 — QM-001 — DONE
- Inspected the empty workspace and ZIP entry list: 12 files rooted directly in the archive, no nested starter folder. Initial `git status --short` reported no Git repository.
- Expanded into `.import-staging`, read every file, copied without collisions, compared SHA-256 hashes for all 12 destination files and confirmed each file was readable: PASS (12/12). Only after verification, removed the resolved in-workspace staging directory and ZIP. The attachment outside the workspace was not changed.
- Initialized Git using existing configured identity; reviewed scope, BR/rules, decisions, strategy and operating contract. No contradictory approved business decisions found. Endpoint count is an estimate; proposed policies are not approvals.
- Converted the starter's Vietnamese onboarding instructions to English to comply with the user's language policy.
- Product Owner explicitly approved DEC-06/07/08/10 in this session. Updated decision log, rules, scope and standing contract; DEC-09 remains PENDING for technical design.
- Validation: hash/read checks PASS; application/API/DB/UI tests NOT_RUN (no source yet). Repository documentation is now the canonical source.
- Git: initialization commit prepared after diff review; see `git log --oneline` for its actual hash.
- Next: QM-002 acceptance criteria, QM-003 authorization/state model, then schema/API specifications. Resume with `git status --short` and read `TASKS.md`.

### 2026-11-20 — QM-002 — DONE
- Added SRS covering all ten BRs, 14 functional requirements, negative boundaries,
  score examples, account access, CSV atomicity, privacy and explicit reversible
  defaults. No product proposal was silently promoted to APPROVED.
- Verification: Python assertion PASS for 14 unique FR IDs and coverage of BR-01..10;
  import artifacts absent PASS; `git diff --check` PASS after removing three inherited
  trailing-space lines. The initial commit's whitespace check did report these
  lines; this entry records their correction rather than claiming it was clean.
- Added `.gitattributes` to keep source/document line endings predictable.
- Application tests NOT_RUN: no implementation. Initial baseline commit: `690a8b8`.
- Git: coherent requirements commit prepared; actual hash available in Git history.
- Next: QM-003 RBAC/state/concurrency, then QM-004/005 schema/API. Resume:
  `git status --short`; read SRS and the current task board.

### 2026-11-20 — QM-003 — DONE
- Added role/ownership matrix, account/exam/attempt transitions, prohibited paths,
  repeated-start/submit behavior and lock ordering. Reviewed against FR-001..014
  and DEC-01..08/10; no widening of role privileges or answer-key disclosure.
- Defined stale-save conflict handling and commit-before-error behavior for expiry
  so returning a late-save error cannot accidentally roll back finalization.
- Verification: design review PASS; `git diff --check` PASS. Runtime race, DB and
  permission tests NOT_RUN (design-only). QM-002 commit: `7d512b4`.
- Tool survey: Java 21.0.4, Maven 3.9.9, Node 22.20.0, Python 3.13.8 available.
  Docker client 29.5.3 exists but Docker Desktop Linux engine pipe is absent;
  database/container integration is currently BLOCKED until engine is running.
  Maven version printed an additional Access denied line; investigate during build.
- Next: schema/seed/clock design, API inventory, scenario traceability. Resume with
  `git status --short`; inspect Docker engine with `docker version` before DB tests.

### 2026-11-20 — QM-004 — DONE
- Added logical ERD/table constraints, lock/index backstops, durable outbox,
  immutable snapshot plan and isolated seed/reset/clock strategy. Checked primary
  PostgreSQL 18 and Spring Boot references linked in the design. DEC-09 remains
  PENDING; selected technical mechanics do not claim a human-approved timing SLA.
- Verification: design cross-review PASS against FR-006..014 and state invariants;
  `git diff --check` PASS. SQL migrations/DB tests NOT_RUN (design deliverable only).
- QM-003 commit: `bd4ec66`. Git: schema-design commit prepared after diff review.
- Next: API contract and risk-based scenario catalogue; investigate Docker startup
  and dependency resolution before claiming backend readiness.

### 2026-11-20 — QM-005 — DONE
- Defined 35 v1 routes, request/response schemas, role/owner boundaries, error
  codes, pagination, CSV format and auth/JWT policy. Deliberately omitted speculative
  routes rather than filling the original endpoint estimate.
- Verification: Python route uniqueness/count PASS (35); explicit FR coverage PASS
  after expanding shorthand FR references detected by the first assertion run.
  Static contract review and `git diff --check` PASS. API tests NOT_RUN (no server).
- Attempted background Docker Desktop startup using installed executable, then
  checked `docker version`: engine pipe still absent. No database test pass claimed.
- QM-004 commit: `12fea5a`. Next: scenario catalogue and documentation integrity
  check, then first auth slice. Resume: `git status --short`, `docker version`.
