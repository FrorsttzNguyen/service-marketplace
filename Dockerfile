# =============================================================================
# Phase 6 Slice 2 — Multi-stage Dockerfile for the Service Marketplace API
# See docs/phase6-production-readiness-spec.md (Slice 2) for the why.
#
# WHY MULTI-STAGE:
# - Stage 1 ("builder") carries Maven + JDK + ALL dependencies + sources. The image
#   for this stage is large but DISCARDED — it only exists to produce the fat jar.
# - Stage 2 ("runtime") starts FROM a slim JRE and copies ONLY the built jar. The
#   final image has no compiler, no Maven, no test libs → smaller + smaller attack surface.
#
# WHY COPY pom.xml + mvnw + .mvn BEFORE src (the "dependency cache layer"):
# - Docker builds each instruction as a layer; a layer is rebuilt only if an earlier
#   layer changed. `dependency:go-offline` only depends on pom.xml, so if we run it
#   in a layer that changes only when pom.xml changes, the expensive dependency
#   download is CACHED across builds that only touch src/. This is the single biggest
#   build-time win for Maven-in-Docker.
#
# SECURITY: No secret is ever baked into this image. Database, Redis, Stripe, JWT,
# admin credentials all arrive via environment variables at RUNTIME (see docker-compose.yml).
# The image itself is safe to push to a public registry.
# =============================================================================

# ---------- Stage 1: build ----------
# maven:3.9-eclipse-temurin-21 ships Maven 3.9 + JDK 21 (matches pom.xml java.version=21).
# Using the official Maven image avoids installing Maven ourselves.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy the Maven wrapper +pom first so the dependency-cache layer only depends on these.
# `.mvn` + `mvnw` + `pom.xml` together let us run `./mvnw` instead of the image's `mvn`.
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn
COPY pom.xml .

# Resolve all dependencies into the local repo (~/.m2) WITHOUT compiling sources.
# -B = batch mode (no download progress noise / interactive prompts).
# -DskipTests = do not run or compile tests during this dependency step.
# This layer is cached and only invalidated when pom.xml changes.
RUN ./mvnw -B -DskipTests dependency:go-offline

# Now copy the actual sources and build the fat jar.
# `package` compiles + runs tests by default, but we -DskipTests here because the
# image's job is to PRODUCE the artifact; the 308-test suite is the CI pipeline's
# gate (see .github/workflows/ci.yml), not the image build's job. Skipping tests
# here keeps the image build fast and decoupled from a running DB.
COPY src ./src
RUN ./mvnw -B -DskipTests package

# ---------- Stage 2: runtime ----------
# eclipse-temurin:21-jre-jammy = ONLY the JRE (no javac/Maven) on Ubuntu Jammy.
# Smaller image, fewer tools an attacker could abuse.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install curl for the docker-compose HEALTHCHECK (`curl /actuator/health`).
# The base JRE image has neither curl nor wget. --no-install-recommends keeps the
# footprint small; we also rm the apt lists to shrink the layer.
# (This must run as root, BEFORE we switch to the non-root USER below.)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a NON-ROOT user. If a container escape ever happened, the process inside
# would still be an unprivileged user instead of root inside the container.
# `useradd --system` creates a system account (no home dir, no shell) — ideal for a service.
RUN groupadd --system appgroup && useradd --system --gid appgroup --no-create-home appuser

# Copy the fat jar produced in stage 1.
# `spring-boot-maven-plugin` repackages the jar to marketplace-0.0.1-SNAPSHOT.jar
# (an executable fat jar with an embedded Tomcat).
COPY --from=builder /build/target/marketplace-0.0.1-SNAPSHOT.jar /app/app.jar

# Everything under /app is owned by the non-root user so the JVM can read its jar.
RUN chown -R appuser:appgroup /app

USER appuser

# Document the port the container listens on. This is informational only;
# the host port mapping is controlled by docker-compose / `docker run -p`.
EXPOSE 8080

# ENTRYPOINT (exec form) — runs `java -jar /app/app.jar` as PID 1.
# Config (DB host, Redis host, secrets) is injected via environment variables at runtime.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
