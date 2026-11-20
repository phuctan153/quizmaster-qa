# QuizMaster QA

**Online Examination Management System — QA Automation Portfolio**

> **Current status:** QM-001..007 verified on 2026-11-20. First auth/account backend slice implemented: 3 unit + 24 HTTP/PostgreSQL integration tests PASS (no skips). Exam modules, frontend and CI remain unimplemented.

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

Backend: Java 21, Spring Boot 3.5.15, Flyway 11.20.3 and PostgreSQL 18.
Application tests use JUnit 5 and Testcontainers; portfolio API/UI TestNG frameworks
and reporting technology remain planned.

## Autonomy workflow

Codex acts as the day-to-day development and QA agent; the human acts as Product Owner and acceptance reviewer. Read `AGENTS.md` for standing operating rules, `TASKS.md` for the board, and `docs/progress/WORKLOG.md` to resume. Codex can update Markdown and execute in-scope tasks independently. Product-changing decisions require human approval.

## Documentation

- `docs/requirements/PROJECT_SCOPE.md` — scope, modules, in/out.
- `docs/requirements/BUSINESS_REQUIREMENTS.md` — business goals and traceability.
- `docs/requirements/SRS.md` — functional acceptance criteria and explicit engineering assumptions.
- `docs/requirements/ACCESS_AND_STATES.md` — role/owner matrix, lifecycles and concurrency invariants.
- `docs/design/ARCHITECTURE_AND_DATA.md` — architecture, ER model, constraints, seed/reset and time testability.
- `docs/design/API_CONTRACT.md` — versioned routes, DTOs, errors and auth contract.
- `docs/design/LOCAL_SETUP.md` — prerequisites, isolated tests, local database and opt-in admin bootstrap.
- `docs/requirements/BUSINESS_RULES.md` — approved vs proposed rules.
- `docs/requirements/DECISION_LOG.md` — approval state of product decisions.
- `docs/test-strategy/TEST_STRATEGY.md` — test priorities and approach.
- `docs/test-strategy/SCENARIOS.md` — 36 risk-based scenarios: TC-001..007 PASS, remaining 29 NOT_RUN.
- `docs/progress/WORKLOG.md` — actual work, checks and continuation.

## Progress

Implemented: POST `/api/v1/auth/login`, GET `/api/v1/auth/me`, admin POST/GET
`/api/v1/users`, PATCH `/api/v1/users/{id}/status`. Current account state is checked
on every token use. Passwords use salted PBKDF2. No default credentials are shipped.
Audit events and the other designed routes remain planned. See `TASKS.md` and the
worklog for evidence. No CI, UI, release or Product Owner acceptance is claimed.

## Run locally

Run isolated tests with Java 21, Maven and a running Docker Linux engine:

```powershell
mvn -f backend/pom.xml verify
```

See `docs/design/LOCAL_SETUP.md` for the Windows TLS trust-store option and local
server/bootstrap setup. `mvn test` alone does not run PostgreSQL integration tests.
Docker Compose configuration passed validation; manual development bootstrap has
not yet been exercised. The HTTP application was started by integration tests.

Documentation integrity: `python tools/check_docs.py` (Python 3, standard library only).
