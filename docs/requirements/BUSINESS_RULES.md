# Business Rules Catalogue — V1.0

**Status semantics:** `APPROVED` = grounded in approved decisions or V1 question type; `PROPOSED` = detailed policy awaiting validation. Technical mechanisms may be designed conservatively without claiming product approval. Each rule links to a high-level BR.

| Rule ID | BR | Status | Rule | Test focus |
|---|---|---|---|---|
| RULE-AUTH-001 | BR-01 | PROPOSED | Protected APIs require valid authenticated identity. | Missing, invalid, expired token |
| RULE-AUTH-002 | BR-01 | PROPOSED | Admin, Instructor and Student have role-specific permissions. | 403/ownership matrix |
| RULE-AUTH-003 | BR-01, BR-09 | PROPOSED | Students cannot access another student's answers/results. | IDOR and cross-user access |
| RULE-AUTH-004 | BR-01, BR-03 | PROPOSED | Instructors can modify only owned content unless authorized otherwise. | Ownership checks |
| RULE-QUE-001 | BR-03 | APPROVED | V1 questions have exactly one correct answer. | Invalid answer cardinality |
| RULE-QUE-002 | BR-03 | PROPOSED | Question belongs to a subject and may have a difficulty level. | Required fields |
| RULE-QUE-003 | BR-03 | PROPOSED | CSV import validates schema, mandatory data and correct-answer references. | Rows, rollback/partial import policy |
| RULE-QUE-004 | BR-03, BR-04 | APPROVED | Editing the question bank must not mutate published exam content. | Immutability, DB snapshots |
| RULE-EXAM-001 | BR-04 | PROPOSED | Exams use DRAFT, PUBLISHED and CLOSED states. | Legal/illegal transitions |
| RULE-EXAM-002 | BR-04 | PROPOSED | Only a complete valid draft may be published. | Invalid publish |
| RULE-EXAM-003 | BR-04 | APPROVED | Published exam version contains fixed questions with optional display-order shuffle. | Content membership/order |
| RULE-EXAM-004 | BR-04 | APPROVED | Published content is immutable; modifying it requires a new version. | Version isolation |
| RULE-EXAM-005 | BR-04, BR-07 | PROPOSED | Exam duration is positive and enforced server-side. | Duration bounds |
| RULE-EXAM-006 | BR-04, BR-06 | APPROVED | Configured attempt limit is an integer from 1 to 3. | 0, 1, 3, 4, decimals |
| RULE-ATT-001 | BR-05, BR-06 | PROPOSED | Student starts only assigned eligible exam versions. | Missing assignment, closed exam |
| RULE-ATT-002 | BR-06 | APPROVED | Student may not exceed configured 1–3 attempts. | Attempt count boundary |
| RULE-ATT-003 | BR-06 | APPROVED | Only one IN_PROGRESS attempt per student/exam version (DEC-10). | Parallel starts |
| RULE-ATT-004 | BR-06 | PROPOSED | Attempt allocation enforces limits atomically under concurrency. | Concurrent requests |
| RULE-ATT-005 | BR-04, BR-06 | APPROVED | Shuffle does not change fixed question membership. | Consistent scoring basis |
| RULE-TAKE-001 | BR-07 | PROPOSED | Saved answers are attached to the correct student, attempt and question. | Ownership, data integrity |
| RULE-TAKE-002 | BR-07 | PROPOSED | An in-progress attempt can resume before deadline. | Reconnect, session |
| RULE-TAKE-003 | BR-07 | PROPOSED | Backend clock controls start/end timestamps. | Clock tampering |
| RULE-TAKE-004 | BR-07, BR-08 | APPROVED | Expired attempts finalize automatically using successfully saved answers. | Deadline, partial answers |
| RULE-TAKE-005 | BR-07, BR-08 | PROPOSED | Late answers cannot alter final results. | Time boundary/race |
| RULE-TAKE-006 | BR-07, BR-08 | PROPOSED | Finalized attempts cannot be edited or re-submitted as new grading. | Invalid transition |
| RULE-SCORE-001 | BR-08 | APPROVED | Backend computes score from saved answers and correct answers for the fixed exam version. | Score integrity |
| RULE-SCORE-002 | BR-08, BR-09 | APPROVED | Score is published immediately on successful finalization and grading. | Timely visibility |
| RULE-SCORE-003 | BR-08 | APPROVED | Equal-weight score = correct / total × 100 rounded to two decimals HALF_UP; pass when rounded score meets threshold (DEC-06). | Threshold and rounding boundaries |
| RULE-SCORE-004 | BR-08 | APPROVED | Each attempt has one authoritative result; resubmission returns that result (DEC-10). | Duplicate submission |
| RULE-SCORE-005 | BR-08, BR-09 | PROPOSED | History and result views reflect persisted scores and attempt states. | DB-to-API consistency |
| RULE-DATA-001 | BR-08 | PROPOSED | Finalization and scoring have transactional consistency. | Rollback/retry |
| RULE-DATA-002 | BR-08 | PROPOSED | Concurrent submit requests cannot produce duplicate scores. | Idempotency, lock strategy |
| RULE-DATA-003 | BR-07 | PROPOSED | Concurrent answer updates follow a deterministic documented rule. | Lost updates |
| RULE-DATA-004 | BR-08 | APPROVED | Client-provided points/correctness are never authoritative for scoring. | Tampered payload |
| RULE-LOG-001 | BR-10 | PROPOSED | Important publish/assignment/submit actions create audit events. | Completeness |
| RULE-LOG-002 | BR-10 | PROPOSED | Notifications are visible only to intended recipients. | Ownership |
| RULE-LOG-003 | BR-08, BR-10 | PROPOSED | Notification failures do not corrupt committed exam results. | Failure isolation |
| RULE-LOG-004 | BR-10 | PROPOSED | Audit logs do not leak secrets or excessive personal data. | Redaction |

## Review notes

- These rules are a **planning catalogue**, not a fully accepted SRS.
- DEC-06/07/08/10 were approved on 2026-11-20. Expired attempts consume quota; student result views omit answer keys. HTTP codes, state mechanics and CSV transaction policy require engineering specifications.
- Where rules overlap, tests should use traceability rather than inflate counts artificially.
