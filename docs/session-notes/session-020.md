# Session 020 — Context Preservation Handoff for PR #6

**Date:** 2026-06-13  
**Focus:** Preserve context before starting a new session; complete PR #6 review handoff docs/rules; provide a paste-ready prompt for the next agent.

---

## What Was Done

- Completed and strengthened the PR #6 follow-up handoff file:
  - `docs/pr6-review-fix-prompts-2026-06-13.md`
  - Contains short verdict, exact findings, severity, why each issue matters, required tests, copy-paste GLM prompt, and post-amend review checklist.
- Updated `docs/session-notes/session-019.md` so it points to the PR #6 follow-up file and clearly says PR #6 should not merge yet.
- Added a context-low handoff rule:
  - Global rules: `/Users/hiennguyen/.claude/CLAUDE.md`
  - Project rules: `CLAUDE.md`
  - Rule: when context is ~75% used or Hien says context is nearly full, stop new work, preserve status, write/update session note, include phase status and a new-session prompt, and ensure PR/coder prompts are saved under `docs/`.
- Added `tasks/lessons.md` with the correction pattern so future agents preserve context proactively.
- Updated `tasks/todo.md` with completed documentation/handoff tasks.
- No production Java code was changed in this context-preservation session.

---

## Current Project State

- **Branch:** `fix/phase4-audit-correctness`
- **PR:** #6 — `fix: Address audit correctness and security gaps`
- **PR URL:** https://github.com/FrorsttzNguyen/service-marketplace/pull/6
- **Merge status:** **Do not merge yet.** PR #6 is a partial fix and still has blocker/high-severity review findings.
- **Most recent full test evidence:** `./mvnw test` passed earlier in this review flow:
  - `Tests run: 284, Failures: 0, Errors: 0, Skipped: 0`
  - `BUILD SUCCESS`
- **Tests in this final handoff step:** not rerun because only docs/rules/task notes were changed.
- **Lightweight verification in this final handoff step:** `git diff --check` passed; `git status --short` reviewed.

### Files Changed / Created in This Handoff Window

- `docs/pr6-review-fix-prompts-2026-06-13.md` — new PR #6 amend prompt and review checklist.
- `docs/session-notes/session-019.md` — updated prior session handoff with PR #6 follow-up link and next instructions.
- `docs/session-notes/session-020.md` — this context preservation handoff.
- `tasks/todo.md` — current documentation/handoff tracking.
- `tasks/lessons.md` — correction rule for context-low handoff.
- `CLAUDE.md` — project rule updated locally with context-low handoff guidance. Note: this file is ignored via `.git/info/exclude` in this repo.
- `/Users/hiennguyen/.claude/CLAUDE.md` — global rule updated with the same context-low handoff guidance.

---

## PR #6 Review Summary — What the Next Agent Must Know

Short verdict to send GLM/coder:

```text
Amend PR #6 on the same branch. Do not merge yet.
```

Main follow-up doc:

```text
docs/pr6-review-fix-prompts-2026-06-13.md
```

The two most dangerous remaining areas:

1. **Stripe webhook duplicate transaction on PostgreSQL**
   - Current shape catches `DataIntegrityViolationException` from `saveAndFlush()` inside `@Transactional`.
   - PostgreSQL can mark the transaction aborted; duplicate webhook delivery can still become 500.
   - Need PostgreSQL-safe idempotency such as `INSERT ... ON CONFLICT DO NOTHING` or an isolated claim-event transaction.

2. **Payment/Refund transaction boundaries**
   - Payment creation still passes a stale/detached `Order` into `PaymentTransactionService`.
   - Refund creation still touches lazy graph (`payment.getOrder()`, `payment.getRefunds()`) outside safe transaction.
   - Full refund relies on a false cascade assumption from `Payment` to `Order`.

Full findings in `docs/pr6-review-fix-prompts-2026-06-13.md`:

1. Duplicate Stripe webhook handling can still return 500.
2. Webhook malformed payload/header can return 500.
3. Missing local Payment for supported Stripe events can retry forever.
4. Payment creation still has concurrency/stale-entity risk.
5. Refund create flow accesses lazy JPA graph outside transaction.
6. Full refund order update may not persist.
7. Payment-by-order authorization leaks existence and returns wrong status.
8. Refund amount validation remains race-prone.
9. Role security misses `GET /api/bookings/vendor`.

---

## Phase Status and Next Fix Priority

| Phase | Current status | What must be fixed / verified next |
|-------|----------------|-------------------------------------|
| Phase 0 | Implemented; VI/EN learning docs present | No urgent code/doc issue found in this review path. Keep as baseline. |
| Phase 1 | Implemented; VI/EN learning docs present | No urgent issue found in this review path. Keep as domain model baseline. |
| Phase 2 | Implemented; VI/EN learning docs present | Recheck security/API claims after PR #6 role/auth fixes; old docs may overstate security completeness. |
| Phase 3 | Implemented; VI/EN learning docs present | Fix language-switch/internal HTML links. Later add stronger PostgreSQL/TestContainers coverage for booking overlap constraints. |
| Phase 4 | Implemented; VI/EN learning docs present; PR #6 in progress | Highest priority. Amend PR #6 for webhook/payment/refund/security blockers before merge. Recheck Phase 4 learning docs after code changes so docs do not teach stale transaction/security behavior. |
| Phase 5 | Not started | Redis caching pending. Start only after Phase 4 hardening and doc repair are stable. |
| Phase 6 | Not started | Frontend pending. Do not start yet. |
| Phase 7 | Not started | README/architecture/CI polish pending. Do after backend hardening/docs. |

Recommended order for the next session:

1. Review `docs/pr6-review-fix-prompts-2026-06-13.md`.
2. Send GLM the short verdict plus Section 4 copy-paste prompt from that doc.
3. After GLM amends PR #6, review the actual diff, not only GLM's summary.
4. Run `./mvnw test`.
5. Use Section 5 checklist in `docs/pr6-review-fix-prompts-2026-06-13.md` before allowing merge.
6. Only after PR #6 passes, repair stale/broken HTML docs and update phase learning docs.

---

## Blocking Issues / Decisions Needed

- Decide missing local Payment behavior for valid supported Stripe events:
  - Retry/alert with clear monitoring, or
  - Mark/ignore stale/cross-environment events to avoid permanent Stripe retries.
- Decide/confirm one-payment-per-order invariant:
  - Recommendation: enforce in DB with unique constraint and transactional recheck.
- Refund concurrency:
  - Recommendation: fix now because PR #6 already changes refund transaction boundaries.
- Role authorization strategy:
  - If URL matcher strategy remains primary, include all vendor-only routes such as `GET /api/bookings/vendor`.

---

## Paste-ready Prompt for New Session

Hien can paste this into the next session:

```text
We are in /Users/hiennguyen/Project/service-marketplace on branch fix/phase4-audit-correctness.

First, read these files in order:
1. CLAUDE.md
2. docs/session-notes/session-020.md
3. docs/pr6-review-fix-prompts-2026-06-13.md
4. docs/session-notes/session-019.md
5. tasks/todo.md

Then inspect quickly:
- git status --short
- gh pr view 6 --json title,headRefName,baseRefName,url,state
- gh pr diff 6 --name-only

Context:
- PR #6 is not safe to merge yet.
- Send GLM/coder this verdict first: "Amend PR #6 on the same branch. Do not merge yet."
- Then paste Section 4 of docs/pr6-review-fix-prompts-2026-06-13.md to GLM.
- After GLM amends PR #6, review the actual diff, run ./mvnw test, and use Section 5 checklist from the follow-up doc.

Do not start Phase 5 until PR #6 webhook/payment/refund/security blockers are fixed or explicitly deferred with documented rationale.
```

---

## Final Notes for Continuity

- This session intentionally did documentation/rules/context preservation only.
- Do not treat the passing `./mvnw test` result as proof PR #6 is safe; the identified blockers are mostly untested transaction/security edge cases.
- The follow-up doc under `docs/` is the source of truth for GLM/coder amendment instructions.
