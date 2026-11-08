# FieldTrack

**An offline-first field-service management system** — admins dispatch jobs, technicians complete
them on their phone with no network, attaching photos and a customer signature as proof.

[![Backend CI](https://github.com/malavikacherakkal/fieldtrack/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/malavikacherakkal/fieldtrack/actions/workflows/backend-ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)](https://www.postgresql.org/)

> **Status: in progress.** Phase 0 (project setup, CI, containerised database) is complete and
> green. Phases 1–8 are specified in [`docs/PRD.md`](docs/PRD.md) and not yet built. This README
> describes what exists today — see [Roadmap](#roadmap) for what is coming.

---

## The interesting problem

Field technicians work in basements, lift shafts and rural sites where there is no signal. An app
that merely *degrades* offline is useless to them: they need to complete a job, take photos and
capture a signature with no connection at all, and have it arrive intact later.

FieldTrack solves this with an **outbox pattern**. The phone never talks to the network directly:

- **Room is the single source of truth.** The UI only ever reads from the local database.
- **Every change is queued** into a `pending_changes` outbox table, applied locally at once.
- **WorkManager drains the outbox** when connectivity returns, surviving app kills and reboots.
- **Conflicts are detected, not ignored.** Each job carries a `version` column; if an admin edited
  the job while the technician was offline, the server returns `409` and the user is told.

## Architecture

```
   Android app (Kotlin)              HTTPS + JWT         Spring Boot API (Java)
   ─────────────────────             ───────────         ──────────────────────
   Compose screens                                      Security filter
     Login, jobs, details, signature                      JWT check, role rules
         |                                                      |
   ViewModels                                            Controllers
     UI state as StateFlow, Hilt-injected                  REST endpoints, DTOs, validation
         |                                                      |
   Repository  <--- the ONLY network boundary --->       Services
     Room is the single source of truth                    Status flow, sync, PDF reports
       |          |                                            |              |
   Room DB    WorkManager                                 PostgreSQL     File storage
   jobs +     drains the outbox                           via Spring     photos, signatures
   outbox                                                 Data JPA
                   ^                                            |  job assigned
                   +------ Firebase Cloud Messaging <------------+
```

**The rule that defines the design:** screens never call the network. They read Room; WorkManager
syncs. That single constraint is what makes offline work rather than merely survive.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2, Spring Web MVC, Bean Validation |
| Security | Spring Security, JWT (access + refresh), BCrypt |
| Database | PostgreSQL 17, Spring Data JPA, Flyway migrations |
| Testing | JUnit 5, Mockito, **Testcontainers** (real Postgres, not H2) |
| API docs | springdoc OpenAPI (Swagger UI) |
| Android | Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Coroutines/Flow |
| Android data | Retrofit, OkHttp, kotlinx.serialization, Room, encrypted DataStore |
| Android background | WorkManager, CameraX, Firebase Cloud Messaging |
| Build & CI | Gradle (Kotlin DSL), Docker Compose, GitHub Actions |

## Getting started

**Prerequisites:** JDK 21, Docker, Git. Nothing else — Gradle installs itself via the wrapper.

```bash
git clone https://github.com/malavikacherakkal/fieldtrack.git
cd fieldtrack
docker compose up -d --wait        # PostgreSQL 17 on :5432, waits until healthy
cd backend
./gradlew bootRun                  # API on http://localhost:8080
```

Health check: <http://localhost:8080/actuator/health>

Run the tests — these start their own throwaway PostgreSQL via Testcontainers, so Docker must be
running, and your development database is never touched:

```bash
./gradlew test
```

On Windows use `gradlew.bat` in PowerShell, or `./gradlew` in Git Bash.

### Configuration

Every environment-specific value is read from an environment variable with a local-dev fallback,
so no secret is ever committed. Copy `.env.example` to `.env` to override:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fieldtrack` | JDBC connection string |
| `DB_USERNAME` / `DB_PASSWORD` | `fieldtrack` | Database credentials |
| `SERVER_PORT` | `8080` | HTTP port |
| `SQL_LOG_LEVEL` | `INFO` | Set `DEBUG` to inspect generated SQL |

## Project layout

```
backend/     Spring Boot REST API (Java 21, Gradle Kotlin DSL)
android/     Kotlin + Jetpack Compose technician app (from Phase 5)
docs/        PRD.md -- full specification; LEARNING_LOG.md -- build notes
.github/     CI workflows
```

## Roadmap

Built in nine phases; the full specification for each is in [`docs/PRD.md`](docs/PRD.md).

| Phase | Scope | Status |
|---|---|---|
| 0 | Repo, Docker Compose, CI, Spring Boot skeleton | ✅ Done |
| 1 | Customers: CRUD, pagination, search, Flyway, Swagger | ⬜ Next |
| 2 | Auth: JWT access + refresh, BCrypt, `ADMIN` / `TECHNICIAN` roles | ⬜ Planned |
| 3 | Jobs + status state machine, optimistic locking, audit history | ⬜ Planned |
| 4 | Photo/signature uploads, PDF job reports | ⬜ Planned |
| 5 | Android foundation: Login, Today's jobs, token refresh | ⬜ Planned |
| 6 | Job details, CameraX photos, Compose Canvas signature pad | ⬜ Planned |
| 7 | **Offline sync**: Room, outbox, WorkManager, conflict resolution | ⬜ Planned |
| 8 | Push notifications, Docker deploy, signed release APK | ⬜ Planned |

## Engineering decisions worth noting

- **UUID primary keys**, generated client-side — a phone must create records offline without
  colliding with the server.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate` and never alters tables, so
  every schema change is a reviewable SQL file.
- **Testcontainers over H2.** H2 accepts SQL that PostgreSQL rejects; testing against the real
  engine catches that before production does.
- **Pinned image tags** (`postgres:17-alpine`, never `:latest`) so a build is reproducible.
- **Secrets only from the environment** — never in code, config or logs.

## Licence

Not yet licensed. All rights reserved pending a licence decision.
