# PR #7 Fix Plan — Phase 5 Stabilization

**Date:** 2026-06-16  
**Target PR:** https://github.com/FrorsttzNguyen/service-marketplace/pull/7  
**Branch:** `feat/phase5-caching`  
**Status:** Implemented in the 2026-06-16 PR #7 fix session; keep this file as the original repair checklist.

**Latest verification:** `env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test` passes with **299 tests, 0 failures, 0 errors, 0 skipped** after the amendments.

---

## Goal

Stabilize PR #7 so Hien can safely review and merge Phase 5. The priority is not adding more Phase 5 features; it is making the current Redis caching/rate-limit work truthful, testable, and correctly configured.

## Scope

### In scope

- Fix Maven test runner failure caused by Mockito/Byte Buddy self-attach.
- Fix Redis environment binding so documented env vars actually activate `spring.data.redis.*`.
- Make Phase 5 test/docs claims truthful.
- Add or explicitly defer Redis-backed integration coverage.
- Fix Phase 5 tracking docs and PR body claims.

### Out of scope for this PR

- Phase 6 frontend.
- Full HTML docs cleanup for all phases unless touching Phase 5 docs anyway.
- Booking overlap DB exclusion constraint.
- `ServiceSpecification` public search endpoint.
- Refund CHECK constraint migration.

Those remain important, but they should be separate follow-up PRs after PR #7 is stable.

---

## Action Items

### 1. Reproduce the test blocker first

Run:

```bash
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
```

Expected current failure:

- `Tests run: 298, Failures: 0, Errors: 127`
- Root cause: Mockito inline Byte Buddy mock maker cannot self-attach.

Do not start feature edits until this is reproduced or disproved.

### 2. Stabilize Mockito/Byte Buddy test execution

Primary target: `pom.xml`.

Recommended direction:

- Configure Maven Surefire with a stable JVM setup for Mockito inline mocking on Java 21/macOS.
- Prefer an explicit Java agent / Surefire `argLine` solution over relying on runtime self-attach.
- Keep the fix minimal and explain why in a short XML comment.

Verification:

```bash
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
```

If one pre-existing functional failure remains after the runner is fixed, document the exact test and confirm whether it reproduces on `main`.

### 3. Fix Redis environment binding

Current problem:

- `.env.example` documents `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`.
- Phase 5 code activates Redis config via `spring.data.redis.host`.
- Spring Boot does not automatically map `REDIS_HOST` to `spring.data.redis.host`.

Acceptable fix options:

1. Change `.env.example` to use Spring-recognized names:
   - `SPRING_DATA_REDIS_HOST`
   - `SPRING_DATA_REDIS_PORT`
   - `SPRING_DATA_REDIS_PASSWORD`
2. Or map the current variables in YAML:
   - `spring.data.redis.host: ${REDIS_HOST:localhost}`
   - `spring.data.redis.port: ${REDIS_PORT:6379}`
   - `spring.data.redis.password: ${REDIS_PASSWORD:}`

Keep dev/test behavior unchanged:

- Dev still connects to local Docker Redis.
- Test profile still has no `spring.data.redis.host` unless a Redis integration test explicitly opts in.

### 4. Make cache eviction test claims truthful

Current issue:

- `ServiceCatalogCachingTest.updateServiceEvictsCache` verifies failure-path behavior: cache remains when mutation throws.
- It does **not** verify successful mutation eviction.

Fix either by:

- Adding a success-path eviction test with valid vendor/category/service fixture, or
- Renaming the test and updating `docs/learning-roadmap.md` / `docs/phase5-evaluation.md` so they do not claim successful update eviction is tested.

Preferred: add the success-path test if it can be done without overcomplicated fixture setup.

### 5. Decide Redis-backed integration coverage

Current Phase 5 tests use:

- Simple in-memory cache.
- In-memory rate-limit bucket fallback.

Preferred fix:

- Add one Redis-backed smoke test using Testcontainers Redis or another controlled Redis test service.
- Verify at least one Redis path that current tests cannot cover:
  - `RedisCacheManager` can serialize/deserialize `ServiceResponse`.
  - Bucket4j `LettuceBasedProxyManager` consumes tokens through Redis.

If deferred:

- Keep the defer explicit in `docs/phase5-evaluation.md`.
- Keep Phase 5 test score conservative.
- Do not claim Redis wiring is tested.

### 6. Repair Phase 5 docs consistency

Update:

- `docs/todo.md`
- `docs/learning-roadmap.md`
- `docs/phase5-evaluation.md`
- PR body, if needed

Required corrections:

- `docs/todo.md` Phase 5 checklist should reflect actual completed/deferred items.
- Remove or clarify claims that successful cache eviction is tested unless a success-path test is added.
- Update test results after the real rerun.
- Keep EN Phase 5 docs marked deferred unless they are actually created.

### 7. Optional local HTML cleanup

If touching HTML docs:

- Fix Phase 5 language switch links, or create EN docs.
- Re-run internal link checker.

Do not let HTML cleanup distract from P0/P1 code/test blockers.

### 8. Final verification before review

Required:

```bash
git status --short --branch
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
```

If docs/html links are changed:

```bash
node -e '<internal href checker>'
```

Update PR #7 body with the exact final test result.

---

## Review Checklist After Amend

Reviewer should verify:

- Test suite result is reproducible.
- Redis env binding is correct for dev/prod/test.
- No new Redis requirement leaks into default test profile.
- `RateLimitFilter` still runs before JWT.
- `getServiceById` cache remains intentional; Page caching remains deferred or correctly implemented.
- Docs no longer overclaim test coverage.

---

## Copy-Paste Prompt For Codex Fix Session

```text
Hien wants PR #7 amended, then reviewed again.

Read these first:
- AGENTS.md
- docs/pr-7-review.md
- docs/pr-7-fix-plan.md
- docs/session-notes/session-021.md
- docs/session-notes/session-022.md

Branch: feat/phase5-caching
PR: https://github.com/FrorsttzNguyen/service-marketplace/pull/7

Goal: stabilize PR #7, not expand scope.

Start by reproducing:
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test

Known blocker: Mockito/Byte Buddy inline mock maker cannot self-attach on Java 21/macOS, causing 127 test errors. Fix Maven/Surefire/Mockito config so the full suite is reproducible.

Then fix Redis env binding: .env.example uses REDIS_HOST/REDIS_PORT/REDIS_PASSWORD, but Phase 5 beans activate from spring.data.redis.host. Use Spring-recognized env names or YAML placeholders.

Then make docs/test claims truthful:
- Add cache eviction success-path test or correct wording.
- Add Redis-backed smoke test or explicitly defer it.
- Update docs/todo.md, docs/learning-roadmap.md, docs/phase5-evaluation.md, and PR body with actual test results.

After amendments, run the full test command again and summarize exact results. Do not merge PR #7 until review passes.
```
