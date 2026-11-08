# Learning log

Three to five lines per phase, written by hand: **what I learned, what confused me.**

Per `docs/PRD.md`, a phase is not done until this file is updated and the phase's main flow can
be explained without looking at the code. The confusing parts are the most valuable thing in
here — they are the list of what to revisit.

Useful questions to answer in an entry:

- What does this layer/annotation/file actually do?
- What would break if I removed it?
- What two other ways could this have been done, and why was this one chosen?
- What edge case is still not handled?

---

## Phase 0 — Setup

**Date:** 2026-11-08

### What was set up

| Tool | Version | Why it is needed |
|---|---|---|
| Temurin JDK | 21.0.13 LTS | Java 21 is the language level the backend targets |
| Docker Desktop | 29.9.2 | Runs PostgreSQL locally, and lets Testcontainers start a real database during tests |
| GitHub CLI | 2.103.3 | Creating the repo and reading CI results from the terminal |
| Gradle | 9.8.2 (via wrapper) | Build tool — never installed globally, see below |

### Things worth understanding from this phase

**The Gradle wrapper (`gradlew`).** Gradle itself is not installed on this machine. `gradlew` is
a small script that downloads the exact Gradle version named in
`gradle/wrapper/gradle-wrapper.properties` and runs the build with it. That is why a stranger can
clone this repo and build it without installing Gradle, and why CI builds with the same version
as the laptop. Always run `./gradlew`, never a global `gradle`.

**`spring-boot-starter-*` dependencies.** A "starter" is not a library — it is a curated list of
libraries that work together. `spring-boot-starter-webmvc` pulls in Spring MVC, Jackson, and an
embedded Tomcat. None of the versions are written down anywhere: the
`io.spring.dependency-management` plugin sets them all from the Boot version, which is why
upgrading Boot upgrades the whole stack coherently.

**`ddl-auto: validate`.** Hibernate can create database tables from entity classes. It is
switched off here. Flyway owns the schema through versioned SQL files, and Hibernate only checks
that the entities match. Letting Hibernate generate the schema means there is no record of how
production got to its current shape, and no way to review a schema change in a diff.

**Why `postgres:17-alpine` and not `postgres:latest`.** Initializr generated `:latest` for the
test container. With `:latest`, a Postgres major-version release silently changes what the tests
run against, and a green build turns red with no local change. Pinned tags make a build
reproducible.

**What CI is actually for.** `backend-ci.yml` runs `./gradlew build` on a clean Linux machine on
every push. Its real job is to catch what works "on my machine" but is broken everywhere else —
a file that was never committed, a dependency only present locally, a test that depended on
leftover database state.

### Confused about / to revisit

- _(fill in by hand — e.g. what does `io.spring.dependency-management` do that the `java` plugin cannot?)_
- _(what is the difference between `implementation` and `runtimeOnly` in `build.gradle.kts`?)_

### Phase 0 checklist

- [ ] `docker compose up` starts Postgres
- [ ] CI runs green on the first push
- [ ] I can explain every line of `build.gradle.kts`
