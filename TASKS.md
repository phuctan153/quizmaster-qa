# QuizMaster QA — Living Task Board

> Maintained autonomously by Codex after each meaningful task change. `DONE` requires evidence, not merely generated files.

## Task lifecycle

`BACKLOG → READY → IN_PROGRESS → VERIFYING → DONE`; use `BLOCKED` with reason and unblock condition. Only one primary `IN_PROGRESS` task at a time unless parallel tasks are genuinely independent. Split tasks that are too large to verify in one focused unit. Update this file without waiting for human prompts.

## Current status

- Phase: **03 — First backend slice (design baseline completed)**
- Current task: **QM-015 (IN_PROGRESS)** — exam assignment API
- Last verified task: **QM-014**
- Approved product decisions: **DEC-01..DEC-08, DEC-10**
- Implementation status: **Auth/account API, React UI, taxonomy, question CRUD, atomic CSV import, exam draft/publish snapshots, close and successor versions verified; assignment API is in progress; attempts/results and CI pending**

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
| QM-009 | Categories and subjects API vertical slice | P1 | DONE | QM-006, QM-007 | Four routes and V2 migration verified against PostgreSQL 18.7; 3 unit + 26 HTTP/PostgreSQL integration tests PASS; TC-008 PASS; docs check PASS; see 2026-11-25 worklog |
| QM-010 | Instructor question-bank CRUD slice | P1 | DONE | QM-009 | Instructor-owned create/list/read/update verified; validation, one correct option, role/owner denial, revision race and migration V3; 3 unit + 28 HTTP/PostgreSQL tests PASS; see 2026-11-25 worklog |
| QM-011 | Atomic CSV question import | P1 | DONE | QM-010 | UTF-8/multiline CSV; exact headers; row/file limits; row errors; atomic import; 6 unit + 30 HTTP/PostgreSQL tests PASS, including 500-row and exact 1 MiB boundaries; see worklog |
| QM-012 | Exam draft and publish lifecycle | P1 | DONE | QM-010, QM-011 | Instructor-owned draft CRUD, strict settings/ownership/revision checks, atomic immutable question+option snapshots; 6 unit + 33 HTTP/PostgreSQL tests PASS; TC-013..015 PASS; see worklog |
| QM-013 | Close published exams | P1 | DONE | QM-012 | Owner-only close; draft rejected; repeat/concurrent close stable with one revision increment; role/foreign denials; 6 unit + 34 HTTP/PostgreSQL tests PASS; TC-016 PASS |
| QM-014 | Successor exam versions | P1 | DONE | QM-012, QM-013 | Published/closed source copies to editable draft with immutable snapshot/settings; owner denial; source bank edits isolated; concurrent version numbers unique; 6 unit + 35 HTTP/PostgreSQL tests PASS |
| QM-015 | Exam assignment API | P1 | IN_PROGRESS | QM-014 | Instructor assigns owned published exam to active student; duplicate assignment idempotent; instructor roster and student own-assignment listing; PostgreSQL/RBAC evidence |
| QM-016+ | Exam attempts, scoring/results, notifications and CI | P1 | BACKLOG | QM-015 | Split remaining functional and delivery slices into concrete tasks |

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

### QM-009 — Taxonomy API vertical slice — DONE
- Priority: P1; dependencies: QM-006, QM-007; FR-003, TC-008.
- Goal: Admin creates categories/subjects; Admin and Instructor read paginated lists.
- Acceptance: trim names; reject blanks/overlong/normalized duplicates; enforce
  parent FK; reject Student access and non-admin writes; unknown parent 404;
  duplicate 409; strict JSON; no duplicate rows under concurrent creation.
- Validation: `mvn -f backend/pom.xml -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE -B verify` → BUILD SUCCESS; 3 unit + 26 HTTP/PostgreSQL integration tests PASS; Flyway applied V1 and V2 on PostgreSQL 18.7. `python tools/check_docs.py` PASS; `git diff --check` PASS.
- Evidence: `IdentityIT.taxonomyEnforcesRolesNormalizationParentAndPagination`; `IdentityIT.concurrentNormalizedCategoryCreationCommitsExactlyOneRow`; generated Failsafe XML under `backend/target/failsafe-reports`; TC-008 evidence in `docs/test-strategy/SCENARIOS.md` and worklog.
- Next: QM-010 instructor-owned question-bank CRUD (completed below).

### QM-010 — Instructor question-bank CRUD slice
- Priority: P1; dependency: QM-009; FR-004, TC-009, TC-010.
- Goal: enable instructors to create and manage their own single-correct-answer questions.
- Acceptance: create/list/read/update own questions; foreign question access returns 404; subject must exist; validate prompt/options, unique labels/text and exactly one correct option; update requires current revision and increments it atomically; simultaneous stale updates yield one success and one 409; deny Admin/Student question access.
- Validation: Java unit and real PostgreSQL/HTTP integration tests, docs checker and clean diff. Update contract/spec before coding if a decision-level ambiguity is discovered; do not include CSV import (separate task).
- Evidence: migration V3 and four question routes; verification below.
- Verification: Maven `verify` BUILD SUCCESS, 6 unit + 30 HTTP/PostgreSQL integration tests PASS; real PostgreSQL 18.7; Flyway V1–V3 PASS; includes ownership, role denial, required/optional fields, option count/correctness/uniqueness, unknown field, and concurrent optimistic update.
- Next: QM-011 atomic CSV import (completed below).

