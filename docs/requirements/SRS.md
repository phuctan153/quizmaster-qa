# Software Requirements and Acceptance Criteria — V1

Status: engineering specification, not evidence of implemented behavior. Approved
decisions constrain all rows. Details marked assumption remain reversible and do
not promote PROPOSED catalogue rules to product approval.

## Functional acceptance criteria

| FR | BR / rules | Required outcome and negative boundaries |
|---|---|---|
| FR-001 | BR-01; RULE-AUTH-001..003 | Valid active account can log in and read its own identity. Invalid credentials use one generic error. Missing, malformed, expired or tampered token returns 401. Wrong role returns 403. No password/hash/token appears in identity or errors. |
| FR-002 | BR-02; RULE-AUTH-002 | Admin creates accounts with one of three roles and changes active status. Duplicate normalized email is rejected atomically. Non-admin cannot create/deactivate accounts. Inactive users cannot log in or use existing tokens. |
| FR-003 | BR-03; RULE-QUE-002 | Admin manages categories/subjects; instructor selects an existing subject. Reject missing names, duplicate normalized names, invalid parent and deletion of referenced taxonomy. |
| FR-004 | BR-03; RULE-QUE-001/004, RULE-AUTH-004 | Instructor creates/updates owned single-answer questions. Reject empty prompt, duplicate option labels, missing/unknown correct option or multiple correct options. Student payloads never contain correctness flags. Bank edits cannot alter a published snapshot. |
| FR-005 | BR-03; RULE-QUE-003 | CSV import validates headers, encoding, lengths, subject and option/correct-answer references. Reject the entire batch on any bad row, with row-number errors; no partial persistence. Assumption: UTF-8, 1 MiB, at most 500 rows, four options per imported row. |
| FR-006 | BR-04; RULE-EXAM-001..006 | Instructor edits an owned draft with nonempty unique question IDs, positive duration, passing threshold 0–100 and integer attempt limit 1–3. Publish validates and snapshots all content atomically. Invalid configuration leaves draft unchanged. |
| FR-007 | BR-04; RULE-EXAM-003/004 | Published version is immutable. Version creation copies content into a new draft with a new identifier. Bank changes or edits to another draft cannot change its questions, settings or answer key. Closing prevents new starts. |
| FR-008 | BR-05; RULE-ATT-001 | Instructor assigns an owned published version to an active student. Duplicate assignment is idempotent. Foreign ownership, draft/closed version and non-student recipient are rejected. No assignment implies no start permission. |
| FR-009 | BR-06; RULE-ATT-002..005; DEC-07/10 | Start reserves one numbered attempt atomically, only for an assigned published version and below limit. At most one active attempt per student/version. Concurrent starts cannot exceed quota. Expired attempts count; duplicate active start returns the existing attempt. |
| FR-010 | BR-07; RULE-TAKE-001..003 | Student reads/resumes own active attempt before deadline and saves an answer only for its snapshot questions/options. Omitted answers remain unanswered. Backend determines timestamps. Reject foreign attempts, unknown options and stale revision updates without changing persisted answers. |
| FR-011 | BR-07/08; RULE-TAKE-004..006 | At or beyond the deadline, late writes are rejected and saved answers are finalized. Explicit submit before deadline and expiry both commit one result. A UI countdown or client clock cannot extend the deadline. |
| FR-012 | BR-08; RULE-SCORE-001/003/004; RULE-DATA-001/002/004 | Correct answers have equal weight; unanswered/wrong answers earn zero. Compute correct/total × 100 with decimal arithmetic and HALF_UP to two decimals, then compare to threshold. Example: 2/3 = 66.67; threshold 66.67 passes, 66.68 fails. Ignore/reject client-supplied score/correctness. Repeated submit returns the persisted result without regrading. |
| FR-013 | BR-09; RULE-SCORE-002/005; DEC-08 | Successful finalization immediately makes score/pass/state visible to the owning student. History includes own attempts only. Do not expose correct options or explanations to students, including through published exam/attempt payloads. Instructor sees only results for owned versions. |
| FR-014 | BR-10; RULE-LOG-001..004 | Publish, assignment, account changes and finalization produce actor/resource/time audit events. Notifications for assignment/result target the correct student. Retried finalization cannot duplicate events. Notification delivery failure must not roll back a committed score. No passwords, JWTs or answer keys in audit metadata. |

## Engineering assumptions and constraints

- Identity: no public registration, password reset, refresh-token or logout endpoint in
  the first auth slice. Admin provisioning and short-lived JWTs keep the surface
  small. Test-only seed is separate from production initialization.
- Draft question form: 2–6 options with unique nonblank text, exactly one correct;
  CSV uses four columns for options as a documented subset. Prompt 1–2000 chars,
  option 1–500, names 1–120. Text is trimmed. These bounds are reversible defaults.
- Email: trimmed and lowercased with a locale-independent rule, maximum 254 chars;
  password 12–128 chars without trimming; no credentials in source or browser logs.
- Duration: integer seconds, 60–10800; threshold decimal 0–100 with at most two
  fractional digits. Limits are initial engineering defaults, not new approvals.
- Closing a version prevents new attempts but allows existing attempts to finish
  under their original deadlines. Assignment has no schedule/window in V1.
- Deadline equality is expired: `now >= deadline`. Concurrent answer/submit
  operations serialize on the attempt row; stale answer revisions return conflict.
- List responses are paginated, stable sorted by ID, default 20/max 100; invalid
  page/size is rejected. UTC instants are serialized as ISO-8601 with offset.
- No production scale/SLA claim. A bounded expiration reconciliation interval will
  be measured in isolated integration tests; DEC-09 describes that technical plan.

## Cross-cutting acceptance

- Denied operations must leave database state unchanged. Missing/foreign resources
  use a uniform 404 after authentication to avoid leaking ownership.
- Mutations validate server-side, enforce uniqueness in the database and produce
  structured errors without stack traces. Secrets come from environment variables.
- A clean clone must have reproducible setup, isolated seed/reset, automated tests
  and honest command/result evidence before a slice is marked DONE.
- Coverage is traceability, not a quota. Application tests are currently NOT_RUN.

## Remaining questions

No pending product choice blocks documenting the first auth slice. The exact
expiration worker/recovery mechanism (DEC-09) is technical design work under QM-004.
Additional material policy changes must be logged for Product Owner approval.
