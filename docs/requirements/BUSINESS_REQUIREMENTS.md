# Business Requirements — V1.0

> High-level requirements. Detailed functional acceptance criteria remain to be authored under QM-002. Requirement descriptions are scoped but not an API specification.

| ID | Priority | Business requirement | Verification direction |
|---|---|---|---|
| BR-01 | Critical | Authenticated users access functions only in permitted roles and ownership boundaries. | Authentication, authorization, cross-user negative tests |
| BR-02 | High | Admin manages user accounts and access status. | API validation, role restrictions, persistence |
| BR-03 | High | Instructor manages eligible questions and imports questions from CSV. | CRUD/import negative tests, DB checks |
| BR-04 | Critical | Instructor creates, versions, publishes and closes exam content. | State transitions, immutability, concurrency |
| BR-05 | High | Instructor assigns eligible exams to students. | Assignment rules, data isolation |
| BR-06 | Critical | Eligible Student starts a limited number of exam attempts. | Atomic limits, eligibility, concurrency |
| BR-07 | Critical | Student saves answers and takes exams within backend-enforced deadlines. | Persistence, expiration, time boundary |
| BR-08 | Critical | Backend finalizes and accurately scores submitted/expired attempts. | Idempotency, scoring, transaction, race conditions |
| BR-09 | High | Students view immediately published scores and their own attempt history. | Results correctness and privacy |
| BR-10 | Medium | System produces necessary internal notifications and audit trails. | Event accuracy, data isolation and sanitization |

## Traceability convention

Connect `BR-XX` → `RULE-AREA-XXX` → (future) `FR-XXX` / API path → `TC-XXX` → actual run/evidence. Never record coverage as executed unless verified. Changes to these BRs that materially change approved scope require a decision log entry and review.
