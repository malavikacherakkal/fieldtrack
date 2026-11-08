# FieldTrack — PRD & Build Plan

**Source:** `FieldTrack-PRD-Build-Plan.pdf`, 26 October 2026 · Vyoop
**Status:** living document. Keep it in the repo so Claude Code can read it with `Read docs/PRD.md`.

---

## Overview

FieldTrack is a mini field-service system: a Java Spring Boot backend plus a Kotlin Android app
for technicians. It is built in 9 phases over about 8–10 weeks, using Claude Code to write the
code and this document to review and learn from it.

**Why this project:** it covers the skills employers and clients look for (REST APIs, databases,
auth, testing, Android with Compose, offline sync), and it mirrors a domain already known from
Middlebolt.

## How to use this document

1. Before each phase, read its **Learning goals**. Know what you are about to see.
2. Paste the phase's **prompt**.
3. Ask for work in **small commits, one feature per commit**.
4. Review every commit with the phase's **Review checklist** before moving on. If you cannot
   explain a line, ask for an explanation, then write it into `docs/LEARNING_LOG.md`.
5. Once per phase, **write one small feature by hand**. This is how the learning sticks.

---

## Product requirements

An admin creates jobs and assigns them to technicians; technicians complete them on their phone,
even offline, with photos and a customer signature as proof.

### Users and roles

| Role | Uses | Can do |
|---|---|---|
| **Admin** | API (via Swagger/Postman; optional web panel later) | Manage customers, technicians and jobs; assign jobs; see all job reports |
| **Technician** | Android app | See own assigned jobs; update status; add notes, photos, signature |

### Core features (MVP)

1. Login with email and password; JWT access token and refresh token.
2. **Customers:** create, edit, list, search (name, phone, address, location).
3. **Jobs:** title, description, customer, scheduled date/time, priority, assigned technician.
4. **Job status flow:** `ASSIGNED → ON_THE_WAY → IN_PROGRESS → COMPLETED` (or `CANCELLED` by admin).
5. **Technician app:** today's jobs, job details, call customer, open location in Maps.
6. **Proof of work:** before/after photos, notes, customer signature.
7. **Offline mode:** jobs cached on the phone; updates queued and synced when online.
8. **Job report:** PDF generated after completion.
9. **Push notification** when a new job is assigned.

### Out of scope for v1

Invoicing and payments · multi-company (multi-tenant) support · live GPS tracking ·
a full admin web dashboard · iOS.

### Success criteria

The full flow — admin creates job → technician completes it offline → syncs → PDF report —
works end to end on a real phone, with tests passing in CI.

---

## Architecture and tech stack

Two apps in one GitHub repo: a Spring Boot REST API with PostgreSQL, and an offline-first
Android app that syncs with it.

```
   Android app (Kotlin)                 HTTPS + JWT        Spring Boot API (Java)
   ─────────────────────                ───────────        ──────────────────────
   Compose screens                                        Security filter
     Login, jobs, job details, signature                     JWT check, role rules
         |                                                        |
   ViewModels                                              Controllers
     UI state as StateFlow, injected by Hilt                  REST endpoints, DTOs, validation
         |                                                        |
   Repository  <---- the ONLY network boundary ---->       Services
     Room is the single source of truth                       Status flow, sync, PDF reports
       |          |                                              |              |
   Room DB    WorkManager                                   PostgreSQL     File storage
   jobs +     runs sync jobs                                via Spring     uploaded images
   outbox                                                   Data JPA
                   ^                                              |  job assigned
                   +------ Firebase Cloud Messaging <--------------+
                           push to technician phones
```

**The rule that defines this architecture:** the app's screens never call the network directly.
The repository reads from Room, and WorkManager syncs queued changes to the API.

| Layer | Choice | What you learn |
|---|---|---|
| Backend language | Java 21, Gradle (Kotlin DSL) | Modern Java: records, enums, streams |
| Backend framework | Spring Boot, Spring Web, Bean Validation | Dependency injection, REST design |
| Security | Spring Security, JWT, BCrypt | Auth flows, roles, token handling |
| Database | PostgreSQL, Spring Data JPA, Flyway | Schema design, migrations, queries |
| Backend testing | JUnit 5, Mockito, Testcontainers | Unit vs integration tests |
| API docs and files | springdoc OpenAPI (Swagger), OpenPDF | API documentation, file generation |
| Android UI | Kotlin, Jetpack Compose, Material 3 | Declarative UI, state |
| Android architecture | MVVM, StateFlow, Coroutines, Hilt | Clean layering, async code, DI |
| Android data | Retrofit, OkHttp, kotlinx.serialization, Room, DataStore | Networking, local database, secure storage |
| Android background | WorkManager, CameraX, Firebase Cloud Messaging | Sync, camera, push notifications |
| DevOps | Docker, Docker Compose, GitHub Actions, a cloud host (Render or Railway) | CI/CD and deployment |

