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

### 2026-11-20 — QM-006 — DONE
- Added 36 risk-based scenarios with FR/rule traceability, expected negative/race
  outcomes and execution layers. Every scenario is NOT_RUN, including UI and DB.
- Added standard-library documentation integrity check. Actual command:
  `python tools/check_docs.py` → PASS (14 FRs, 35 routes, 36 scenarios, references,
  language/whitespace and import layout). `git diff --check` → PASS.
- QM-005 commit: `e221422`. No application/CI evidence exists yet.
- Next: execute PLAN-001 (QM-007), resolve dependency/runtime prerequisites and
  test auth slice. Resume: `python tools/check_docs.py`; `docker version`.

### 2026-11-20 — QM-007 — DONE — first executable backend milestone
- Implemented five auth/account routes with Spring Boot 3.5.15 / Java 21, JDBC,
  Flyway migration, normalized unique email, salted PBKDF2 hashes, validated DTOs,
  strict JSON properties/types, paginated admin list and structured errors.
- HS256 JWT requires an environment-supplied >=32-byte base64 secret, issuer,
  audience and 15-minute expiry. Roles/active state come from the database on every
  request. Added opt-in empty-table admin bootstrap and loopback-only Compose DB.
- Environment recovery: sandbox could not reach Docker; background startup outside
  sandbox made engine available (29.5.3). Initial offline Maven build failed due
  to uncached Failsafe. Online build initially failed PKIX trust validation; using
  the Windows ROOT store fixed HTTPS without disabling TLS checks.
- First actual integration run: 22 tests PASS, no skips. Found Flyway 11.7.2 warning
  that PostgreSQL 18 was newer than supported. Checked primary adapter source,
  pinned Flyway 11.20.3; subsequent run has no database-version warning.
- Final command from backend: `mvn -Djavax.net.ssl.trustStoreType=Windows-ROOT
  -Djavax.net.ssl.trustStore=NONE -B verify` → BUILD SUCCESS at 16:29 Bangkok;
  3 unit + 24 HTTP/PostgreSQL tests, 0 failures/errors/skips. PostgreSQL 18.7 in a
  disposable Testcontainers DB, actual Tomcat random port, real migrations/repository.
  Coverage includes JWT claims/signature/exact expiry, role/forged-role denial,
  normalized-email concurrency (one 201 + one 409), deactivation and Unicode bounds.
- Reports are ignored generated XML under backend/target/{surefire,failsafe}-reports;
  durable scenario/test mapping is in SCENARIOS. `jar tf` confirmed both bundled
  Flyway libraries are 11.20.3. No H2 or mocked persistence was used.
- Other checks: `docker compose config --quiet` with a generated process-only
  password PASS; `python tools/check_docs.py` PASS; Git diff whitespace check PASS.
- Limits: standalone bootstrap and development Compose startup NOT_RUN; only config
  and integration-driven HTTP startup verified. Exam/worker/audit/notification
  modules, React UI, Selenium/REST Assured portfolio framework and CI NOT_RUN.
  Development-only HTTP uses loopback; no production readiness claim.
- QM-006 commit: `6f66e04`. Reviewed source/config for credentials, response leaks,
  auth ordering, DB constraints and test resource scope. Backend milestone commit
  prepared; get its exact hash via `git log -1 --oneline`.
- Handoff: QM-008 READY; implement the minimal React auth/Admin journey, run browser
  integration, then create concrete module tasks from roadmap. DEC-09 still PENDING
  as documented; no product decision blocks the auth UI.
- Resume: `git status --short`; `python tools/check_docs.py`; read TASKS/QM-008 and
  LOCAL_SETUP. Reproduce backend: `mvn -f backend/pom.xml
  -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE verify`.

### 2026-11-20 — GitHub remote connection — PASS
- User requested connection to `https://github.com/phuctan153/quizmaster-qa.git`
  and continuation. Initial worktree clean; no prior remote existed.
- Added `origin`. Initial HTTPS check failed certificate-chain validation; set
  repository-local `http.sslBackend=schannel` to use Windows trust, keeping TLS
  verification enabled. `git ls-remote origin` and `git fetch origin` exited 0
  with no advertised refs: remote is empty at inspection time. Local history
  remains intact on master. No push was performed; standing push boundary remains.
- Verification: `git remote -v`, `git ls-remote origin`, `git fetch origin` PASS.
- Next: QM-008 React login/identity/Admin UI, component checks and live browser/API
  validation; retain backend regression. Last backend commit: `d7127de`.

