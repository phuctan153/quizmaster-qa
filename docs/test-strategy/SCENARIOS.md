# Initial Risk-Based Scenario Catalogue

TC-001..007 and TC-035 are **PASS** on 2026-11-20; see execution mapping below.
TC-008..015 are **PASS** on 2026-11-25. TC-016..034 and TC-036 are **NOT_RUN**. These are independent
expected results derived from SRS. Risk P0 precedes P1.

| TC | Priority | FR | Rule / decision | Scenario → expected outcome | Layer |
|---|---|---|---|---|---|
| TC-001 | P0 | FR-001 | RULE-AUTH-001 | Active valid login → token, own identity, no secrets in response | API |
| TC-002 | P0 | FR-001 | RULE-AUTH-001 | Unknown/inactive/wrong password → identical generic 401 | API |
| TC-003 | P0 | FR-001 | RULE-AUTH-001 | Missing/malformed/tampered/expired/wrong issuer/audience JWT → 401 | Security |
| TC-004 | P0 | FR-001 | RULE-AUTH-002 | Each non-admin role calls account mutation → 403, no rows changed | API/DB |
| TC-005 | P0 | FR-002 | RULE-AUTH-002 | Admin creates duplicate normalized email concurrently → one account, one 409 | API/DB |
| TC-006 | P0 | FR-002 | RULE-AUTH-001 | Deactivate user after token issued → existing token rejected 401 | Integration |
| TC-007 | P1 | FR-002 | RULE-AUTH-002 | Invalid email/password/role/unknown property → 400, no secrets echoed | API |
| TC-008 | P1 | FR-003 | RULE-QUE-002 | Missing parent/duplicate taxonomy/foreign role → rejected, no dangling FK | API/DB |
| TC-009 | P0 | FR-004 | RULE-QUE-001 | Zero/multiple correct options or invalid count → 400 | API |
| TC-010 | P0 | FR-004 | RULE-AUTH-004 | Instructor B edits/reads A's question → 404, no mutation | Security/DB |
| TC-011 | P1 | FR-005 | RULE-QUE-003 | CSV bad header/quoted newline/bad final row → correct parsing, whole-batch rollback on bad data | API/DB |
| TC-012 | P1 | FR-005 | RULE-QUE-003 | 500/501 rows and 1 MiB/1 MiB+1 byte → acceptance/rejection at bounds | API |
| TC-013 | P0 | FR-006 | RULE-EXAM-006 | Attempt limit 0/1/3/4/1.5 → only integers 1..3 accepted | API |
| TC-014 | P0 | FR-006 | RULE-EXAM-002 | Publish empty/invalid/stale draft → rejected, remains DRAFT, no snapshot | API/DB |
| TC-015 | P0 | FR-007 | RULE-EXAM-004 | Edit bank/create new version after publish → old snapshot/settings unchanged | Integration/DB |
| TC-016 | P0 | FR-007 | RULE-EXAM-001 | CLOSED→publish and PUBLISHED→edit → 409; repeat close is stable | API/state |
| TC-017 | P0 | FR-008 | RULE-ATT-001 | Assign draft/closed/foreign exam or non-student → denied; duplicate assignment returns same row | API/DB |
| TC-018 | P0 | FR-009 | RULE-ATT-002; DEC-07 | Start quota 1 and 3 with expired/submitted predecessors → never exceed limit | API/DB |
| TC-019 | P0 | FR-009 | RULE-ATT-003/004 | Barrier-start concurrent requests → one active attempt, contiguous committed numbers, stable counter | Concurrency/DB |
| TC-020 | P0 | FR-009 | RULE-EXAM-001 | Race start against close → serialized allowed start or rejected start, no post-close allocation | Concurrency |
| TC-021 | P0 | FR-010 | RULE-AUTH-003 | Student B reads/saves/submits A's attempt/result → uniform 404, unchanged rows | Security/DB |
| TC-022 | P0 | FR-010 | RULE-TAKE-001 | Foreign snapshot question/option or stale answer revision → rejected without update | API/DB |
| TC-023 | P0 | FR-010 | RULE-DATA-003 | Two saves at same revision → one accepted, one 409; read matches winner | Concurrency/DB |
| TC-024 | P0 | FR-010 | RULE-ATT-005 | Resume shuffled attempt → stable order, fixed membership, no correctness fields | API/UI |
| TC-025 | P0 | FR-011 | RULE-TAKE-004/005 | Clock before/equal/after deadline → save allowed only before; expiry uses committed answers | Clock/DB |
| TC-026 | P0 | FR-011 | RULE-TAKE-004 | Worker stopped/restarted past deadline → overdue attempt reconciled exactly once | Recovery/DB |
| TC-027 | P0 | FR-012 | RULE-SCORE-003 | 0/3, 2/3, 3/3 → 0.00/66.67/100.00; 66.67 passes threshold 66.67 but fails 66.68 | Unit/API |
| TC-028 | P0 | FR-012 | RULE-DATA-004 | Client injects score/correctness or unknown properties → 400; authoritative score unchanged | Security |
| TC-029 | P0 | FR-012 | RULE-SCORE-004 | Submit/expiry/retry race → one result, same score/time on resubmit, one event | Concurrency/DB |
| TC-030 | P0 | FR-012 | RULE-DATA-001 | Fail inside result transaction → no partial terminal state/result; retry succeeds once | Fault/DB |
| TC-031 | P0 | FR-013 | RULE-SCORE-002; DEC-08 | Submit success → result immediately readable, no answer key across student endpoints | API/UI |
| TC-032 | P0 | FR-013 | RULE-AUTH-003 | Instructor/student foreign result list/detail → no leaks including pagination totals | Security |
| TC-033 | P1 | FR-014 | RULE-LOG-003 | Notification delivery fails → score stays committed, outbox retries without duplicate notification | Fault/DB |
| TC-034 | P1 | FR-014 | RULE-LOG-002/004 | Read foreign notification/audit as student → denied; audit metadata contains no secrets | API/DB |
| TC-035 | P1 | FR-001 | RULE-AUTH-001 | UI login, denied route and logout-local token clearing → expected access boundaries | UI |
| TC-036 | P0 | FR-013 | RULE-SCORE-002 | Instructor publishes/assigns, student answers/submits/views history → consistent UI/API/DB result | E2E |