---

## Data model

Six tables in PostgreSQL, managed with Flyway migrations. The Android app mirrors `jobs`,
`customers` and pending updates in Room.

| Table | Key columns | Relations |
|---|---|---|
| `users` | `id` (UUID), `name`, `email`, `password_hash`, `role` (ADMIN/TECHNICIAN), `phone`, `active`, `created_at` | One technician → many jobs |
| `customers` | `id`, `name`, `phone`, `email`, `address`, `latitude`, `longitude`, `created_at` | One customer → many jobs |
| `jobs` | `id`, `title`, `description`, `customer_id`, `technician_id`, `priority` (LOW/MEDIUM/HIGH), `status`, `scheduled_at`, `started_at`, `completed_at`, `version`, `updated_at` | Belongs to customer and technician |
| `job_status_history` | `id`, `job_id`, `from_status`, `to_status`, `changed_by`, `changed_at`, `note` | Audit trail for each job |
| `job_attachments` | `id`, `job_id`, `type` (PHOTO_BEFORE/PHOTO_AFTER/SIGNATURE), `file_url`, `uploaded_at` | Many per job |
| `refresh_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `revoked` | Many per user |

**Design rules to learn here:**

- **UUIDs instead of auto-increment IDs** — safe for records created offline on a phone.
- **A `version` column for optimistic locking** — detects sync conflicts.
- **Never store plain passwords or plain refresh tokens** — always hashed.
- **Every status change writes a history row.**

---

## API specification

REST API under `/api/v1`, JSON in and out, JWT in the `Authorization: Bearer` header.
Every endpoint documented in Swagger UI.

| Method | Endpoint | Who | Purpose |
|---|---|---|---|
| POST | `/auth/login` | All | Returns access + refresh token |
| POST | `/auth/refresh` | All | New access token from refresh token |
| GET | `/me` | All | Current user profile |
| GET / POST | `/customers` | Admin | List (paged, searchable) / create |
| GET / PUT / DELETE | `/customers/{id}` | Admin | Read / update / delete |
| GET / POST | `/technicians` | Admin | List / create technician accounts |
| GET / POST | `/jobs` | Admin | List with filters (status, date, technician) / create |
| GET / PUT | `/jobs/{id}` | Admin | Read / update or reassign |
| GET | `/jobs/mine?date=` | Technician | My jobs for a day |
| PATCH | `/jobs/{id}/status` | Technician | Change status (validated flow) |
| POST | `/jobs/{id}/attachments` | Technician | Upload photo or signature (multipart) |
| GET | `/jobs/{id}/report.pdf` | Both | Download job report |
| POST | `/sync` | Technician | Push queued offline changes; get changes since last sync |

**Error format — the same everywhere:**

```json
{ "code": "JOB_NOT_FOUND", "message": "...", "details": [] }
```

with the right HTTP status: `400`, `401`, `403`, `404`, and `409` for sync conflicts.

---

## Android app screens

Six screens in Jetpack Compose with Material 3; each screen has its own ViewModel and UI state.

| Screen | Shows | Actions |
|---|---|---|
| **Login** | Email, password, error message | Log in; token saved in encrypted DataStore |
| **Today's jobs** | List grouped by status, sync indicator, date picker | Pull to refresh, open job |
| **Job details** | Customer, address, description, status timeline | Call, open in Maps, change status |
| **Proof of work** | Before/after photo grid, notes field | Take photo (CameraX), delete, save |
| **Signature** | Drawing canvas, customer name | Clear, confirm, complete job |
| **Profile & sync** | User info, pending changes count, last sync time | Sync now, log out |

**Every screen must handle four states: loading, empty, error, offline.** Reviewing these four
states is one of the fastest ways to tell good Android code from demo code.

---

## Build phases

Nine phases: backend first (weeks 1–4), then Android (weeks 5–8), then polish and deploy
(weeks 9–10). Finish each phase's checklist before starting the next.

### Phase 0 — Setup (1–2 days)

> Create a monorepo called `fieldtrack` with two folders: `backend/` (Spring Boot, Java 21,
> Gradle Kotlin DSL) and `android/` (empty for now). Add `docs/PRD.md`, a `CLAUDE.md` describing
> the project, coding conventions and "small commits, explain each change", a `.gitignore`, a
> `docker-compose.yml` with PostgreSQL, and a GitHub Actions workflow that builds and tests the
> backend. Explain each file you create.

**Learning goals:** Gradle basics, Docker Compose, what CI does.

**Review checklist**
- [ ] `docker compose up` starts Postgres
- [ ] CI runs green on the first push
- [ ] You can explain every line of `build.gradle.kts`

### Phase 1 — Backend foundation: customers (week 1)

> Read `docs/PRD.md`. Build the Customers feature in the backend using a layered structure
> (controller, service, repository, entity, DTO). Use Spring Data JPA, Flyway migration for the
> `customers` table, Bean Validation on request DTOs, a global exception handler returning the
> error format in the PRD, pagination and search on `GET /customers`, and Swagger UI. Write unit
> tests for the service and an integration test with Testcontainers. Commit after each layer.

**Learning goals:** Spring dependency injection, JPA entities vs DTOs, why migrations matter,
how validation and exception handling work.

**Review checklist**
- [ ] Entities are never returned directly from controllers
- [ ] Validation errors return 400 with clear field messages
- [ ] Tests run against a real Postgres in Testcontainers
- [ ] **By hand:** add a `notes` field to customers (migration, entity, DTO, test)

### Phase 2 — Authentication and roles (week 2)

> Add authentication per `docs/PRD.md`: `users` table, BCrypt password hashing, Spring Security
> with stateless JWT (access token 15 min, refresh token 7 days stored hashed), roles `ADMIN` and
> `TECHNICIAN`, endpoints `/auth/login`, `/auth/refresh`, `/me`, and role-based access on all
> existing endpoints. Seed one admin user from environment variables. Add tests for 401 and 403
> cases. Explain the security filter chain step by step.

**Learning goals:** how a request passes the Spring Security filter chain, what is inside a JWT,
why refresh tokens exist.

**Review checklist**
- [ ] No secrets in code (JWT secret and admin password come from env vars)
- [ ] A technician gets 403 on admin endpoints (tested)
- [ ] Passwords and refresh tokens are hashed in the DB
- [ ] **By hand:** add `POST /auth/logout` that revokes the refresh token

### Phase 3 — Jobs and status flow (week 3)

> Implement Jobs per `docs/PRD.md`: `jobs` and `job_status_history` tables, admin CRUD with
> filters, `GET /jobs/mine` for technicians, and `PATCH /jobs/{id}/status` that only allows valid
> transitions (Assigned → On the way → In progress → Completed; Cancelled by admin only). Put the
> transition rules in the domain/service layer, write a history row on each change, use
> `@Version` for optimistic locking, and cover every valid and invalid transition with unit tests.

**Learning goals:** modelling a state machine with Java enums, transactions (`@Transactional`),
optimistic locking.

**Review checklist**
- [ ] Invalid transitions return 409 or 400, never 500
- [ ] A technician cannot change someone else's job (tested)
- [ ] No N+1 query problem when listing jobs (check SQL logs)
- [ ] **By hand:** add a filter for overdue jobs

### Phase 4 — Attachments and PDF report (week 4)

> Add `POST /jobs/{id}/attachments` (multipart upload for PHOTO_BEFORE, PHOTO_AFTER, SIGNATURE)
> with a `FileStorage` interface and a local-disk implementation (S3-compatible later), file type
> and size validation, and `GET /jobs/{id}/report.pdf` that generates a job report (customer,
> technician, status timeline, photos, signature) using OpenPDF. Add tests.

**Learning goals:** coding to an interface, multipart uploads, generating files on the server.

**Review checklist**
- [ ] Upload rejects files over the size limit and non-image types
- [ ] Storage can be swapped without changing the service code
- [ ] **By hand:** add your company logo to the PDF header

### Phase 5 — Android foundation (weeks 5–6)

> Create the Android app in `android/` with Kotlin, Jetpack Compose, Material 3, MVVM, Hilt,
> Retrofit + kotlinx.serialization, Coroutines/Flow, Navigation Compose, and encrypted DataStore
> for tokens. Build the Login and Today's jobs screens from `docs/PRD.md`, with an OkHttp
> interceptor that adds the JWT and refreshes it on 401. Every screen must handle loading, empty,
> error and offline states. Add ViewModel unit tests. Explain the architecture before writing code.

**Learning goals:** Compose state and recomposition, ViewModel + StateFlow, dependency injection
with Hilt, coroutines.

**Review checklist**
- [ ] No network calls inside Composables
- [ ] Rotating the phone does not lose state or repeat requests
- [ ] Tokens are not stored in plain SharedPreferences
- [ ] **By hand:** add the date picker to the jobs list

### Phase 6 — Job details, camera and signature (week 7)

> Build Job details, Proof of work and Signature screens from `docs/PRD.md`: status change
> buttons following the allowed flow, call and open-in-Maps intents, CameraX for before/after
> photos with image compression, a Compose Canvas signature pad exported as PNG, and uploads to
> the attachments endpoint. Handle runtime permissions properly.

**Learning goals:** Android intents and permissions, CameraX, drawing on Canvas, file handling.

**Review checklist**
- [ ] Denying camera permission shows a helpful message, not a crash
- [ ] Photos are compressed before upload
- [ ] **By hand:** add a "retake" button on a photo

### Phase 7 — Offline mode and sync (week 8)

> Make the app offline-first: Room as the single source of truth for jobs and customers, a
> `pending_changes` outbox table for status changes and attachments, WorkManager to sync when the
> network returns, and the backend `POST /sync` endpoint that accepts queued changes and returns
> changes since the last sync. Use the job `version` field to detect conflicts and return 409; on
> conflict the server wins and the user is told. Write tests for the sync logic on both sides.

**Learning goals:** repository pattern, outbox pattern, background work, conflict resolution.
**This phase is the strongest talking point in interviews and client meetings.**

**Review checklist**
- [ ] Turn on airplane mode, complete a job, turn it off: the job syncs
- [ ] Changes are never lost if the app is killed mid-sync
- [ ] Conflict case tested (admin edits the job while the technician is offline)

### Phase 8 — Notifications, polish and deploy (weeks 9–10)

> Add Firebase Cloud Messaging: the backend stores device tokens and sends a push when a job is
> assigned; the app shows the notification and opens the job. Then prepare deployment: a backend
> Dockerfile, production config via env vars, deploy to a cloud host with managed Postgres, and a
> GitHub Actions workflow that builds a signed release APK. Finish with a README covering setup,
> architecture and screenshots.

**Learning goals:** push notifications, containerising a Spring app, environment-based config,
release builds.

**Review checklist**
- [ ] Live API URL works with Swagger
- [ ] Release APK installs on a real phone and talks to the live API
- [ ] README lets a stranger run the project in under 15 minutes

---

## Code review method

Review every generated change as if a junior developer wrote it: **read the diff, run it, then
question it.**

For each commit:

1. Read the diff on GitHub (or `git diff`), file by file.
2. Run the app and the tests yourself.
3. Ask these questions until every answer makes sense:
   1. "Explain this file line by line as if I'm new to Spring/Compose."
   2. "What would break if I removed this line?"
   3. "What are two other ways to do this, and why did you choose this one?"
   4. "What edge cases are not handled?"
4. Write 3–5 lines in `docs/LEARNING_LOG.md`: what you learned, what confused you.

**Questions to ask on any code:**

- Does each class have one clear job?
- Are names clear without comments?
- What happens when the network, database or input is bad?
- Is anything secret, hardcoded or logged that shouldn't be?
- Is there a test that would fail if this broke?

**Definition of done for each phase:** checklist ticked, tests green in CI, one feature written
by hand, learning log updated, and you can explain the phase's main flow without looking at the
code.

---

## Portfolio packaging

A reviewer spends about two minutes on a project, so the repo must show the result fast.

- [ ] README with a one-line pitch, architecture diagram, tech stack, and setup steps
- [ ] 6–8 app screenshots and a 60–90 second demo video (the offline sync flow is the highlight)
- [ ] Live Swagger link and a downloadable APK in GitHub Releases
- [ ] Green CI badge and test coverage badge
- [ ] A short write-up (blog post or LinkedIn) on one hard problem you solved, such as offline sync conflicts
- [ ] Clean commit history with clear messages

## Next steps after v1

An admin web panel · multi-company support · rewriting the API in Ktor to compare it with Spring.

---

## Deviations from the original PDF

Recorded here so the document stays honest about what was actually built, and why.

| PDF says | Built as | Why |
|---|---|---|
| Spring Boot 3 | **Spring Boot 4.1.2** | Spring Initializr no longer offers Boot 3 — it has aged out of open-source support, so it receives no security patches. Boot 4 renames some starters (`spring-boot-starter-web` → `spring-boot-starter-webmvc`, and per-module test starters), which is worth knowing when reading older tutorials. |
