# Project Scope — V1.0 Baseline

**Project:** QuizMaster QA
**System under test:** Online Examination Management System
**Primary objective:** A strong QA / API automation portfolio with a deliberately moderate AUT development burden.
**Status:** Scope baseline established; detailed contract, schemas and implementation not approved/completed.

## Stakeholders and roles

- **Product Owner (human):** defines goals, reviews product, approves material changes.
- **Codex (agent):** owns implementation, tests, engineering documentation, task tracking and verification within baseline.
- **Admin:** administers users and system-level functions.
- **Instructor:** manages owned questions/exams/assignments and authorized results.
- **Student:** receives assignments, takes exams and views authorized own results.

## Ten modules and provisional endpoint budget

| Module | Target endpoints |
|---|---:|
| Authentication | 5 |
| User Management | 5 |
| Categories & Subjects | 4 |
| Question Bank | 7 |
| Exam Management | 7 |
| Exam Assignment | 4 |
| Exam Taking | 5 |
| Scoring & Results | 5 |
| Notifications | 3 |
| Audit Logs | 3 |
| **Total** | **48** |

These counts are not an API specification and may be adjusted with rationale. Do not add endpoints merely to reach 48.

## In scope

- Basic account and role-based access management; question bank including CSV import.
- Exam draft, publish, close, assignment, versioning and configured pass threshold/time/attempt limit.
- Fixed question set, optionally shuffled display, single-correct-answer questions.
- Start/resume/save answers/submit/auto-expire, server-side scoring and immediate score display.
- Own results and history; basic internal notifications and audit events.
- Minimal functional UI for key E2E flows, testable backend/DB, seed and test-data reset.
- Manual/API/database/integration/authorization/negative/boundary/state/concurrency/UI E2E automation, CI.

## Out of scope V1

Microservices, Kafka, Redis, payments, AI scoring, essay grading, video proctoring, real-time messaging infrastructure, elaborate analytics dashboards and performance at production scale.

## Approved product decisions

See `DECISION_LOG.md` for wording and approval status. DEC-01 expiration auto-finalize and score saved answers; DEC-02 attempt limit 1–3; DEC-03 score immediate after successful finalization; DEC-04 fixed questions with optional shuffle; DEC-05 published content immutable, edit via versioning.

Additional approvals on 2026-11-20: equal weights, score on a 0–100 scale rounded to two decimals HALF_UP, compare rounded score to threshold; expiration consumes quota; student views omit answer keys; one active attempt per student/exam version; repeated submission returns the same result (DEC-06/07/08/10).

## Proposed implementation details

Atomic attempt allocation, backend expiration sweep and immutable question snapshots. These are reversible technical mechanisms enforcing approved decisions; DEC-09 remains PENDING until mechanics are designed. Technical choices do not imply product approval.

## Quality goals

Expected behavior specified before tests; independent/repeatable data; backend-trusted time, auth and scoring; defect evidence real; robust CI; easy reviewer setup. Counts are ambitions, not acceptance quotas: ~100–150 API scenarios, 60–100 automated API cases, 20–30 DB validations, 25–40 business-logic scenarios, 8–12 UI E2E flows; groups overlap.

## Phases

1. Requirements and acceptance criteria.
2. Architecture, RBAC/state models, schema, API contracts and testability.
3. AUT implementation in end-to-end vertical slices.
4. Manual/API/database tests parallel with features.
5. Automation and regression hardening.
6. CI and portfolio evidence/readme polish.

## Acceptance boundary

No module is complete solely because its screens or APIs exist. Require appropriate permission, error, business rule, data consistency and regression checks, with executed evidence. Product Owner acceptance is a separate step from engineering verification.