## Executed evidence — 2026-11-20

`mvn -f backend/pom.xml -Djavax.net.ssl.trustStoreType=Windows-ROOT
-Djavax.net.ssl.trustStore=NONE -B verify` → PASS: 3 unit + 24 HTTP/PostgreSQL
integration invocations, zero failures/errors/skips. Java 21.0.4, Spring Boot
3.5.15, Flyway 11.20.3, PostgreSQL 18.7, Docker 29.5.3. Failsafe uses a real random
HTTP port and disposable PostgreSQL; no mocked repository or H2 database.

| Scenario | Executable evidence in IdentityIT (unless noted) |
|---|---|
| TC-001 | validLoginNormalizesEmailAndReturnsSafeIdentity |
| TC-002 | loginFailuresAreIndistinguishable |
| TC-003 | missingAndMalformedBearerAreUnauthorized; invalidClaimsOrSignatureAreRejected (7 cases); JwtValidationTest.expiryIsRejectedAtExactDeadlineWithoutClockSkew |
| TC-004 | bothNonAdminRolesAreDeniedBeforeBodyValidation; forgedRoleClaimDoesNotGrantAdminAccess |
| TC-005 | adminCreatesHashedAccountAndRejectsDuplicateEmail; concurrentDuplicateCreationCommitsExactlyOneAccount |
| TC-006 | deactivationRejectsAlreadyIssuedToken |
| TC-007 | malformedLoginIsRejectedWithoutEchoingSecrets (5 cases); adminInputAndPaginationAreValidated; numericRolesAndStringBooleansAreRejected; passwordMaximumSupportsUnicodeWithoutTruncation |

Additional executed assertions: direct DB checks on email normalization/role;
short/malformed signing keys fail closed; credential DTOs redact `toString`.
Reports: `backend/target/surefire-reports/TEST-*.xml` and
`backend/target/failsafe-reports/TEST-*.xml` (generated and ignored). Curated durable
summary: WORKLOG QM-007 entry. Scenario counts and test invocation counts differ.

TC-035 PASS (QM-008): actual browser login, Student denied Admin deep link,
Admin create/status, inactive login denial and 401 logout against live backend/DB;
12 Vitest component/client tests and successful Vite build. See UI_JOURNEY and
WORKLOG QM-008 for commands, versions, fixes and screenshot locations. This is
manual browser execution; no Selenium automation or CI pass is claimed.

