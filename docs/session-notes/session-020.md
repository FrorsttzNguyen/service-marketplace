# Session 020 — Phase 5 Implementation (Redis Caching + Rate Limiting)

**Date:** 2026-06-16
**Focus:** Implement Phase 5 — Redis cache-aside + distributed rate limiting (Bucket4j). All 9 plan tasks completed.

---

## What Was Done

Implemented Phase 5 end-to-end on branch `feat/phase5-caching` (base: `main` @ `1e54de4`). 8 commits, 9 tasks:

1. **Dependencies:** `spring-boot-starter-cache`, `bucket4j-core` + `bucket4j-redis` (8.10.1 — 8.14.0 does not exist on Maven Central; verified directory listing).
2. **RedisConfig + CacheConfig:** `RedisTemplate` with JSON serialization (`GenericJackson2JsonRedisSerializer` + dedicated `redisObjectMapper` with java.time + `@class` type info). `RedisCacheManager` with per-cache TTL (catalog 5m, detail 15m, category 5m).
3. **`@Cacheable`:** only on `ServiceCatalogService.getServiceById` (returns a concrete record). `getAllServices` / `getServicesByCategory` deliberately NOT cached — see pitfall below.
4. **`@CacheEvict`:** `allEntries=true`, `beforeInvocation=false` on `createService`/`updateService`/`deactivateService`.
5. **Rate limiting:** `RateLimitConfig` (Lettuce `ProxyManager`, `@ConditionalOnProperty`), `RateLimitFilter` (token bucket: login 5/min, register 3/hr, refresh 10/min; 429 + Retry-After + ErrorResponse; XFF-aware IP). Registered before JWT filter in `SecurityConfig`.
6. **Tests:** `ServiceCatalogCachingTest` (5) + `RateLimitFilterTest` (7) = +12 tests. Suite 286 → 298.
7. **Config:** `application.yml` (`spring.cache.type=redis`, `app.ratelimit.enabled=true`); `application-test.yml` (`spring.cache.type=simple`, no `spring.data.redis.host`, `app.ratelimit.enabled=false`).
8. **Docs:** 3 VI HTML learning docs (cache-aside, invalidation, rate-limiting); roadmap + AGENTS.md/CLAUDE.md scores + todo.md updated. EN docs deferred.

---

## Key Pitfall Hit (important for reviewer)

**Jackson cannot deserialize `Page<T>` without a datatype module.** `jackson-datatype-spring-data` does not resolve on Maven Central for this Spring Data 3.5 / Jackson 2.19 stack. Caching `getAllServices(Pageable)` would: store fine on cache MISS, but return a `LinkedHashMap` on HIT → silent `ClassCastException`. Decision: cache only `getServiceById` (concrete record). Documented in `ServiceCatalogService` javadoc + doc 01.

---

## Test Profile Engineering (important for reviewer)

Phase 5 required making the test profile fully Redis-free:
- `RedisConfig` + `CacheConfig.cacheManager` + `RateLimitConfig` → `@ConditionalOnProperty(name="spring.data.redis.host")`.
- `application-test.yml` does NOT set `spring.data.redis.host` → those beans are absent → tests use `ConcurrentMapCacheManager` (fallback) + in-memory rate-limit buckets.
- `app.ratelimit.enabled=false` in test profile so existing auth/booking integration tests (shared context, repeated `/api/auth/**` calls) are not blocked by 429.

---

## ⚠️ Environment Issue (pre-existing, affects ALL tests)

The local `.env` sets `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/marketplace`. This env var **overrides** the H2 datasource in `application-test.yml` (env vars > YAML in Spring Boot), causing `"Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://..."`.

**Tests must be run with the datasource env vars unset:**
```bash
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
```
This reproduces on `main` (before Phase 5) — NOT a Phase 5 regression.

---

## Current Project State

- **Branch:** `feat/phase5-caching`
- **Base:** `main` @ `1e54de4`
- **Tests:** 298 total, **297 pass, 1 fail**.
  - The 1 failure is `AuthControllerIntegrationTest$FullAuthFlowTests.shouldAccessProtectedEndpointAfterLogin` (expected 404, got 400). **Reproduces on `main`** — pre-existing, unrelated to Phase 5.
- **No PR opened yet** — per AGENTS.md PR Review Handoff Rule, a dedicated review session should review the diff before merge.

### Commits (8)
```
de96772 chore(phase5): Add cache and rate-limiting dependencies
8a4aff6 feat(phase5): Add Redis cache configuration with JSON serialization
1fff420 feat(phase5): Cache getServiceById in Redis
2458543 feat(phase5): Evict serviceDetail cache on vendor mutations
a1e11e5 feat(phase5): Add Redis-backed rate limiting for auth endpoints
1dc10bf test(phase5): Add cache and rate-limit tests
3b2b9cf chore(phase5): Document cache + rate-limit config in application yml
9a54118 docs(phase5): Mark Phase 5 complete in learning roadmap
```

