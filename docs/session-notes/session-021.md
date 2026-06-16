# Session 021 — PR #7 Review / Phase 5 Audit

**Date:** 2026-06-16  
**Branch:** `feat/phase5-caching`  
**PR:** https://github.com/FrorsttzNguyen/service-marketplace/pull/7  
**Focus:** Review GLM5.2 Phase 5 PR, project docs, HTML learning docs, and Phase 0-4 status.

---

## What Was Done

- Reviewed actual PR #7 diff against `main`, not just the PR body.
- Reviewed Phase 5 backend code: Redis config, cache manager, cache annotations, rate-limit config/filter, security filter ordering, tests, and application config.
- Reviewed Phase 5 markdown docs: `docs/phase5-evaluation.md`, `docs/session-notes/session-020.md`, `docs/learning-roadmap.md`, `docs/todo.md`.
- Reviewed Phase 5 HTML learning docs under `docs/html/vi/phase5/`.
- Ran internal HTML link check across `docs/html`.
- Spot-checked Phase 0-4 against prior audit status and current source.
- Created `docs/pr-7-review.md` with findings, verdict, and coder prompt.
- Updated `tasks/todo.md` with current review checklist/results.

No production code was changed in this review.

---

## Current Project State

- **Current branch:** `feat/phase5-caching`
- **Open PR:** #7, `feat(phase5): Redis caching and auth rate limiting`
- **Merge status from GitHub:** clean
- **Local untracked files that existed before review:** `.agents`, `docs/learning-brief-phase0-1.md`
- **Review-created files:** `docs/pr-7-review.md`, `docs/session-notes/session-021.md`
- **Review-updated file:** `tasks/todo.md`

### Test State

Command run:

```bash
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
```

Result inside sandbox:

- `Tests run: 298, Failures: 0, Errors: 127, Skipped: 0`
- Root cause: Mockito inline Byte Buddy mock maker could not self-attach.

Result after Hien approved running outside sandbox:

- Same failure class: Mockito/Byte Buddy self-attach error.

This means the PR claim "297 pass, 1 fail" was not reproducible in this review session. Treat test health as **blocked** until the test JVM / Mockito agent config is fixed or CI evidence proves otherwise.

---

## Review Verdict

**Do not merge PR #7 yet.**

Primary blockers:

1. Full test suite is not reproducible on the current machine because Mockito/Byte Buddy inline mock maker cannot self-attach.
2. Redis env vars in `.env.example` are likely wrong for Spring Boot binding (`REDIS_HOST` does not become `spring.data.redis.host`), so non-dev Redis-backed rate limiting may not activate as intended.

Secondary findings:

- Current Phase 5 tests exercise simple/in-memory cache and rate-limit paths, not Redis-backed wiring.
- Cache eviction success path is not tested even though roadmap text says it is.
- Phase 5 HTML docs are strong in VI but EN docs are missing and language switch links are broken.
- `docs/todo.md` still has Phase 5 build/verify items unchecked while `docs/learning-roadmap.md` marks Phase 5 complete.

Full details: `docs/pr-7-review.md`.

---

## Learning Docs Status

### HTML Docs Coverage

| Phase | VI | EN | Status |
|---|---:|---:|---|
| Phase 0 | 6 files | 6 files | Complete; acceptable |
| Phase 1 | 5 files | 5 files | Complete; strong |
| Phase 2 | 6 files | 6 files | Complete; some roadmap checklist staleness remains |
| Phase 3 | 6 files | 6 files | Content exists; language-switch links broken |
| Phase 4 | 4 files | 4 files | Content exists; docs/index/link polish still needed |
| Phase 5 | 3 files | 0 files | VI content good; EN missing; language switch links broken |

HTML link check result:

- 72 HTML files
- 675 internal links checked
- 30 broken internal links
- Phase 5 added 3 broken language-switch links to missing EN files.

Phase 5 docs meet many content requirements: diagrams, callouts, tables, real project code snippets, "Tại sao" explanations, and actual pitfalls. They fall short on EN coverage, broken language switchers, and syntax-highlighting span usage.

---

## Phase 0-4 Quick Status

High-confidence summary:

- Phase 0-1 are broadly solid for learning.
- Phase 2-4 are learning-complete, but not production-polished.
- Several prior audit P0/P1 issues appear fixed on current `main`: payment-by-order ownership, vendor userId lookup, URL-level role guards, and payment/refund transaction self-invocation.

Still open before Phase 6/7:

1. `ServiceSpecification` exists but no public search endpoint uses it.
2. Booking DB constraint still only blocks same `start_time`, not overlapping time ranges.
3. `RefundStatus.PROCESSING` exists in Java but Flyway refund CHECK constraint omits it.
4. Test profile remains H2 + Hibernate create-drop + Flyway disabled; Testcontainers base classes exist but no tests extend them.
5. README references frontend, CI, API spec, C4/state/sequence docs that are not present or not ready.

---

## Next Session Instructions

Priority order:

1. **Amend PR #7 test infrastructure.** Make `./mvnw test` reproducible on Java 21/macOS. Likely direction: configure Mockito/Byte Buddy Java agent or Surefire `argLine`, then rerun full suite.
2. **Fix Redis env binding.** Use `SPRING_DATA_REDIS_HOST` style env vars or map `REDIS_HOST` placeholders into `spring.data.redis.*`.
3. **Update PR #7 truthfulness.** Remove stale "297 pass, 1 fail" claim unless reproduced after the test fix.
4. **Decide Redis integration test.** Add one smoke test or explicitly defer it while keeping Phase 5 test score conservative.
5. **Repair docs consistency.** Update `docs/todo.md`; translate Phase 5 EN docs or remove/fix broken language switch links.
6. After PR #7 is clean, continue Phase 4/5 deferred tasks before Phase 6 frontend: ServiceSpecification endpoint, booking overlap DB protection, RefundStatus CHECK fix, PostgreSQL/TestContainers tests, HTML link cleanup.

## Copy-Paste Prompt For Next Agent

```text
Review/amend PR #7 for service-marketplace. Start by reading:
- docs/pr-7-review.md
- docs/session-notes/session-021.md
- docs/session-notes/session-020.md

Current verdict is "do not merge yet".

First fix the test runner: `env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test` failed with 298 tests / 127 errors due Mockito inline Byte Buddy self-attach. Configure the test JVM so Maven tests are reproducible on Java 21/macOS, then rerun full suite.

Then fix Redis env binding: `.env.example` uses REDIS_HOST/REDIS_PORT/REDIS_PASSWORD but Phase 5 code activates Redis beans from `spring.data.redis.host`. Use Spring-recognized env vars or YAML placeholders.

After code/config fixes, update PR body and docs so test results and Phase 5 completion claims are truthful. Do not merge until tests are reproducible or CI evidence is provided.
```

---

## Blocking Issues / Decisions Needed

- Decide whether PR #7 must include a Redis integration test before merge. Reviewer recommendation: at least one smoke test is preferable; if not, explicitly defer and keep the lower test score.
- Decide whether Phase 5 should have exactly 3 learning docs or the general 01-06 navigation rule should apply. Current docs use 01-03 only.
- Decide whether Phase 6 frontend can start before fixing Phase 4/5 deferred correctness items. Reviewer recommendation: fix backend correctness/tests/docs first.