TC-008 mapping:
`IdentityIT.taxonomyEnforcesRolesNormalizationParentAndPagination` and
`IdentityIT.concurrentNormalizedCategoryCreationCommitsExactlyOneRow`. The
2026-11-25 `mvn -f backend/pom.xml -Djavax.net.ssl.trustStoreType=Windows-ROOT
-Djavax.net.ssl.trustStore=NONE -B verify` → PASS, 3 unit and 26 integration
tests with zero failures/errors/skips. PostgreSQL 18.7 Testcontainers, Flyway V1/V2
and random-port HTTP. Covered RBAC, trim and normalized duplicate handling, blank
and overlong values, unknown parent, subject filtering/pagination and atomic
concurrent duplicate category creation. Failsafe XML is generated under
`backend/target/failsafe-reports` (ignored).

TC-009 and TC-010 PASS (QM-010):
`IdentityIT.questionBankEnforcesOwnershipAndSingleCorrectOption` validates option
count, zero/multiple correct options, normalized duplicate labels/text, strict
JSON, optional difficulty, role restrictions, subject lookup and ownership-based
404; `IdentityIT.questionUpdateUsesRevisionForConcurrentWriters` verifies one
200 and one 409 on a concurrent stale revision. Same Maven run as TC-008: 3 unit
+ 28 PostgreSQL/HTTP integration tests PASS, zero failures/errors/skips; migration
V3 applied to disposable PostgreSQL 18.7. Reports under ignored
`backend/target/failsafe-reports`.

TC-011 and TC-012 PASS (QM-011): `QuestionCsvParserTest` covers quoted commas,
escaped quotes, multiline values, malformed quoting, invalid UTF-8, and the
501-row/1 MiB+1 byte limits. `IdentityIT.csvQuestionImportIsQuotedAndAtomic` covers
quoted HTTP import, row-specific final-row errors, no partial persistence, header/
encoding rejection, exact 1 MiB acceptance and 1 MiB+1 rejection (413).
`IdentityIT.csvImportAcceptsFiveHundredRowsAndRejectsFiveHundredOne` verifies 500
rows commit and 501 reject without partial persistence. Maven `verify` on
2026-11-25: 6 unit + 30 HTTP/PostgreSQL tests PASS, zero failures/errors/skips;
Flyway V1–V3 on disposable PostgreSQL 18.7.

TC-013, TC-014 and TC-015 PASS (QM-012):
`IdentityIT.examDraftPublishesOwnedImmutableQuestionSnapshot` covers invalid and
boundary settings, duplicate/missing/foreign questions, instructor ownership,
publish state, and snapshot prompt/options/answer stability after the source
question and options are edited. It also checks published drafts reject mutation.
`IdentityIT.examDraftConcurrentUpdateHasOneRevisionWinner` proves one current
revision wins and the stale request gets 409; `IdentityIT.invalidPublishRollsBackStateAndSnapshot`
proves an empty draft stays DRAFT with no revision/snapshot changes after rejection.
Maven `verify` on 2026-11-25: 6 unit + 33 HTTP/PostgreSQL integration tests PASS,
zero failures/errors/skips; Flyway V1–V4 on disposable PostgreSQL 18.7.

## Execution strategy

1. Unit: score math, validators and Clock edge cases; no network or sleeps.
2. Integration: real Flyway + PostgreSQL Testcontainers; auth/RBAC and transaction
   failures. H2 is not evidence for PostgreSQL locking/constraints.
3. API automation: REST Assured + Java + TestNG against isolated runnable backend;
   include DB/JDBC assertions with fixture-owned IDs and independent connections.
4. UI: Selenium + Java + TestNG for a small set of critical journeys after API
   behavior stabilizes. Selector contracts use stable test attributes.
5. CI: fast build/unit gate then container integration and API gate. Publish reports
   only from actual runs. Reporting selection remains an evaluated follow-up.

Concurrency uses barriers/latches and bounded joins, not arbitrary sleep-based
ordering. Synthetic accounts are isolated per case. Clock is test-controlled,
and reset is restricted to disposable databases. Scenario counts are not test
pass counts. Manual exploratory sessions record build, steps and actual findings.