### Files changed
- `pom.xml`, `.env.example`
- `config/RedisConfig.java` (new), `config/CacheConfig.java` (new), `config/RateLimitConfig.java` (new), `config/SecurityConfig.java` (modified)
- `application/service/ServiceCatalogService.java` (modified), `application/service/VendorServiceManagement.java` (modified)
- `infrastructure/security/RateLimitFilter.java` (new)
- `application.yml`, `application-test.yml`
- `docs/phase5-evaluation.md` (new), `docs/learning-roadmap.md`, `docs/todo.md`, `AGENTS.md`, `CLAUDE.md`, `docs/session-notes/session-020.md` (this)
- `docs/html/vi/phase5/{styles.css,01,02,03}.html` (local-only, gitignored)
- `docs/html/index.html` (local-only, gitignored)

---

## Phase 5 Score: 8.05/10 (lowest phase — honest)

```
Learning Docs   30%  8.0  (VI only, EN deferred)
Code Quality    30%  8.5  (Page<T> not cached; rate-limit test toggle)
Test Coverage   20%  7.5  (no Redis integration test)
Concept Mastery 20%  8.0  (teachable, not yet demonstrated)
```
See `docs/phase5-evaluation.md` for full evidence + gaps.

---

## Next Session Instructions

### Priority 1: Review PR (do NOT merge yet)
Per AGENTS.md PR Review Handoff Rule:
1. **Review the actual diff** of `feat/phase5-caching` vs `main` — do not rely on this summary.
2. **Run tests** with the env workaround:
   ```bash
   git checkout feat/phase5-caching
   env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
   # Expected: 298 tests, 1 pre-existing failure
   ```
3. **Write a review follow-up file** under `docs/` (e.g. `docs/pr-7-review.md`) with findings before merging.
4. If clean: `gh pr create` → squash merge.

### Priority 2 (after merge): Fix the test environment issue
The `.env` overriding `application-test.yml` datasource breaks tests for every developer. Options:
- Add `SPRING_PROFILES_ACTIVE=test` handling that ignores `.env`.
- Or document `env -u ... ./mvnw test` in README.
- Or move H2 config to `application.yml` with `@TestPropertySource` overrides.

### Priority 3 (deferred Phase 5 P2 items)
1. **Page<T> caching:** find correct `jackson-datatype-spring-data` coordinate, or cache a `List`+count DTO.
2. **Redis integration test:** add `@Testcontainers` Redis or CI Redis service + one smoke test.
3. **EN docs:** translate Phase 5 docs 01–03.
4. **Remove `PageableKeyGenerator`** dead code (CacheConfig) if Page caching is not pursued.

### Phase 4 audit tasks 6–10 still open (from glm-fix-plan)
- Task 6: Wire `ServiceSpecification` into `/api/services/search`.
- Task 7: PostgreSQL/TestContainers tests (migrations, spec, webhook idempotency).
- Task 8: Booking overlap DB exclusion constraint (V10).
- Task 9: RefundStatus DB CHECK constraint fix (V10).
- Task 10: Repair HTML docs index/links.

---

## Copy-paste prompt for the reviewer session

```text
Review branch feat/phase5-caching (Phase 5: Redis caching + rate limiting). 
Read docs/session-notes/session-020.md and docs/phase5-evaluation.md first.
Review the actual git diff against main — do not trust the summary.
Run tests with: env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test
Expected: 298 tests, 1 pre-existing failure (shouldAccessProtectedEndpointAfterLogin, reproduces on main).
Key decisions to verify:
  1. Only getServiceById is cached (Page<T> not — see ServiceCatalogService javadoc WHY).
  2. @CacheEvict beforeInvocation=false on vendor mutations.
  3. Rate limit filter before JWT filter in SecurityConfig.
  4. Test profile is Redis-free via @ConditionalOnProperty.
Write findings to docs/pr-7-review.md. Do not merge until review passes.
```

---

## Blocking Issues / Decisions Needed

1. Whether to merge Phase 5 with the P2 gaps (Page<T>, Redis test) or block on them. **Recommendation:** merge — gaps are documented and defensible.
2. Whether to fix the `.env` datasource override before or after merge. **Recommendation:** after — it's pre-existing and affects all phases.
3. Next phase direction after Phase 5 merge: Phase 4 audit tasks (6–10), or Phase 6 frontend. Hien previously chose "Phase 4 dọn + Phase 5 + Phase 6 minimal" — but audit tasks 6–10 are still open and were explicitly deferred.