### QM-011 — Atomic CSV question import
- Priority: P1; dependencies: QM-010; FR-005, TC-011, TC-012.
- Goal: import question rows through the contracted multipart endpoint without partial persistence.
- Acceptance: UTF-8, exact header, quoted CSV parsing, 1 MiB and 500-row limits, subject and correct-label validation, useful row/field errors, one transaction for the whole import; no partial rows on any invalid row.
- Validation: unit/parser boundary tests plus real PostgreSQL/HTTP integration for success, quoting, malformed rows, payload/row limits, and rollback; docs checker and clean diff.
- Evidence: migration V3 and multipart import route; verification below.
- Verification: Maven `verify` BUILD SUCCESS; 6 unit + 30 HTTP/PostgreSQL integration tests PASS against PostgreSQL 18.7. Covered quoted comma/newline parsing, strict UTF-8, header/row errors, no partial persistence on invalid batch, 500 accepted/501 rejected, exactly 1 MiB accepted/1 MiB+1 rejected (413), docs and whitespace checks PASS.
- Next: QM-012 exam draft and publish lifecycle.

### QM-012 — Exam draft and publish lifecycle — DONE
- Priority: P1; dependencies: QM-010, QM-011; FR-006/007, TC-014..016.
- Goal: let an Instructor configure an owned exam draft and publish an immutable question snapshot.
- Acceptance: instructor-only own exam create/read/update/publish; validate duration, attempt limit, pass threshold, unique nonempty question IDs and question ownership; stale revision 409; invalid publish leaves draft unchanged; published question content is isolated from later bank edits.
- Scope: draft CRUD + publish snapshot. Close and successor version creation stay a follow-up task; no assignment/attempt endpoints.
- Verification: Java tests plus real PostgreSQL/HTTP integration for RBAC, owner IDOR, validation, transaction rollback, revision race and snapshot immutability.
- Evidence: V4 schema, routes, `IdentityIT.examDraftPublishesOwnedImmutableQuestionSnapshot`, `IdentityIT.examDraftConcurrentUpdateHasOneRevisionWinner` and `IdentityIT.invalidPublishRollsBackStateAndSnapshot`; TC-013..015 mappings.
- Verification: `mvn -f backend/pom.xml -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE -B verify` → BUILD SUCCESS; 6 unit + 33 HTTP/PostgreSQL integration tests PASS on PostgreSQL 18.7, zero failures/errors/skips. Validation includes exact integer attempt limits and immutable question/option content after source edits.
- Next: QM-013 owner-only close transition.

### QM-013 — Close published exams — DONE
- Priority: P1; dependency: QM-012; FR-007, TC-016.
- Goal: stop starts on an owned published exam while preserving snapshots and existing records.
- Acceptance: Instructor closes own published exam; foreign exam 404; draft close 409; repeated close is stable/idempotent; no snapshot/settings mutation; state/revision reflected consistently.
- Scope: close endpoint only. Successor version cloning is QM-014 because copying a published snapshot into an editable draft needs an explicit technical contract.
- Verification: real PostgreSQL/HTTP tests for role/owner boundaries, allowed/denied states, repeated request, and close/update concurrency.
- Evidence: `ExamService.close`; `IdentityIT.closeRequiresPublishedOwnedExamAndIsIdempotentUnderConcurrency`; contract and TC-016 mapping.
- Verification: Maven `verify` BUILD SUCCESS; 6 unit + 34 HTTP/PostgreSQL integration tests PASS on PostgreSQL 18.7; concurrent close returns CLOSED at revision 2 exactly once.
- Next: QM-014 successor exam versions.

### QM-014 — Successor exam versions
- Priority: P1; dependency: QM-012; FR-007, TC-015/016.
- Goal: create a new draft version without altering its published source.
- Technical approach: copy the published question/option snapshot and settings into the new draft so later bank edits cannot silently change the successor's starting content. This implements the existing SRS version-copy rule and does not change approved product policy.
- Verification: PostgreSQL/HTTP checks for source unchanged, copied prompt/options/answer key/settings, owner denial, draft-source rejection and concurrent version numbering.
- Evidence: V4 snapshot tables reused; `ExamService.createSuccessor`; `IdentityIT.successorVersionCopiesPublishedSnapshotAndAllocatesUniqueNumbersConcurrently`; latest suite 6 unit + 35 integration PASS.
- Next: QM-015 instructor assignment and roster/list API.

### QM-015 — Exam assignment API
- Status: IN_PROGRESS
- Priority: P1; dependencies: QM-014; FR-008.
- Goal: assign an owned published exam version to an active Student and expose scoped assignment lists.
- Acceptance criteria: unique assignment per exam version/student; repeat assignment returns the existing record; Instructor roster is owner-scoped; Student list contains only the caller's assignments; reject inactive/non-Student accounts and non-published exams; enforce role/ownership and pagination.
- Verification: real PostgreSQL/HTTP tests for idempotency, list filtering, role/owner boundaries, inactive accounts and invalid exam states; docs checker and diff review.
- Evidence: API contract routes `PUT /exams/{id}/assignments/{studentId}`, `GET /exams/{id}/assignments`, and `GET /assignments`; execution pending.
- Next: add assignment persistence and endpoints, then exercise RBAC, idempotency and leakage boundaries.

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
