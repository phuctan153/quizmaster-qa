# Access, Lifecycle and Concurrency Model

Engineering design for FR-001..014. Ownership checks are enforced server-side,
including nested resource relationships. Admin is not an implicit instructor or
student. Proposed details implement baseline scope without claiming new approval.

## RBAC matrix

`Own` requires authenticated actor ownership; `Assigned` requires an assignment
to the exact published version. Dash means denied (403 for role, 404 for foreign
resource after role eligibility). Anonymous callers receive 401 on protected APIs.

| Operation | Admin | Instructor | Student |
|---|---|---|---|
| Read own identity | Own | Own | Own |
| Create/list/activate accounts | Yes | — | — |
| Read taxonomy | Yes | Yes | — |
| Change taxonomy | Yes | — | — |
| Create/import/change/read bank questions | — | Own | — |
| Create/read/edit/publish/close/version exam | — | Own | — |
| Assign exam and read roster | — | Own exam | — |
| Read assigned exam metadata | — | — | Assigned |
| Start/read/resume/save/submit attempt | — | — | Own + Assigned for start |
| Read result/history | — | Own exam | Own attempt |
| Read/mark notifications | Own | Own | Own |
| Read audit events | Yes | — | — |

Instructor lookup of active student IDs/emails is limited to assignment workflow;
it must not expose full user administration data. Student public exam metadata
never includes correct answers. Instructor result access does not grant access to
another instructor's bank, exam or assignments.

## Account lifecycle

Admin creates ACTIVE or INACTIVE accounts; activation/deactivation changes status.
Every authenticated request resolves the current account and role, so deactivation
invalidates access even before JWT expiry. An unknown/deleted identity is 401.
Role updates, account deletion and self-registration are excluded from the initial
contract. Password/hash are write-only; no account response includes them.

## Exam version lifecycle

| From | Action / precondition | To / effect | Denied cases |
|---|---|---|---|
| None | Owner creates valid draft | DRAFT | Invalid fields; wrong role |
| DRAFT | Edit own draft | DRAFT, revision increases | Stale revision; foreign questions |
| DRAFT | Publish valid complete draft | PUBLISHED, immutable snapshot | Empty/invalid questions or settings |
| PUBLISHED | Close own version | CLOSED | Foreign ownership |
| PUBLISHED or CLOSED | Create successor version | New DRAFT with new ID; source unchanged | Non-owner |
| CLOSED | Repeat close | CLOSED, no duplicate event | Non-owner |

No PUBLISHED→DRAFT or CLOSED→PUBLISHED transition. Published settings, passing
threshold, duration, attempt limit and snapshot are immutable. Closing does not
erase assignments/results or shorten existing deadlines (SRS assumption).

## Assignment and attempt lifecycle

Assignment is unique by `(version, student)`; no revoke operation in initial V1.
Creating it requires owned PUBLISHED version and active Student. A later close
disables starts; changing account status disables access but does not pause time.

| From | Action / precondition | To / effect | Denied cases |
|---|---|---|---|
| None | Assigned active student starts published version below quota | IN_PROGRESS; numbered slot, server deadline | No assignment, closed, exhausted |
| IN_PROGRESS | Start same version again before deadline | Same attempt; no quota increase | Foreign student |
| IN_PROGRESS | Save answer at `now < deadline` | IN_PROGRESS; revision increases | Stale revision; unknown snapshot option |
| IN_PROGRESS | Submit at `now < deadline` | SUBMITTED + result | Wrong owner |
| IN_PROGRESS | Worker or request observes `now >= deadline` | EXPIRED + result from committed answers | Late answer cannot affect result |
| SUBMITTED or EXPIRED | Submit again / read result | Same state/result | Wrong owner |
| SUBMITTED or EXPIRED | Save answer | No change, conflict | All writes denied |

Both terminal states consume quota. No reset/regrade/cancel endpoint. Starting
after an old attempt expires first reconciles it, then reserves another slot if
eligible. Original shuffle order is persisted and reused on resume.

## Transaction and race invariants

1. Start locks version, then assignment row in that order, validates status/quota,
   reconciles expired active attempt and inserts the next numbered attempt.
   Counter, attempt and audit updates commit together; rollback consumes no slot.
2. Close locks the same version row. A start ordered before close may finish;
   a start ordered after close is denied. Bank edits never rewrite snapshots.
3. Save/submit/expiry lock the attempt row, then sample backend UTC time. Saves
   require matching answer revision. Competing saves with the same revision yield
   one winner and one 409; no silent lost update. All attempt questions are snapshot
   IDs, not live question-bank IDs.
4. Finalization reads only answers committed before its serialized deadline check.
   Terminal state, result, audit and outbox event commit atomically. Unique result
   per attempt prevents double scoring. Duplicate submit returns persisted result.
5. Finalization never rolls back merely because a late answer must receive a 409:
   service returns an outcome, commits expiry, then controller maps the conflict.
6. Worker claims bounded overdue attempts without waiting on already locked rows;
   retries recover after restart. Notifications are delivered from durable outbox,
   deduplicated by event key. Readers reconcile an overdue attempt before returning
   its result, so worker delay does not allow late editing.
7. Lock order is version→assignment→attempt when multiple types are needed;
   attempt-only paths never acquire their parent locks afterward. Conflicts and
   deadlocks return a safe retry error; bounded retries must be tested.

## Required verification

Run role × ownership tests with two instructors and two students. Assert unchanged
row counts on denied transitions, concurrent quota upper bound, unique terminal
result/event, answer revision conflict, deadline equality, start/close ordering,
published snapshot isolation and duplicate-submit stability. These are planned
tests, NOT_RUN until executable slices exist.
