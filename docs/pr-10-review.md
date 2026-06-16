# PR #10 Review — Phase 6 Slice 1: CI Pipeline

- **PR:** [#10 ci: add GitHub Actions build+test workflow (Java 21, H2 profile)](https://github.com/FrorsttzNguyen/service-marketplace/pull/10)
- **Branch:** `feat/phase6-ci` → `main`
- **Reviewer:** Opus. Coder: GLM.
- **Date:** 2026-06-17
- **Spec:** `docs/phase6-production-readiness-spec.md` (Slice 1)
- **Diff:** +44 / 1 file (`.github/workflows/ci.yml`), no source changes.

## Verdict

**APPROVE — ready to merge.** Clean, correct, matches the spec exactly, and — most importantly — the
**Actions run is genuinely green on real GitHub infra**, not just asserted. No blocking findings.

## What I verified (not just trusting the PR body)

- ✅ Read the actual `ci.yml` from `origin/feat/phase6-ci` (not the PR summary).
- ✅ Triggers: `push` to `main` + all `pull_request`. Single job `build-and-test` on `ubuntu-latest`.
- ✅ `actions/checkout@v4`, `actions/setup-java@v4` `temurin` **java-version '21'** — matches
  `pom.xml <java.version>21</java.version>` (not a guess).
- ✅ `cache: maven` (~/.m2 keyed on pom hash).
- ✅ Build step `./mvnw -B verify` — runs tests; any failure fails the job → blocks merge.
- ✅ **No** Postgres/Redis service container, **no** Testcontainers — correctly avoids the documented
  `audit_logs` jsonb/text blocker (deferred to Slice 4).
- ✅ **CI run actually green:** `gh pr checks 10` → `build-and-test pass 1m1s`.
- ✅ **Pulled the run log** (`gh run view 27639751029 --log`): `Tests run: 308, Failures: 0, Errors: 0,
  Skipped: 0` → `BUILD SUCCESS` (45.86s). The `verify` step really ran the full suite, did not skip tests,
  and Testcontainers never started (no abstract-base subclass executed) — exactly as the spec predicted.

## Findings

None blocking.

**Optional nits (non-blocking, can defer or ignore):**
1. No `timeout-minutes` on the job — a runaway/hung run could burn Actions minutes. Best practice to cap
   (e.g. `timeout-minutes: 20`). Trivial, optional.
2. No explicit `permissions:` block — defaults are fine for a build-only workflow (read-only). Only worth
   adding if later steps need to write (e.g. posting coverage). Skip for now.

Neither is worth a re-spin; could fold into a later slice if desired.

## Spec acceptance (Slice 1) — all met

- [x] `.github/workflows/ci.yml` added; no source changes (1 file, +44).
- [x] Java version == pom.xml (21).
- [x] Maven invocation matches the green local run (`./mvnw -B verify`, H2 profile).
- [x] No Testcontainers/Postgres service.
- [x] Actions run green (308 tests, verified from log).
- [x] Job fails the PR check on any test failure (verify semantics).

## Verdict for Hien

**Merge PR #10.** CI now gates every future PR (Phase 6 Slices 2–5 and beyond will be protected by it).

## Next after merge

Slice 2 — `feat/phase6-docker` (multi-stage Dockerfile + docker-compose app+postgres+redis). Per the spec's
Slice 2 review note, during that slice **check whether the app boots clean against real Postgres in compose**
(runtime `application.yml` also uses `ddl-auto=validate`); if the audit_logs jsonb/text mismatch breaks
runtime startup too, Slice 4 (the jsonb fix) becomes a prerequisite for deploy.
