# Session 022 — PR #7 Fix Preparation

**Date:** 2026-06-16  
**Branch:** `feat/phase5-caching`  
**PR:** https://github.com/FrorsttzNguyen/service-marketplace/pull/7  
**Focus:** Prepare the next Codex session to amend PR #7.

---

## What Was Done

- Created `docs/pr-7-fix-plan.md` with the ordered repair plan for PR #7.
- Updated `tasks/todo.md` with a new "PR #7 Fix Session Preparation" checklist.
- Preserved the prior review result in `docs/pr-7-review.md`.
- Kept the current verdict unchanged: **do not merge PR #7 yet**.

No production code was changed in this preparation step.

---

## Current Project State

- **Current branch:** `feat/phase5-caching`
- **Open PR:** #7, `feat(phase5): Redis caching and auth rate limiting`
- **Review verdict:** do not merge yet
- **Review follow-up:** `docs/pr-7-review.md`
- **Fix plan:** `docs/pr-7-fix-plan.md`
- **Previous handoff:** `docs/session-notes/session-021.md`
- **This handoff:** `docs/session-notes/session-022.md`

Local untracked files noted from previous review:

- `.agents`
- `docs/learning-brief-phase0-1.md`

Do not delete or revert them unless Hien explicitly asks.

---

## Learning Docs Status

| Phase | VI HTML | EN HTML | Status |
|---|---:|---:|---|
| Phase 0 | 6 | 6 | Complete enough for learning |
| Phase 1 | 5 | 5 | Strong |
| Phase 2 | 6 | 6 | Complete, some checklist staleness |
| Phase 3 | 6 | 6 | Content exists, broken language-switch links |
| Phase 4 | 4 | 4 | Content exists, link/index polish needed |
| Phase 5 | 3 | 0 | VI content good, EN missing, language links broken |

Last HTML link check from Session 021:

- 72 HTML files
- 675 internal links
- 30 broken links

Phase 5 docs are not the first thing to fix unless a code/doc claim is touched. The immediate blocker is test reproducibility.

---

## Priority Order For Next Session

### 1. Fix test runner first

Run:

```bash
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
```

Known failure:

- `298 tests`
- `127 errors`
- Mockito inline Byte Buddy mock maker cannot self-attach.

Likely file:

- `pom.xml`

Expected outcome:

- Full test suite runs reproducibly on Java 21/macOS.
- If a real functional failure remains, document exact test name and whether it reproduces on `main`.

### 2. Fix Redis env binding

Known mismatch:

- `.env.example` uses `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`.
- Phase 5 beans activate from `spring.data.redis.host`.

Likely files:

- `.env.example`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- possibly `src/test/resources/application-test.yml`

Expected outcome:

- Dev/prod env vars actually bind to `spring.data.redis.*`.
- Test profile remains Redis-free unless a Redis integration test explicitly opts in.

### 3. Fix Phase 5 test/doc truthfulness

Current issue:

- Existing cache eviction test verifies failure-path "do not evict on throw", not success-path eviction.
- Current tests do not prove Redis-backed wiring.

Options:

- Add success-path eviction test, or correct docs/test names.
- Add Redis-backed smoke test, or explicitly defer and keep score conservative.

Likely files:

- `src/test/java/com/hien/marketplace/application/service/ServiceCatalogCachingTest.java`
- `src/test/java/com/hien/marketplace/infrastructure/security/RateLimitFilterTest.java`
- `docs/learning-roadmap.md`
- `docs/phase5-evaluation.md`
- `docs/todo.md`

### 4. Update PR body and re-review

After fixes:

- Rerun full Maven test command.
- Update PR body with exact result.
- Re-review the actual diff.
- Keep **do not merge yet** until review passes.

---

## Files To Read First

1. `AGENTS.md`
2. `docs/pr-7-review.md`
3. `docs/pr-7-fix-plan.md`
4. `docs/session-notes/session-021.md`
5. `docs/session-notes/session-022.md`
6. `pom.xml`
7. `.env.example`
8. `src/main/resources/application.yml`
9. `src/test/resources/application-test.yml`

---

## Copy-Paste Prompt For New Codex Session

```text
Hien wants PR #7 amended, then reviewed again.

Start by reading:
- AGENTS.md
- docs/pr-7-review.md
- docs/pr-7-fix-plan.md
- docs/session-notes/session-021.md
- docs/session-notes/session-022.md

Current branch should be feat/phase5-caching. PR #7 is not merge-ready.

Goal: stabilize PR #7, not expand scope.

Priority 1: make Maven tests reproducible.
Run:
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test

Known failure from prior review: 298 tests, 127 errors, root cause Mockito inline Byte Buddy mock maker cannot self-attach on Java 21/macOS. Fix Maven/Surefire/Mockito config so tests run reliably.

Priority 2: fix Redis env binding. .env.example uses REDIS_HOST/REDIS_PORT/REDIS_PASSWORD, but Phase 5 beans activate from spring.data.redis.host. Use Spring-recognized env names or YAML placeholders, while keeping test profile Redis-free.

Priority 3: make Phase 5 test/docs claims truthful. Add a cache eviction success-path test or correct wording. Add a Redis-backed smoke test or explicitly defer it. Update docs/todo.md, docs/learning-roadmap.md, docs/phase5-evaluation.md, and PR body.

After amendments, rerun full tests and review the diff. Do not merge PR #7 until review passes.
```

---

## Blocking Issues / Decisions

- Whether PR #7 must include a Redis integration smoke test before merge. Recommendation: yes if quick; otherwise explicitly defer and do not overclaim.
- Whether Phase 5 needs EN HTML docs before merge. Recommendation: not required for code merge, but required before docs are called complete.
- Whether to start Phase 6 before backend cleanup. Recommendation: fix PR #7 first, then address remaining backend correctness/docs gaps before frontend work.
