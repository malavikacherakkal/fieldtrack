# FieldTrack — working agreement

Read this before writing any code in this repo. The full specification is in
[`docs/PRD.md`](docs/PRD.md) — read that too when starting a phase.

## What this project is

A mini field-service system, built as a **learning and portfolio project**.

- `backend/` — Java 21 + Spring Boot REST API, PostgreSQL, Flyway, JWT auth
- `android/` — Kotlin + Jetpack Compose technician app, offline-first (from Phase 5)
- `docs/` — the PRD, and the learning log

An admin creates jobs and assigns them to technicians. Technicians complete them on their
phone, **even with no network**, attaching photos and a customer signature as proof. The work
is split into 9 phases; `docs/PRD.md` defines each one.

## How to work here — this matters more than the code

This repo exists so its owner **learns** app development, not just so the app exists.
That changes how to help:

1. **Small commits, one feature per commit.** A commit that touches the migration, entity, DTO,
   service, controller and tests at once is unreviewable, and unreviewable means unlearned.
2. **Explain every change.** When introducing a file or an annotation for the first time, say
   what it does and what would break without it. Assume Spring and Compose are both new.
3. **Follow the phase order in `docs/PRD.md`.** Do not implement Phase 3 features while Phase 1
   is unfinished, even when it would be faster.
4. **Leave the by-hand feature alone.** Each phase has one task marked **By hand** in its review
   checklist. Do not implement those — they are the owner's practice. Explain and review them
   instead, and say so if asked to write one.
5. **Prefer the boring, idiomatic solution.** A clever abstraction that cannot be explained in
   two sentences is the wrong choice here.
6. **Never invent progress.** If something is untested or unverified, say so plainly.

## Conventions

### Backend (Java)

- **Layering:** `controller → service → repository`. Controllers do HTTP only (parse, validate,
  map, return status). Services hold the business rules and own `@Transactional`. Repositories
  do data access. No business logic in a controller, no HTTP types in a service.
- **Never return a JPA entity from a controller.** Map to a DTO. Entities leak the schema,
  trigger lazy-loading surprises, and make every field public API by accident.
- **DTOs are Java `record`s.** Request DTOs carry Bean Validation annotations.
- **Flyway owns the schema.** `ddl-auto` is `validate` and must stay that way. Schema changes
  mean a new `V<n>__description.sql` migration. Never edit a migration that has already been
  committed and run — add a new one.
- **UUID primary keys**, generated in the application, because the phone creates records offline.
- **Errors use the PRD format** — `{ "code", "message", "details" }` — produced by one global
  `@RestControllerAdvice`. No stack traces or raw exception text in responses. Never a 500 for a
  predictable condition.
- **Secrets come from environment variables only.** Nothing secret in code, in
  `application.yml`, or in a log line. `.env` is gitignored; `.env.example` documents the keys.
- Indentation is tabs in Java (`.editorconfig` enforces it), 2 spaces in YAML and SQL.

### Android (Kotlin, from Phase 5)

- **MVVM:** Compose screen → ViewModel (`StateFlow` of one UI state type) → repository.
- **Composables never call the network or the database.** Only the repository does.
- **Room is the single source of truth.** The UI reads Room; sync writes to Room; WorkManager
  pushes the outbox to the API. This is what makes offline mode work rather than merely degrade.
- **Every screen handles four states: loading, empty, error, offline.** A screen missing one is
  not finished.
- Tokens live in **encrypted DataStore**, never plain SharedPreferences.

### Testing

- Service logic: unit tests with Mockito, no Spring context — these must be fast.
- Anything touching the database: integration test with **Testcontainers** against real
  PostgreSQL. Not H2 — H2 accepts SQL that Postgres rejects, so it hides real bugs.
- Pin container image tags (`postgres:17-alpine`, never `:latest`) so a test run is reproducible.
- Every bug fix gets a test that would have failed before the fix.

### Commits

Imperative subject under ~72 characters, with scope: `backend: add customer search by phone`.
Body explains *why* when the reason is not obvious from the diff.

## Commands

```bash
docker compose up -d                 # start PostgreSQL
docker compose down                  # stop it (data survives)

cd backend
./gradlew bootRun                    # run the API on :8080
./gradlew test                       # unit + integration tests (needs Docker running)
./gradlew build                      # compile + test + package — what CI runs
```

On Windows use `gradlew.bat` in PowerShell, or `./gradlew` in Git Bash.
