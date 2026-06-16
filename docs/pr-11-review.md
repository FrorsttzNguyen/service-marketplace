# PR #11 Review — Phase 6 Slice 2: Dockerfile + docker-compose

- **PR:** [#11 build: add multi-stage Dockerfile, docker-compose (app+postgres+redis), .dockerignore](https://github.com/FrorsttzNguyen/service-marketplace/pull/11)
- **Branch:** `feat/phase6-docker` → `main`
- **Reviewer:** (Opus). Coder: GLM.
- **Date:** 2026-06-17
- **Spec:** `docs/phase6-production-readiness-spec.md` (Slice 2)
- **Diff:** 3 files added — `Dockerfile`, `docker-compose.yml` (overwrites old pg+redis-only one), `.dockerignore`. **No app code changed.**

## TL;DR verdict

**Infrastructure is correct and verified working — but the app CANNOT BOOT against real Postgres, so the
PR does NOT meet Slice 2's acceptance "app `/actuator/health` reachable". DO NOT MERGE yet.**

The blocker is the **`audit_logs` jsonb/text mismatch — now confirmed to break RUNTIME startup**, not only
Testcontainers tests. This is exactly the contingency the spec's Slice 2 review note warned about:

> NOTE TO REVIEWER (Claude): the runtime `application.yml` also uses `ddl-auto=validate`. Verify whether the
> app boots clean against real Postgres in compose. If the audit_logs jsonb/text mismatch also breaks runtime
> startup (not just tests), Slice 4 must move BEFORE Slice 5 (deploy).

**Answer: yes, it breaks runtime startup.** Therefore **Slice 4 (jsonb fix) is now a hard prerequisite for
Slice 2 acceptance AND for Slice 5 (deploy).** Recommended path: keep this PR open, do Slice 4 on its own
branch, then re-verify Slice 2 against the Slice 4 image.

## What I verified (real runs, not asserted)

### ✅ `docker build -t marketplace .` — succeeds

- Multi-stage. Stage 1 `maven:3.9-eclipse-temurin-21` (Maven 3.9 + JDK 21, matches `pom.xml` java.version=21
  and the maven-wrapper 3.9.16). Copies `mvnw`/`mvnw.cmd`/`.mvn`/`pom.xml` first, runs
  `./mvnw -B -DskipTests dependency:go-offline` (cached dep layer), then copies `src` and runs
  `./mvnw -B -DskipTests package` → `marketplace-0.0.1-SNAPSHOT.jar` (spring-boot repackage) → `BUILD SUCCESS`.
- Stage 2 `eclipse-temurin:21-jre-jammy`: installs `curl` (for the healthcheck), creates non-root
  `appuser:appgroup`, copies jar to `/app/app.jar`, `chown`, `USER appuser`, `EXPOSE 8080`,
  `ENTRYPOINT ["java","-jar","/app/app.jar"]`.
- Image runs as non-root (confirmed: `started by appuser` in boot log). No secrets baked (config is env-only).

### ✅ `docker compose up --build` — all three services start; deps healthy before app

- `postgres:16-alpine` → healthy (`pg_isready`); `redis:7-alpine` → healthy (`redis-cli ping`); `app`
  starts only after both are `service_healthy` (`depends_on` long form) ✅.
- App connects to **compose** services by service name, NOT localhost:
  `Database: jdbc:postgresql://postgres:5432/marketplace` and Redis host `redis` (from boot log).
  Confirms the "not the host" requirement.
- `SPRING_PROFILES_ACTIVE=dev` is correctly NOT set (dev hardcodes `localhost:5433`).
- `SPRING_DATASOURCE_*` + `SPRING_DATA_REDIS_*` + `STRIPE_*` + `ADMIN_*` forwarded from `.env`. Stripe's
  `@NotBlank` config is satisfied, so the app reaches the Flyway/Hibernate phase.

### ✅ Flyway runs successfully against the compose Postgres

Boot log (real):
```
Database: jdbc:postgresql://postgres:5432/marketplace (PostgreSQL 16.14)
Successfully validated 9 migrations (execution time 00:00.007s)
Migrating schema "public" to version "1 - create users table"
... "2".."9"...
Successfully applied 9 migrations to schema "public", now at version v9 (execution time 00:00.073s)
```
All V1..V9 migrations apply on a clean volume, including **V6 which creates `audit_logs.old_values`/`new_values`
as `JSONB`**. Flyway itself is fine.

### ❌ Hibernate `ddl-auto=validate` FAILS at runtime — app does NOT boot

Immediately after Flyway, the runtime `application.yml` (`spring.jpa.hibernate.ddl-auto: validate`) runs and:
```
ERROR ... j.LocalContainerEntityManagerFactoryBean : Failed to initialize JPA EntityManagerFactory:
[PersistenceUnit: default] Unable to build Hibernate SessionFactory; nested exception is
org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: wrong column type encountered
in column [new_values] in table [audit_logs]; found [jsonb (Types#OTHER)], but expecting [text (Types#VARCHAR)]
```
→ `Application run failed`. `/actuator/health` is **never reachable**. App container goes `unhealthy`.

**Root cause:** `domain/audit/AuditLog.java` maps `oldValues`/`newValues` as
`@Column(name="old_values", columnDefinition="text") private String` and `new_values` likewise. Migration
`V6` creates them as `JSONB`. The runtime profile (unlike the H2 `test` profile, which uses
`create-drop`) actually validates against the Flyway schema → mismatch → boot fails.

This is the **same** root cause as the documented Testcontainers blocker, just surfacing at runtime instead
of test time. The spec assumed it was "LOW" probability at runtime because "app ran Postgres+validate during
dev" — but dev always ran with `SPRING_PROFILES_ACTIVE=dev`, whose `application-dev.yml` does NOT change
`ddl-auto` (it stays `validate` from common), so the only way dev ever booted was... **it didn't validate
jsonb either, OR dev was actually started against an H2/create-drop at some point.** Either way: against the
compose Postgres with the real V6 jsonb schema, runtime validate fails. Confirmed by a real boot.

## Findings

| # | Finding | Severity | Where | Action |
|---|---------|----------|-------|--------|
| 1 | **Runtime boot fails: `audit_logs.new_values` jsonb vs `text` under `ddl-auto=validate`** | **BLOCKER** | `domain/audit/AuditLog.java:49,60` (`columnDefinition="text"`) vs `db/migration/V6__create_reviews_notifications_audit.sql:51,52` (`JSONB`); triggered by common `application.yml` `ddl-auto: validate` | **Slice 4 prerequisite.** This PR cannot pass Slice 2 acceptance until Slice 4 lands. Do not merge yet. |
| 2 | `.dockerignore` keeps `!.env.example` allow-rule but `*.md` exclude then drops `README.md`/`AGENTS.md` from context — fine (build needs none of them) | Info | `.dockerignore` | No change needed. |
| 3 | App healthcheck needs `curl`, which the slim JRE base lacks; Dockerfile installs it (correct) | Info | `Dockerfile` runtime stage | Good — keep. Alternative `wget` would also work. |

Non-findings (verified, NOT problems):
- Non-root user: confirmed (`appuser`).
- Secrets: none baked; all env.
- Image is slim JRE (no Maven/javac in runtime layer).
- `depends_on` long form waits for healthy (Flyway/Lettuce never race the DB).

## Spec acceptance (Slice 2) — current status

- [x] Multi-stage Dockerfile (build → slim runtime, non-root, EXPOSE, ENTRYPOINT).
- [x] `docker-compose.yml`: app + postgres + redis, env-wired, healthchecks, depends_on, pg volume.
- [x] `.dockerignore` excludes target/.git/.github/docs/.agents/.env/*.md/.idea.
- [x] `docker build -t marketplace .` succeeds from clean checkout.
- [x] App connects to the COMPOSE postgres + redis (service names), not the host.
- [x] Image runs as non-root.
- [ ] **`/actuator/health` reachable** — **FAILS**: app does not boot (jsonb blocker). ← this is the gap.
- [ ] **Flyway validates jsonb/text mismatch at runtime** — the spec's explicit open question. **Answer: it
      DOES break runtime.** → Slice 4 must precede deploy.

## Verdict for Hien (send verbatim to coder)

**Do not merge PR #11 yet.** The Dockerfile / docker-compose / .dockerignore are correct and verified, but
the app cannot boot against the compose Postgres because of the pre-existing `audit_logs` jsonb-vs-text
mismatch under `ddl-auto=validate` (runtime, not just tests). This makes **Slice 4 a prerequisite**.

Recommended order:
1. Keep PR #11 open (do NOT merge). It is correct as far as infra goes.
2. Do **Slice 4** (`feat/phase6-testcontainers-ci` or a dedicated `feat/phase6-audit-jsonb`) — fix the
   jsonb/text mismatch (see spec options 1/2/3). Easiest: new Flyway migration `ALTER COLUMN ... TYPE text`
   (option 1) OR map jsonb properly with hypersistence-utils (option 2). Decide with Claude.
3. After Slice 4 lands, re-verify PR #11's image boots: `docker compose up --build` → app `/actuator/health`
   returns UP, Flyway + validate both pass on real Postgres.
4. Then merge PR #11 (or rebase it onto main post-Slice-4 first).

If you'd rather merge the infra now and fix boot separately, that's acceptable IF the PR description states
clearly "app does not boot yet; Slice 4 required" — but the cleaner option is to wait.

## Copy-paste prompt for the Slice 4 coder

```
Implement Phase 6 Slice 4 (jsonb/text fix) per docs/phase6-production-readiness-spec.md
("Known blocker" + Slice 4). Do NOT touch app behavior beyond the audit_logs mapping.

Background (verified during Slice 2 review, see docs/pr-11-review.md):
- db/migration/V6__create_reviews_notifications_audit.sql:51,52 creates audit_logs.old_values/new_values as JSONB.
- domain/audit/AuditLog.java:49,60 maps them as @Column(columnDefinition="text") private String.
- Runtime application.yml uses ddl-auto=validate. After Flyway applies V6 (real JSONB columns), Hibernate
  validates the String/text entity against the jsonb DB column and FAILS at boot:
  "Schema-validation: wrong column type encountered in column [new_values] in table [audit_logs];
   found [jsonb (Types#OTHER)], but expecting [text (Types#VARCHAR)]"
- This now breaks RUNTIME startup (Slice 2), not only Testcontainers tests.

Choose ONE approach (decide with Claude), implement, then verify BOTH:
A) Add a NEW Flyway migration (e.g. V10__audit_logs_text.sql): ALTER TABLE audit_logs
   ALTER COLUMN old_values TYPE text, ALTER COLUMN new_values TYPE text. (Never edit V6 — Flyway checksum.)
   -> Entity stays String/text; both sides become text. Simplest, loses jsonb querying (acceptable: audit
      rows are rarely queried by JSON content). H2 test profile unaffected.
B) Map jsonb properly: add hypersistence-utils, @Type(JsonType.class) + Map<String,Object> (or JsonNode),
   add jsonb support to the H2 test profile so create-drop stays consistent. More code, more ripple.

MUST verify (run for real, don't assert):
1) ./mvnw -B verify                            # 308-test H2 suite still green
2) docker build -t marketplace .               # image still builds
3) docker compose up --build                   # against Slice-2 compose
   - app boots, Flyway applies V1..V10, ddl-auto=validate PASSES
   - curl -fsS http://localhost:8080/actuator/health -> {"status":"UP"}
4) Confirm H2 test profile still builds schema from entities without jsonb errors.

Open PR into main. Do not merge; report back for review.
```

## Verify commands (for this PR, current state — blocker reproducible)

```bash
cp -f .env.example .env        # ensure STRIPE_* + ADMIN_* are present
docker build -t marketplace .  # ✅ succeeds
docker compose down -v --remove-orphans
docker compose up --build -d
docker compose logs app | grep -iE "validate|migrat|jsonb|SchemaManagement|Application run"
# Expect: "Successfully applied 9 migrations" then "Schema-validation: wrong column type ... jsonb ... text"
#         then "Application run failed". This is the blocker.
docker compose down -v --remove-orphans
```

## Next after Slice 4

- Re-verify PR #11 image boots against Postgres with the Slice-4 migration; `/actuator/health` UP.
- Then merge PR #11, then Slice 5 (deploy) becomes unblocked.
```
