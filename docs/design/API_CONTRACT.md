# REST API Contract — v1

Status: design baseline; auth/account, taxonomy, instructor question CRUD/import, exam lifecycle and assignment routes are implemented. Attempt start is in progress; remaining paths are not implemented. Base path `/api/v1`.
FR references resolve to SRS; access rules resolve to ACCESS_AND_STATES.
UUID path IDs, UTC timestamps, JSON requests/responses unless stated otherwise.
All endpoints require bearer JWT except login. No implicit role hierarchy.

## Common protocol

- 200 read/update/action; 201 creation with Location header; 204 deletion.
- 400 malformed JSON, invalid UUID, missing/unknown fields or invalid values;
  401 invalid/missing authentication or bad login; 403 denied role; 404 missing or
  foreign-owned resource; 409 duplicate/state/revision/quota conflict; 413 import
  too large; 415 unsupported media type; 503 unavailable dependency/retryable lock
  timeout. Never expose SQL/stack traces in responses.
- A 401 response has `WWW-Authenticate: Bearer`; authentication failures precede
  protected payload validation. Protected 403 precedes ownership lookup.
- Error JSON: `{"code":"VALIDATION_ERROR","message":"Request validation failed",
  "fieldErrors":[{"field":"email","message":"must be valid"}]}`. Stable codes
  include UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, DUPLICATE_EMAIL, DUPLICATE_NAME, STATE_CONFLICT,
  ATTEMPT_LIMIT, REVISION_CONFLICT, ATTEMPT_EXPIRED, VALIDATION_ERROR,
  UNSUPPORTED_MEDIA_TYPE, PAYLOAD_TOO_LARGE and SERVICE_UNAVAILABLE.
  Messages contain no submitted secrets; fieldErrors defaults to an empty array.
- List query `page=0&size=20`, page integer >=0, size 1–100. Response
  `{items:[...],page:0,size:20,total:17}` sorted by ID ascending. Explicit sort is
  not exposed in V1. Filter parameters are whitelisted per endpoint.
- Reject unknown write properties, including client `score`, `role` on login,
  timestamps and correctness flags in student answer requests.
- Draft/answer update requires integer `revision`; return new revision on success.
  Version creation is not idempotent; submit, assignment and mark-read are.

## Endpoint inventory

Roles: A=Admin, I=Instructor, S=Student, *=any authenticated role, Public=anonymous.
Schemas below name request → response; `—` means no body. Every role restriction
also requires the ownership check defined in the access model.

| Method | Path (under base) | Role | Input → success | FR / special errors |
|---|---|---|---|---|
| POST | /auth/login | Public | Login → Token (200) | FR-001; generic 401 for invalid/inactive |
| GET | /auth/me | * | — → Account (200) | FR-001 |
| POST | /users | A | CreateUser → Account (201) | FR-002; duplicate 409 |
| GET | /users | A | page,size → Page<Account> | FR-002 |
| PATCH | /users/{id}/status | A | AccountStatus → Account | FR-002 |
| GET | /students | I | page,size → Page<StudentSummary> | FR-008; active students only; implemented |
| GET | /categories | A,I | page,size → Page<Category> | FR-003; implemented |
| POST | /categories | A | Name → Category (201) | FR-003; duplicate 409 DUPLICATE_NAME; implemented |
| GET | /subjects | A,I | page,size,categoryId? → Page<Subject> | FR-003; implemented |
| POST | /subjects | A | SubjectInput → Subject (201) | FR-003; parent 404/duplicate 409 DUPLICATE_NAME; implemented |
| GET | /questions | I | page,size,subjectId? → Page<Question> | FR-004; own only; implemented |
| POST | /questions | I | QuestionInput → Question (201) | FR-004; implemented |
| GET | /questions/{id} | I | — → Question | FR-004; foreign resource 404; implemented |
| PUT | /questions/{id} | I | QuestionUpdate → Question | FR-004; revision 409; implemented |
| POST | /questions/import | I | multipart file → ImportResult (201) | FR-005; atomic row errors 400; implemented |
| GET | /exams | I | page,size → Page<Exam> | FR-006; own only; implemented |
| POST | /exams | I | ExamInput → Exam (201) | FR-006; implemented |
| GET | /exams/{id} | I | — → Exam | FR-006; own only; implemented |
| PUT | /exams/{id} | I | ExamUpdate → Exam | FR-006; state/revision 409; implemented |
| POST | /exams/{id}/publish | I | Revision → Exam | FR-006; state/validation 409/400; immutable snapshots; implemented |
| POST | /exams/{id}/close | I | — → Exam | FR-007; draft 409; repeated close stable; implemented |
| POST | /exams/{id}/versions | I | — → Exam (201) | FR-007; published/closed source only; implemented |
| PUT | /exams/{id}/assignments/{studentId} | I | — → Assignment (201 new / 200 existing) | FR-008; state/inactive 409; implemented |
| GET | /exams/{id}/assignments | I | page,size → Page<Assignment> | FR-008; owner only; implemented |
| GET | /assignments | S | page,size → Page<AssignedExam> | FR-008/009; own only; implemented |
| POST | /exams/{id}/attempts | S | — → Attempt (201 new / 200 active) | FR-009; quota/state 409; unassigned 404 |
| GET | /attempts/{id} | S | — → Attempt | FR-010, FR-011; reconcile expiry |
| PUT | /attempts/{id}/answers/{questionId} | S | SaveAnswer → SavedAnswer | FR-010; expiry/revision/state 409 |
| POST | /attempts/{id}/submit | S | — → Result | FR-011, FR-012; retries 200 same result |
| GET | /attempts/{id}/result | S,I | — → Result | FR-013; still active 409 |
| GET | /results | S | page,size → Page<Result> | FR-013; own only |
| GET | /exams/{id}/results | I | page,size → Page<Result> | FR-013; owned version |
| GET | /notifications | * | page,size → Page<Notification> | FR-014; recipient only |
| PUT | /notifications/{id}/read | * | — → Notification | FR-014; idempotent |
| GET | /audit-events | A | page,size → Page<AuditEvent> | FR-014 |