### 2026-11-20 — QM-008 — DONE — auth/account UI milestone
- GitHub origin connection committed as `2213f2a`; remote empty when fetched.
  This task continues locally under the existing no-push/no-deploy boundary.
- Specified UI_JOURNEY before implementation. Added React 19.2.7/Vite 8.3.8 UI,
  same-origin dev API proxy, memory-only token, role-gated Admin directory with
  pagination/create/status, safe error messages and session generation checks.
- Package survey/install initially exposed jsdom 31's higher Node requirement and
  an npm audit advisory for Vite 8.0.16. Pinned jsdom 26.1.0 for Node 22.20.0 and
  Vite 8.3.8 (registry/audit recommended patched release). Process-only
  NODE_USE_SYSTEM_CA=1 fixed certificate errors without weakening TLS validation.
- Actual final frontend commands: `npm test` → 12 PASS (no failures); `npm run
  build` → PASS; `npm audit --audit-level=moderate` → zero vulnerabilities.
- Backend regression: `mvn -Djavax.net.ssl.trustStoreType=Windows-ROOT
  -Djavax.net.ssl.trustStore=NONE -B verify` → PASS, 3 unit + 24 integration,
  zero failures/errors/skips. Backend runtime behavior was not changed.
- Built and ran the standalone jar with bootstrap on 8080 against a new isolated
  PostgreSQL container; generated test credentials stayed in ignored target state.
  Real in-app browser through Vite 5173 verified Admin login, Student create,
  duplicate email 409, status persistence, inactive login rejection, active Student
  profile, denied accounts deep link and logout after Admin revokes active access.
- Browser uncovered/fixed mobile horizontal overflow (now 375px page/viewport under
  mobile override) and profile catch overwriting 401 expiry notice. Added regression
  test for the notice; browser verified the corrected explanation. TC-035 PASS;
  no Selenium automation claim. Screenshots in ignored frontend/reports/generated.
- Added reproducible start/stop-browser-fixture scripts. Initial cleanup hit a
  Windows quoting error before any stop; corrected JSON label inspection and reran
  successfully. Owned Java process/container stopped, temporary credentials removed,
  preview stopped. No existing database/container was reset or deleted.
- Review: no credentials/build output/node_modules in staging; token/password never
  persisted to browser storage or URLs. Documentation integrity and whitespace
  checks run before commit. No CI, deployment or Product Owner acceptance claimed.
- Next: QM-009 READY, implement four taxonomy routes with PostgreSQL migration,
  role/parent/duplicate/concurrency validation; then allocate question-bank slice.
- Resume: `git status --short`; `python tools/check_docs.py`; `npm --prefix frontend
  test`; backend verify command above. Read TASKS/QM-009 and API_CONTRACT.

### 2026-11-20 — Pre-push verification and initial GitHub push — PASS
- User explicitly authorized pushing the current code after successful checks.
  Scope was verification and publication of the existing milestone, not QM-009.
- Clean initial worktree at `6188911`. Fetched origin and confirmed no remote refs;
  retained the existing master branch without rewriting history or force-pushing.
- Actual frontend commands from frontend directory: `npm test` → 12/12 PASS;
  `npm run build` → PASS; `npm audit --audit-level=moderate` with process-only
  NODE_USE_SYSTEM_CA=1 → zero vulnerabilities.
- Actual backend command from backend directory: `mvn
  -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE -B
  verify` → BUILD SUCCESS at 16:59:53 Bangkok; 3 unit + 24 real HTTP/PostgreSQL
  integration invocations, zero failures/errors/skips.
- `python tools/check_docs.py` and `git diff --check` PASS. Tracked-path inspection
  found no .env, credential/key files, target, node_modules, dist or generated
  screenshot artifacts. Browser scenarios were verified in QM-008; no additional
  manual browser run or CI execution was claimed during this verification.
- `git push -u origin master` succeeded. Remote master SHA from `git ls-remote
  origin refs/heads/master` exactly matched local HEAD:
  `6188911fe2f83f2070ddb05f41975bfe1f28fe9d`. Tracking is origin/master; worktree clean
  after the code push. This journal update is committed and synchronized separately.
- Remaining: examination modules and CI are unimplemented; DEC-09 remains PENDING.
  Next implementation task stays QM-009 READY (taxonomy API). No deployment made.
- Resume: `git status --short --branch`; read TASKS/QM-009; reproduce checks using
  the commands above. GitHub source: https://github.com/phuctan153/quizmaster-qa.
