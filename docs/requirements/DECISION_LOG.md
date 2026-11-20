# Product Decision Log

> Approval is **human-owned**. Codex may create or revise `PROPOSED` or `PENDING` records freely, but must not mark a product decision `APPROVED` without explicit human confirmation. Approval date is not invented.

| ID | Status | Decision | Rationale / notes |
|---|---|---|---|
| DEC-01 | APPROVED | At attempt expiry, automatically finalize and score successfully saved answers. | Backend-controlled deadline; exact mechanics to design. |
| DEC-02 | APPROVED | Instructor sets attempt limit to an integer from 1 through 3. | Atomic enforcement to be designed. |
| DEC-03 | APPROVED | Publish score immediately after successful finalization and scoring. | Releasing answer keys is separately undecided. |
| DEC-04 | APPROVED | An exam version uses a fixed question set with optional display-order shuffle. | Shuffle semantics require design. |
| DEC-05 | APPROVED | Published exam content is immutable; changes require a new version. | Snapshot/version design pending. |
| DEC-06 | APPROVED | Equal weights; score = correct / total × 100, rounded to two decimals HALF_UP. Compare rounded score to passing threshold. | Product Owner approved the proposed bundle in this session, 2026-11-20. |
| DEC-07 | APPROVED | Expired attempts consume the attempt quota. | Product Owner approved the proposed bundle, 2026-11-20. |
| DEC-08 | APPROVED | Student result views expose score without the answer key in V1. | Product Owner approved the proposed bundle, 2026-11-20. |
| DEC-09 | PENDING | Expiration finalization mechanics and delayed worker scheduling. | Backend check + reliable expiration reconciliation. |
| DEC-10 | APPROVED | At most one in-progress attempt per student/exam version. Resubmitting a finalized attempt returns the same authoritative result. | Product Owner approved the proposed bundle, 2026-11-20. HTTP mechanics remain a technical detail. |

## How Codex updates decisions

1. Add entries with unique IDs, concise options/trade-offs and `PENDING` status when meaningful policy choice arises.
2. Decide reversible implementation details on its own, labeling assumptions in design notes rather than inventing approval.
3. When explicit human approval arrives, record the decision and cite the conversation/date **only if known**; update dependent BR/rules/tests.
4. Never erase approved historical decisions to make current implementation appear compliant.