35 endpoints are sufficient for the initial contract. The starter estimate of 48
is not a feature requirement. No public signup/password reset, delete-history,
notification sending or arbitrary role mutation endpoints are added.

## Payload schemas

All fields required unless `?` is shown. Strings are trimmed except passwords.
All response-only fields are rejected in write DTOs. Nested schemas obey the same
validation. SRS defines numerical/text bounds.

| Schema | Fields and semantics |
|---|---|
| Login | email:string(valid email <=254), password:string(12–128) |
| Token | accessToken:string, tokenType:"Bearer", expiresIn:integer seconds; Cache-Control:no-store |
| Account | id:UUID, email:string, role:ADMIN\|INSTRUCTOR\|STUDENT, active:boolean; never hash/password |
| CreateUser | email, password, role, active:boolean |
| AccountStatus | active:boolean |
| StudentSummary | id:UUID, email:string; no hash/status/admin metadata |
| Name | name:string(1–120) |
| Category | id:UUID, name:string |
| SubjectInput / Subject | categoryId:UUID,name / plus id:UUID |
| QuestionInput | subjectId:UUID,prompt:string(1–2000),difficulty?:EASY\|MEDIUM\|HARD,options:array(2–6) of {label:string(1–20),text:string(1–500),correct:boolean}; label/text are trimmed, distinct case-insensitively, exactly one correct |
| QuestionUpdate | QuestionInput plus revision:integer>=0 |
| Question | input fields plus id,ownerId,revision; instructor only |
| ImportResult | imported:integer,questionIds:UUID[]; invalid import uses error field paths `rows[2].correctLabel` |
| ExamInput | title:string(1–120),questionIds:UUID[] unique, durationSeconds:integer(60–10800),attemptLimit:integer(1–3),passingThreshold:decimal(0–100,2dp),shuffle:boolean |
| ExamUpdate | ExamInput plus revision |
| Revision | revision:integer>=0 |
| Exam | id,familyId,versionNumber,ownerId,state:DRAFT\|PUBLISHED\|CLOSED,revision; settings from ExamInput; question snapshot includes key for owning instructor only |
| Assignment | id,examId,studentId,allocatedCount,createdAt |
| AssignedExam | examId,title,state,durationSeconds,attemptLimit,attemptsUsed; no questions/keys before start |
| Attempt | id,examId,attemptNumber,state:IN_PROGRESS\|SUBMITTED\|EXPIRED,startedAt,deadline,finalizedAt?:instant,questions:array of {id,prompt,options:[{id,label,text}],selectedOptionId?:UUID,revision}; saved display order, no correctness |
| SaveAnswer | selectedOptionId:UUID or null to clear, revision:integer>=0; first revision=0 |
| SavedAnswer | questionId,selectedOptionId:UUID or null,revision:integer>=1,updatedAt |
| Result | attemptId,examId,studentId,state:SUBMITTED\|EXPIRED,score:decimal(2dp),passed:boolean,finalizedAt; no answer key/per-question correctness |
| Notification | id,type:ASSIGNED\|RESULT_READY,resourceId,createdAt,readAt?:instant |
| AuditEvent | id,actorId?:UUID,action,resourceType,resourceId,createdAt; sanitized allowlisted metadata only |

CSV headers exactly `subjectId,prompt,optionA,optionB,optionC,optionD,correctLabel`;
correctLabel A/B/C/D, UTF-8, CSV quoting supports commas/newlines, max 500 data rows
and 1 MiB. Invalid header/row is 400 with row/field errors; oversized file is 413.
The whole file is validated before persistence, and all valid rows commit in one
transaction. This endpoint is implemented and verified.

## First executable slice: auth and accounts

JWT uses HS256 with a base64-encoded secret of at least 32 decoded bytes supplied
through environment, issuer `quizmaster-qa`, audience `quizmaster-api`, subject
account UUID and expiration 15 minutes. No client-chosen roles. Verify signature,
algorithm, issuer, audience, expiry using backend Clock; reload account state on
every request. No token in URLs or logs. Password hashing uses Spring Security's
adaptive encoder with tests for valid/invalid credentials; no hand-rolled crypto.

Examples use placeholders only:

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"student@example.test","password":"<runtime-test-password>"}
```

Valid active credentials → 200 Token; invalid password, unknown or inactive account
→ identical 401 code/message. Bad JSON/invalid lengths → 400. `GET /auth/me` with
valid token returns Account. Student token sent to `POST /users` → 403 before body
processing. Admin duplicate normalized email → 409 DUPLICATE_EMAIL. Deactivate a
student then reuse that student's JWT → 401. No default admin password is shipped.

## Validation boundary

This document is the contract for subsequent implementation and automation. It
does not claim OpenAPI conformance or runtime validation. Per-slice machine-readable
OpenAPI and contract checks may be introduced without changing these business rules.
