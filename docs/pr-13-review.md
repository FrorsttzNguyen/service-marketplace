# PR #13 Review — Phase 6 Slice 3: Prod config + observability

- **PR:** [#13 feat(observability): prod profile, prometheus metrics, JSON logging, OpenAPI export](https://github.com/FrorsttzNguyen/service-marketplace/pull/13)
- **Branch:** `feat/phase6-prod-config` → `main`
- **Reviewer:** (Opus). Coder: GLM.
- **Date:** 2026-06-17
- **Spec:** `docs/phase6-production-readiness-spec.md` (Slice 3)
- **Files:** `pom.xml`, `src/main/resources/application.yml`, `src/main/resources/application-prod.yml` (new),
  `.env.example`, `src/main/java/.../config/SecurityConfig.java`, `docs/api/openapi.yaml` (new), this doc.

## Verdict

**APPROVE.** All Slice 3 acceptance met and **verified by real runs** (boot + curl), not asserted. One scope
addition beyond the literal task list: **springdoc 2.6.0 → 2.8.9** (justified below — it was a pre-existing
breakage blocking the mandatory OpenAPI export, fixed via the same pom.xml Slice 3 already edits).

## Scope vs. task list

| Task | Done | Notes |
|------|------|-------|
| `micrometer-registry-prometheus` (runtime) in pom.xml | ✅ | `runtime` scope — correct |
| Expose `health, info, prometheus, metrics` in common application.yml | ✅ | exposure separated from access control |
| `application-prod.yml` new — env-only secrets, JSON/ECS logging, validate + Flyway on | ✅ | `${JWT_SECRET}` etc. with **no default** → fail-fast |
| `JWT_SECRET` placeholder in `.env.example` | ✅ | documented min length + `openssl rand` hint |
| SecurityConfig actuator lockdown (health/info permitAll; `/actuator/**` → ADMIN) | ✅ | order-correct (public rules before catch-all) |
| Export OpenAPI → `docs/api/openapi.yaml`, `/api/admin/**` present | ✅ | 37 KB, OpenAPI 3.1.0, 29 paths, 3 admin paths |
| `./mvnw -B verify` green | ✅ | 308 tests, 0 failures |
| **Bonus:** springdoc 2.6.0 → 2.8.9 | ✅ | see below |

## Why springdoc was bumped (the one scope addition)

OpenAPI export is a **mandatory Slice 3 acceptance** ("Export OpenAPI to docs/api/openapi.yaml"). On first
attempt `/v3/api-docs.yaml` returned **HTTP 500** with:

```
NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
```

Root cause: **pre-existing**, not introduced by this slice. springdoc `2.6.0` (already on `main`) was
compiled against Spring Framework 6.1, but the project runs Spring Boot 3.5.0 → Spring Framework 6.2.x,
which removed that constructor. It fires whenever a `@RestControllerAdvice` is present (we have
`GlobalExceptionHandler`). Tracked upstream: springdoc-openapi issues #2687, #3041, #3045.

The bump is a **dependency/infra fix in the same pom.xml** Slice 3 already edits (Slice 3's own template
says "Add the micrometer-registry-prometheus dependency if missing" — same file, same category). It is NOT
application code. Without it, the OpenAPI acceptance is unmeetable. Flagging explicitly for transparency.

## What I verified (real runs)

### H2 suite (twice — before and after the springdoc bump)
```
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw -B verify
→ Tests run: 308, Failures: 0, Errors: 0, Skipped: 0   → BUILD SUCCESS
```
The actuator lockdown broke no tests (no test referenced `/actuator/*`).

### Prod-profile boot (throwaway postgres:16-alpine :5435 + redis:7-alpine :6379, `SPRING_PROFILES_ACTIVE=prod`)
- Active profile confirmed: `"The following 1 profile is active: \"prod\""`.
- Flyway ran all 10 migrations on real Postgres: `Successfully applied 10 migrations ... now at version v10`.
- `validate` PASS — no `Schema-validation` / `SchemaManagementException` (the V10 fix from Slice 4 holds).
- `${JWT_SECRET}` resolved (no "could not resolve placeholder") → fail-fast contract honored.
- `Started ServiceMarketplaceApplication in ~2.8s`.

### Structured (JSON/ECS) logging — actually on under prod
Every console line is a JSON object with ECS fields (no extra library — Spring Boot 3.4+ built-in). Sample:
```json
{"@timestamp":"2026-06-16T19:41:22.851068Z","log":{"level":"INFO","logger":"com.hien.marketplace.ServiceMarketplaceApplication"},"process":{"pid":23909,"thread":{"name":"restartedMain"}},"service":{"name":"service-marketplace","node":{}},"message":"The following 1 profile is active: \"prod\"","ecs":{"version":"8.11"}}
```
Dev/test profiles unchanged (human-readable) — override only applies under `prod`.

### Actuator access control (the security-relevant check)
| Endpoint | No auth | With ADMIN JWT |
|----------|---------|----------------|
| `/actuator/health` | **200** `{"status":"UP"}` | n/a |
| `/actuator/info` | **200** | n/a |
| `/actuator/prometheus` | **403 Forbidden** | **200** `text/plain;version=0.0.4` (real metrics) |
| `/actuator/metrics` | **403** | **200** JSON metric list |

Prometheus metric samples returned with the ADMIN token: `application_started_time_seconds`,
`cache_gets_total`, `disk_free_bytes`, `executor_*`, etc. (content-type is the Prometheus exposition format).
Sanity: `/api/admin/vendors` with the same ADMIN JWT → **200** (admin authorization still works).

### OpenAPI export
```
curl http://localhost:8080/v3/api-docs.yaml → HTTP 200, 37632 bytes
openapi: 3.1.0
title: Service Marketplace API
29 paths total
/api/admin paths: /api/admin/vendors, /api/admin/vendors/{vendorId}/approve, /api/admin/vendors/{vendorId}/reject
```

## Findings

| # | Finding | Severity | Action |
|---|---------|----------|--------|
| 1 | springdoc 2.6.0→2.8.9 was needed to make OpenAPI export work at all (pre-existing incompat with Spring Boot 3.5) | Info | Already done in this PR; documented in pom.xml comment + here. |
| 2 | `/actuator/info` returns `{}` (empty) — no `git-info`/`build-info` actuator contributors wired | Low (optional) | Nice-to-have for a later slice (expose commit/build via `build-info` goal). Not blocking. |
| 3 | Prometheus is ADMIN-gated by default; Hien may later want it public for an unauthenticated scrape | Decision (Hien) | The code comment in SecurityConfig documents the one-line change to open ONLY `/actuator/prometheus` if desired. Do NOT loosen `/actuator/**`. |
| 4 | `app.jwt.access-expiration-minutes` / `refresh-expiration-days` are NOT overridden in prod → inherit common defaults (15m / 7d) | Info | Intentional; sensible defaults. Override via env only if needed. |

Non-findings (verified fine):
- No literal secrets anywhere in prod profile.
- `show-sql: false`, `org.hibernate.SQL: WARN` in prod (no SQL noise).
- Dev/test profiles untouched.

## Verify commands (for reviewer)

```bash
# 1. H2 suite
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw -B verify
# expect: Tests run: 308, Failures: 0 → BUILD SUCCESS

# 2. Prod boot against throwaway pg+redis
docker run -d --name s3-pg -e POSTGRES_USER=marketplace -e POSTGRES_PASSWORD=marketplace \
  -e POSTGRES_DB=marketplace -p 5435:5432 postgres:16-alpine
docker run -d --name s3-redis -p 6379:6379 redis:7-alpine
env SPRING_PROFILES_ACTIVE=prod \
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5435/marketplace \
  SPRING_DATASOURCE_USERNAME=marketplace SPRING_DATASOURCE_PASSWORD=marketplace \
  SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=6379 \
  JWT_SECRET='prod-verify-256-bit-secret-aaaaaaaaaaaaaaaaaaaaaaaa' \
  STRIPE_API_KEY=sk_test_x STRIPE_WEBHOOK_SECRET=whsec_x \
  ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=AdminPass123 \
  ./mvnw spring-boot:run
# expect: log lines are JSON (ECS); Started ServiceMarketplaceApplication; Flyway v10

# 3. health + actuator lockdown
curl -s http://localhost:8080/actuator/health            # 200 {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/prometheus   # 403
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"AdminPass123"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/prometheus | head   # 200 + metrics text

# 4. OpenAPI export + admin paths
curl -s http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml
grep -c '/api/admin' docs/api/openapi.yaml   # >=1

# cleanup
pkill -f spring-boot:run; docker rm -f s3-pg s3-redis
```

## Verdict for Hien

**Merge PR #13** once CI is green. Phase 6 now has: CI (Slice 1), Docker stack (Slice 2), audit jsonb fix
(Slice 4), and prod config + observability + OpenAPI (Slice 3). That leaves **Slice 5 (deploy)** as the only
remaining slice — and it's now unblocked (the image boots in compose + prod profile works).

## Next after merge

- Slice 5 (`feat/phase6-deploy`): pick a free-tier host (spec recommends **Render**), Hien performs the
  deploy with the prod env vars (`JWT_SECRET`, `STRIPE_*`, `ADMIN_*`, DB/Redis from the host's managed
  services). README gets the CI badge + live URL + Swagger link. `/actuator/health` must be UP in prod.
