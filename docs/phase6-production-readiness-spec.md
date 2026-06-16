# Phase 6 — Production Readiness / DevOps — Spec

**Type:** Infrastructure / DevOps phase (multi-PR)
**For:** GLM to implement → Claude (Opus) to review each PR → Hien merges
**Base:** branch off `main` per slice (see naming below)
**Status:** Spec ready. Slice 1 is the immediate GLM task.
**Source roadmap:** `docs/learning-roadmap.md` → "Phase 6: Production Readiness / DevOps"

---

## Goal & why

The app is feature-complete (Phases 0–5.5) but has **never run outside a dev laptop**. Phase 6 closes the
biggest portfolio gap: **CI that gates merges, a container image, a one-command full stack, prod config,
observability, and a live URL.** For a CV/portfolio backend, "it builds in CI and runs live" is worth more
than another feature.

## Slicing strategy (IMPORTANT — do NOT do this as one mega-PR)

Phase 6 is broad. Each slice below is an **independent, reviewable PR** with its own branch. Ship and review
them in order; later slices assume earlier ones merged. This keeps diffs small, review honest, and CI useful
from slice 1 onward.

| Slice | Branch | Deliverable | Depends on |
|-------|--------|-------------|------------|
| 1 | `feat/phase6-ci` | GitHub Actions CI: build + test (H2 profile) gates PRs | — |
| 2 | `feat/phase6-docker` | Multi-stage Dockerfile + docker-compose (app+postgres+redis) | 1 |
| 3 | `feat/phase6-prod-config` | `application-prod.yml`, externalized secrets, Actuator, structured logging, OpenAPI export | 1 |
| 4 | `feat/phase6-testcontainers-ci` | Fix audit_logs jsonb mismatch → enable Testcontainers in CI | 1, (blocker below) |
| 5 | `feat/phase6-deploy` | Deploy to free-tier cloud, README CI badge + live/Swagger link | 2, 3 (Hien-driven) |

---

## ⚠️ Known blocker (read before Slice 1 & 4)

The roadmap asks for "Testcontainers in CI", but **Testcontainers integration tests currently cannot run**
because of a pre-existing schema mismatch — confirmed during PR #9 review:

- Migration `db/migration/V6__create_reviews_notifications_audit.sql` creates `audit_logs.old_values` and
  `new_values` as **`JSONB`**.
- Entity `domain/.../AuditLog.java` maps them as `@Column(columnDefinition = "text") private String`.
- `BaseIntegrationTest` (Testcontainers + real Postgres) runs Flyway then `spring.jpa.hibernate.ddl-auto=validate`.
  Hibernate validates the `String/text` entity field against the `jsonb` DB column → **mismatch → context fails to start.**
- The H2 `test` profile uses `ddl-auto=create-drop` (Hibernate builds the schema from the entity, so both
  sides are `text`) → no mismatch → the 308-test suite is green.

**Consequence:** Slice 1 CI must run the **H2 `test` profile** (`./mvnw test`), which is already green. Do NOT
try to wire Testcontainers into CI in Slice 1 — it will fail on the above. Fixing the mismatch is its own
Slice 4. Options for Slice 4 (decide then, with review):
1. New Flyway migration `ALTER COLUMN ... TYPE text` (drop jsonb) — simplest, loses jsonb querying (audit
   rows are rarely queried by JSON content). **Never edit V6 in place** (Flyway checksum) — add a new V#.
2. Map jsonb properly in the entity (hypersistence-utils `@Type(JsonType.class)` + `Map<String,Object>`) —
   the entity javadoc already suggests this; ripples to AuditLog construction/usage; H2 jsonb support differs
   so the test profile config needs care.
3. Profile-specific mapping so only the Postgres/integration path uses jsonb.

> Slice 4 is optional-ish: CI is already valuable with the H2 suite. Prioritize 1→2→3→5; do 4 when the
> Testcontainers coverage is wanted.

---

## Slice 1 — CI pipeline (immediate GLM task)

**Branch:** `feat/phase6-ci`

### Scope
- Add `.github/workflows/ci.yml`:
  - Triggers: `push` to `main`, and all `pull_request`.
  - JDK: match the project's Java version (read `pom.xml` `<java.version>` — do NOT guess; use that exact version).
  - Cache Maven (`~/.m2`) keyed on `pom.xml` hash.
  - Step: `./mvnw -B verify` **with the H2 test profile** (the env-unset form used in the project:
    `env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw -B verify`,
    or set the test profile explicitly). The 308-test suite must pass.
  - Fail the job (and thus block the PR) on any test failure.
- Do NOT add Testcontainers/Postgres services to CI in this slice (see blocker).
- Do NOT change application code in this slice.

### Files
- `.github/workflows/ci.yml` (new)
- README: add a CI status badge (optional in this slice; can fold into Slice 5).

### Verify / acceptance
- [ ] Push the branch → Actions runs → job is **green**.
- [ ] Open the PR → the check appears and is **required-looking** (green check on the PR).
- [ ] Introduce a deliberately failing test locally to confirm CI would go red (then revert) — describe in PR, don't commit the failing test.
- [ ] No source changes; only `.github/workflows/ci.yml` (+ optional README badge).

### Review notes for Claude
- Confirm the Maven invocation matches how the suite is actually run green locally (env-unset datasource).
- Confirm Java version in the workflow == `pom.xml`, not a hardcoded guess.
- Confirm no Testcontainers/Postgres service block snuck in.

