# QuizMaster QA

**Online Examination Management System — QA Automation Portfolio**

> **Current status:** QM-001..005 verified on 2026-11-20: requirements, access/state/data design and a 35-route API contract. QA scenarios are next. Application code, automated tests and CI are not implemented or executed.

## Purpose

Build a deliberately manageable system under test to demonstrate deep QA competence: API automation, negative and boundary testing, business rules, database consistency, authorization, concurrency, time-based workflows, UI E2E testing and CI/CD reporting. Application development serves testing rather than portfolio feature-count inflation.

## Planned system

- Roles: Admin, Instructor, Student.
- Ten functional modules: Authentication, User Management, Categories & Subjects, Question Bank, Exam Management, Exam Assignment, Exam Taking, Scoring & Results, Notifications, Audit Logs.
- 35 designed endpoints in the initial contract; the starter's 48 was an estimate.
- Single-correct-answer multiple-choice exams with fixed question sets and optional display order shuffle.
- Published exam content immutable; edit via a new version.
- Attempt limits configurable from 1 to 3; server-enforced time limit and automatic finalize/score on expiration.
- Publish score immediately upon successful finalization.

## Planned stack

| AUT | QA / Delivery |
|---|---|
| React, Java Spring Boot, PostgreSQL, JWT | REST Assured, Java, TestNG, Postman |
| Modular monolith, Docker Compose | SQL/JDBC, Selenium WebDriver, GitHub Actions |

Reporting technology TBD after evaluation. Versions and exact libraries TBD during design.

## Autonomy workflow

Codex acts as the day-to-day development and QA agent; the human acts as Product Owner and acceptance reviewer. Read `AGENTS.md` for standing operating rules, `TASKS.md` for the board, and `docs/progress/WORKLOG.md` to resume. Codex can update Markdown and execute in-scope tasks independently. Product-changing decisions require human approval.

## Documentation

- `docs/requirements/PROJECT_SCOPE.md` — scope, modules, in/out.
- `docs/requirements/BUSINESS_REQUIREMENTS.md` — business goals and traceability.
- `docs/requirements/SRS.md` — functional acceptance criteria and explicit engineering assumptions.
- `docs/requirements/ACCESS_AND_STATES.md` — role/owner matrix, lifecycles and concurrency invariants.
- `docs/design/ARCHITECTURE_AND_DATA.md` — architecture, ER model, constraints, seed/reset and time testability.
- `docs/design/API_CONTRACT.md` — versioned routes, DTOs, errors and auth contract.
- `docs/requirements/BUSINESS_RULES.md` — approved vs proposed rules.
- `docs/requirements/DECISION_LOG.md` — approval state of product decisions.
- `docs/test-strategy/TEST_STRATEGY.md` — test priorities and approach.
- `docs/progress/WORKLOG.md` — actual work, checks and continuation.

## Progress

See `TASKS.md` for verified task status. No test pass counts, screenshots, releases or completed functionality are claimed by this starter.

## Run locally

Not yet available. Codex must add verified setup, build and test commands when implemented.
