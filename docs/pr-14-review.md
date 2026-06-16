# PR #14 Review — `build(deploy): Render blueprint (app) + prod PORT/Redis-TLS config + README deploy docs`

- **Branch:** `feat/phase6-deploy` → `main`
- **URL:** https://github.com/FrorsttzNguyen/service-marketplace/pull/14
- **Phase:** 6 · Slice 5 (deploy config — Hien-driven deploy, NOT in this PR)
- **Reviewer:** Opus (independent verification)
- **Coder:** GLM
- **Commit:** `3d266b8`

## Scope of review

This PR is **config/docs only** (3 files). No Java code, no migrations, no security change. The review
therefore focuses on: (1) do the config knobs actually do what the comments claim, (2) is `render.yaml`
a valid and safe Blueprint, (3) does the prod profile still boot locally with the new knobs, (4) is the
README accurate, (5) are secrets kept out of git.

## Files changed

| File | Change | Lines |
|------|--------|-------|
| `src/main/resources/application-prod.yml` | +`server.port: ${PORT:8080}` + `spring.data.redis.ssl.enabled: ${REDIS_SSL:false}` | +22 |
| `render.yaml` (new) | Render Blueprint, web service only, docker runtime, env vars | +69 |
| `README.md` | CI badge, Deployment section, Java 21, Live/Swagger placeholders | +61/-4 |

## Verification performed (by Opus, independent of coder)

### 1. Build + tests — `./mvnw -B verify`
- **Result:** `Tests run: 308, Failures: 0, Errors: 0, Skipped: 0` → **BUILD SUCCESS**.
- **Note (not a PR defect):** the very first `verify` run locally errored with 51 "Failed to load
  ApplicationContext" → `Unable to determine Dialect without JDBC metadata`. Root cause: my local shell
  had `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` exported (from `.env`), which Spring Boot environment
  binding honors at **higher priority than the H2 test YAML** — so the test context tried to connect to
  `localhost:5433` Postgres instead of H2. Re-running with those three env vars unset → 308 green.
  **CI is unaffected** because GitHub runners start clean (no `SPRING_DATASOURCE_*` env). This is a
  local-runner-only artifact, not a regression introduced by this PR.

### 2. Prod profile boot with `PORT=9999` + `REDIS_SSL=false`
- Started `docker compose up -d postgres redis` (both healthy).
- Ran `SPRING_PROFILES_ACTIVE=prod PORT=9999 REDIS_SSL=false JWT_SECRET=... ./mvnw spring-boot:run`
  with datasource/redis env pointing at the containers.
- **Result:** `Tomcat started on port 9999` + `Started ServiceMarketplaceApplication` (ECS JSON logs).
- `/actuator/health` on `:9999` → `{"status":"UP"}`.
- **Proves:** `server.port: ${PORT:8080}` honors `PORT` (9999 used, not default 8080); the new
  `redis.ssl.enabled: ${REDIS_SSL:false}` does NOT break plaintext Redis (REDIS_SSL=false → no TLS).
  Neither new knob regresses local/compose.

### 3. YAML validity
- `render.yaml` and `application-prod.yml` both parse via `yaml.safe_load`.
- `render.yaml` service: `type=web`, `runtime=docker`, `plan=free`, `dockerfilePath=./Dockerfile`,
  `healthCheckPath=/actuator/health`, 13 env vars.

### 4. Secrets hygiene
- All 11 secret env vars use `sync: false` (Render: set manually in dashboard, not inherited).
- `JWT_SECRET` uses `generateValue: true` (Render mints on first deploy).
- No literal credentials in `render.yaml`, `application-prod.yml`, or README.
- Confirmed `git show 3d266b8 --stat` = only the 3 expected files; untracked local-only files
  (`.agents`, `docs/learning-brief-*.md`) were NOT staged.

## Findings

| # | Finding | Severity | Action |
|---|---------|----------|--------|
| — | (none) | — | — |

**No blocking, recommended, or optional findings.** The PR does exactly what Slice 5 asks: it prepares
config + docs for a Hien-driven deploy, changes no runtime behavior, keeps secrets out of git, and was
verified to boot locally with the new PORT/REDIS_SSL knobs.

## Non-issues considered and dismissed

- **`render.yaml` declares only a web service (no `psql`/`redis` resource).** Intentional and documented
  in-file: DB/Redis are external managed tiers (Neon, Upstash). Not a defect.
- **`REDIS_SSL=false` default.** Intentional: local/compose Redis is plaintext. `render.yaml` sets
  `REDIS_SSL=true`. Not a defect.
- **README Live URL/Swagger are placeholders.** Intentional: they cannot be filled until Hien actually
  deploys. Not a defect.
- **Pre-existing `JwtUtils` hardcoded JWT default fallback** (common profile). Out of Phase 6 scope; prod
  is safe (overridden + fail-fast, verified Slice 3). Tracked in session-025.

## Verdict

**APPROVE. Safe to merge after Hien reviews.** No deploy is performed by this PR — Hien deploys via
Render dashboard (New → Blueprint → fill `sync: false` env vars) when ready.

## Copy-paste prompt for Hien to send a reviewer agent

```
Read docs/pr-14-review.md and review PR #14 (feat/phase6-deploy → main).
Phase 6 Slice 5 deploy config: application-prod.yml (server.port + redis.ssl flag), render.yaml
(web service only, external Neon+Upstash), README (CI badge, Deployment section, Java 21).
Opus independently verified: ./mvnw -B verify = 308 green (note: local SPRING_DATASOURCE_* env
must be unset or it shadows the H2 test profile — CI runner is clean); prod boot with PORT=9999 +
REDIS_SSL=false boots on 9999 and /actuator/health = UP; both YAML valid; secrets all sync:false
or generateValue:true. No blocking findings. Confirm APPROVE or list blockers; do NOT merge until
Hien says so.
```

## Test commands (for the next reviewer)

```bash
# 1. Full test suite (308 expected green). IMPORTANT: unset datasource env so H2 test profile wins.
env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw -B verify

# 2. YAML validity.
python3 -c "import yaml; yaml.safe_load(open('render.yaml')); yaml.safe_load(open('src/main/resources/application-prod.yml')); print('both valid')"

# 3. Prod boot on PORT=9999 (containers up first).
docker compose up -d postgres redis
export $(grep -v '^#' .env | grep -v '^$' | xargs)
export SPRING_PROFILES_ACTIVE=prod PORT=9999 REDIS_SSL=false JWT_SECRET=local-prod-test-secret-256-bits-long-aaaa
./mvnw spring-boot:run   # expect "Tomcat started on port 9999"
curl -fsS http://localhost:9999/actuator/health   # expect {"status":"UP"}
```