---

## Slice 2 — Dockerfile + docker-compose

**Branch:** `feat/phase6-docker`

### Scope
- Multi-stage `Dockerfile`: build stage (Maven + JDK, `./mvnw -B -DskipTests package`) → runtime stage
  (slim JRE, copy the fat jar, non-root user, `EXPOSE`, `ENTRYPOINT java -jar`). Prefer layered jar extraction
  for better caching if straightforward.
- `docker-compose.yml`: services `app` + `postgres` + `redis`, wired via env, healthchecks, `depends_on`,
  volumes for pg data. App reads DB/Redis host from compose env (not localhost).
- `.dockerignore` (exclude target, .git, docs, .agents, .env).

### Verify / acceptance
- [ ] `docker build -t marketplace .` succeeds from a clean checkout.
- [ ] `docker compose up` brings up all three; app `/actuator/health` (or `/swagger-ui`) reachable.
- [ ] App connects to the compose postgres + redis (not the host).
- [ ] Image runs as non-root.

### Review notes for Claude
- Check image size sanity (slim JRE, no build tools in runtime layer).
- Check no secrets baked into the image; config comes from env.
- Flyway runs against the compose Postgres on startup (real jsonb schema) — app should boot fine (the
  jsonb mismatch only affects `ddl-auto=validate` in tests, runtime uses `validate` too — VERIFY the app
  actually starts against real Postgres; if it trips the same mismatch, that surfaces the blocker at runtime
  and Slice 4 becomes a prerequisite for deploy. Flag to reviewer if so.)

> NOTE TO REVIEWER (Claude): the runtime `application.yml` also uses `ddl-auto=validate`. Verify whether the
> app boots clean against real Postgres in compose. If the audit_logs jsonb/text mismatch also breaks runtime
> startup (not just tests), Slice 4 must move BEFORE Slice 5 (deploy). This needs checking during Slice 2 review.

---

## Slice 3 — Prod config, Actuator, logging, OpenAPI

**Branch:** `feat/phase6-prod-config`

### Scope
- `application-prod.yml`: all secrets via env (DB, Redis, Stripe, JWT, admin) — no literals. Sensible prod
  logging levels; disable SQL logging.
- Spring Boot Actuator: expose `health`, `info`, `metrics`, `prometheus` (lock down others). Add the
  `micrometer-registry-prometheus` dependency if missing.
- Structured JSON logging for prod profile (e.g. logback JSON encoder), human logs for dev.
- Export OpenAPI to `docs/api/openapi.yaml` (from the running app / springdoc) and commit it.

### Verify / acceptance
- [ ] `--spring.profiles.active=prod` with required env set boots clean; missing secret fails fast with a clear message.
- [ ] `/actuator/health` UP, `/actuator/prometheus` serves metrics.
- [ ] Prod logs are JSON; dev logs unchanged.
- [ ] `docs/api/openapi.yaml` present and matches current endpoints (incl. `/api/admin/**`).

---

## Slice 4 — Enable Testcontainers in CI (see blocker)

**Branch:** `feat/phase6-testcontainers-ci`
Fix the `audit_logs` jsonb/text mismatch (one of the options above, decided with Claude), then add a Postgres
(and Redis if needed) service or Testcontainers run to CI so `BaseIntegrationTest`-style tests run on real
Postgres. Acceptance: the Testcontainers tests run green in CI; `validate` passes against the Flyway schema.

---

## Slice 5 — Deploy (Hien-driven)

**Branch:** `feat/phase6-deploy`
**Blocked on a Hien decision:** which free-tier host (Railway / Render / Fly.io). GLM prepares host config
(`render.yaml` / `fly.toml` / Railway settings) + documents required env vars; **Hien performs the actual
deploy** (account, secrets, billing). Then: README gets the CI badge + live URL + Swagger link. Acceptance:
live URL serves API + Swagger, `/actuator/health` UP in prod.

---

## Out of scope for Phase 6
- Frontend (Phase 7), extra docs/system-design polish (Phase 8).
- Review moderation (still no Review hide/remove domain method — unrelated).
- The unrelated open branch `origin/fix/service-search-pagination-sorting` (separate track).

## Open decisions for Hien
1. **Deploy target** (Slice 5): Railway vs Render vs Fly.io. *Recommendation:* **Render** — free web service,
   first-class Docker, managed Postgres + Redis, simple `render.yaml`. Decide before Slice 5 (not blocking 1–3).
2. **Testcontainers fix approach** (Slice 4): pick option 1/2/3 above when we get there.
3. Whether to do Slice 4 at all, or accept H2-only CI for the portfolio.

## GLM coder prompt — Slice 1 (copy-paste)

```
Implement Phase 6 Slice 1 (CI) per docs/phase6-production-readiness-spec.md. New branch feat/phase6-ci off main.

Add ONLY .github/workflows/ci.yml:
- Trigger on push to main and all pull_request.
- Use the Java version from pom.xml <java.version> (read it; do not guess).
- Cache ~/.m2 keyed on pom.xml hash.
- Run the test suite the SAME way it passes locally (H2 test profile):
    env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw -B verify
- Job must fail (block PR) on any test failure.
- Do NOT add Postgres/Testcontainers services (a known schema blocker makes them fail — see spec). Do NOT change app code.

Push, open a PR to main, confirm the Actions run is green, then report back for review. Do not merge.
```
