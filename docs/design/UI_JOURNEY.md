# First UI Journey — QM-008

Scope: FR-001/002, verified auth/account API only. English UI. No unimplemented
exam actions. React client with Vite; same-origin `/api` development proxy.

## Expected behavior before implementation

- `/` presents a labelled email/password login form. Validation uses 12–128
  password characters and the backend remains authoritative. Errors are generic,
  accessible and never contain the submitted password or a raw exception.
- Login stores the bearer token only in application memory, loads `/auth/me` and
  renders identity. Reload returns to sign-in. Logout clears token and credentials.
  A 401 from a protected request clears the session and explains expiry.
- Every role can view its current profile. Admin alone sees Accounts navigation,
  a paginated account list, creation form and activate/deactivate buttons.
  A non-admin navigating to `/accounts` sees Access denied and makes no users API
  request. UI gating supplements, never replaces, backend authorization.
- Creation validates email, password, role and active status. Success refreshes
  the list and clears the password. Duplicate email remains a recoverable 409.
  Pending controls prevent duplicate submission; failure does not invent success.
- Account status update waits for the server before changing displayed status.
  Confirmed identity is refreshed after self-deactivation, triggering logout.
- Passwords and tokens never enter localStorage, sessionStorage, URL/query,
  telemetry or logs. Pending requests cannot restore a logged-out session.
- Responsive layout with visible keyboard focus, semantic forms, error/status
  announcements and readable 16px body text. Controls have stable test IDs or
  accessible names. Navigation supports back/forward.

## Validation plan

Component tests: valid/failed login, admin creation/conflict/status, forbidden role,
logout/401, pending-request race, pagination. HTTP client tests check non-JSON errors
and safe fallback messages. Build must pass. Live browser verifies the real
Vite→Spring Boot→PostgreSQL journey using disposable synthetic data, including
denied access and login after deactivation. Keep backend regression passing.

Local server bootstrap uses environment-only credentials. Standalone public
hosting/deployment is excluded.

## Observed verification — 2026-11-20

- `npm test`: 12 tests PASS; includes role deep links, create/conflict/status,
  pagination, logout during pending mutation, safe proxy errors and protected 401.
- `npm run build`: PASS; `npm audit --audit-level=moderate`: zero vulnerabilities.
- Backend `mvn verify`: 3 unit + 24 real HTTP/PostgreSQL integration tests PASS,
  zero errors/failures/skips (regression, backend code unchanged).
- Actual in-app browser → Vite 5173 → standalone Spring Boot 8080 → disposable
  PostgreSQL: Admin login, Student creation, duplicate-email error, activate and
  deactivate, blocked inactive login, Student profile, forbidden accounts deep
  link and revoked active-session logout all PASS.
- Browser found two defects: mobile page overflow (table min-content plus an
  absolutely positioned hidden header) and a profile error overwriting the expiry
  explanation. Fixed both; mobile DOM measured page width = viewport width = 375px
  under a 390×844 override; expiry explanation verified in browser and new test.
- Screenshots: ignored `frontend/reports/generated/accounts.jpg`,
  `accounts-mobile.jpg`, `access-denied.jpg`. Screenshots show synthetic data only;
  curated result summary is tracked here and in WORKLOG.
- Fixture Java/container stopped after label/process checks; generated credentials
  removed. Dev preview stopped. No remote push, public hosting or production data.
