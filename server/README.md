# Sammalani Alumni — Backend

Spring Boot service behind the alumni platform and Grand Reunion 2027.

**Java 25 · Spring Boot 4.1.0 · Gradle 9.6 (Kotlin DSL) · PostgreSQL 17 · Redis 7 · Flyway · Swagger UI**

---

## Run it

You need Docker and any JDK 21+. Gradle downloads the JDK 25 toolchain itself, so the JDK on your
machine only has to be new enough to run Gradle.

```bash
cd server
docker compose up -d                 # Postgres on 5436, Redis on 6380

JWT_SECRET='change-me-at-least-32-bytes-long-please' \
BOOTSTRAP_ADMIN_PASSWORD='ChooseSomethingStrong!' \
OTP_DEV_CODE='123456' \
./gradlew bootRun
```

- API: **http://localhost:8090/api/v1**
- Swagger UI: **http://localhost:8090/swagger-ui.html**
- Health: **http://localhost:8090/actuator/health**

Ports are deliberately off the defaults so this stack does not collide with anything else running.

`OTP_DEV_CODE` fixes every one-time code to that value and returns it in the response, so you are not
blocked on an SMS gateway. It logs a warning on every startup and must never be set in production.
`BOOTSTRAP_ADMIN_PASSWORD` creates the first super admin **only when no admin exists** — no credential
is written into a migration, where it would live in git forever and be identical on every deployment.

```bash
./gradlew test          # unit tests, no infrastructure needed
./gradlew build         # compile, test, jar
```

---

## Why it is shaped this way

**No payment gateway, by design.** A member pays their batch coordinator offline and reports a
transaction reference. `POST /me/registration/payment-report` records a *claim*; only a coordinator's
`CONFIRMED` decision asserts that money arrived. There is no webhook and nothing to reconcile.

**Members never have a password.** They prove they hold a mobile number. The youngest alum is in
their teens, the oldest passed SSC in 1968, and a password is a thing the second group will lose and
then stop coming back. Only admins have `admin_credential`, hashed with Argon2id.

**An admin token is never a member token.** They carry different audiences inside the signature, so a
member token on `/admin/**` is refused by the filter, not by a route rule. Batch scope is *not* in
the token — it is re-read per request (through a 15-minute cache that is evicted the moment an
account is edited), because a scope baked into an eight-hour token keeps working for eight hours
after it is revoked.

**`review` is append-only.** Every decision is a row: who, when, which batch, and the reason if it
was a rejection. A reversal is a new row. A bulk approval of forty writes forty rows and is
indistinguishable from forty clicks, which is the point — the question that gets asked later is
"who approved him, and were they allowed to?"

**Bulk decisions are partial, not transactional.** A coordinator who ticks forty rows should not lose
thirty-nine sound decisions because one moved out of their scope while they were reading. Each id is
decided on its own; anything refused comes back in `skipped` with a reason, in Bangla and English.

**Errors speak Bangla.** Every problem response carries `code` and `messageBn` beside the RFC 9457
fields. An English-only error is an error most of these users cannot act on.

---

## Layout

```
src/main/java/bd/sammalani/alumni/
├── config/          AppProperties, Security, Cache, OpenAPI, BootstrapRunner
├── common/          error (ApiException + ProblemDetail handler), web (CursorPage, Cursors),
│                    util (PhoneNumbers), jpa (Auditable)
├── security/        JwtService, JwtAuthenticationFilter, AuthPrincipal, CurrentUser
├── auth/            OTP challenges, claim/register flow, member sessions
├── admin/           AdminSession + scope resolution, review queue, bulk decisions,
│                    stats, coordinator accounts
└── domain/          person · batch · event · registration · payment · review · notice · referral
                     (each with its entity, repository, service, controller and DTOs)
```

Flyway owns the schema (`src/main/resources/db/migration`) and Hibernate runs with
`ddl-auto: validate`, so entity drift fails at startup rather than in production.

---

## Performance notes

- **Keyset pagination**, never OFFSET. `GET /admin/applications` orders by `(submitted_at, id)` desc
  and the cursor compares the same pair, so every page is one index scan. With several coordinators
  deciding rows underneath each other, OFFSET would silently skip an applicant each time a row above
  them left the filter — verified with `EXPLAIN`: `Index Scan using registration_member_queue_idx`.
- **Four indexes for one query.** `registration` carries a denormalised `batch_year` and
  `payment_status` so the queue filters and pages without joining; the four composite indexes are
  exactly the four shapes that query takes.
- **Fixed query count per page.** A page of ten and a page of a hundred both cost four round trips:
  registrations (person fetched), latest payment per row, latest decision per registration, latest
  decision per payment. No N+1.
- **`open-in-view: false`.** No lazy loading from a serialiser; every query is deliberate.
- **Caches declare their exact value type** (`CacheConfig`). A cached `List<X>` stored as bare JSON
  comes back as a list of maps and fails on the cast; the alternative — polymorphic default typing —
  writes class names into Redis and instantiates whatever it reads back, which turns Redis write
  access into code execution. Naming the type per cache costs a line each and needs neither.
- **The queue itself is never cached.** A coordinator must not be shown a row someone else decided
  thirty seconds ago.
- **Virtual threads on.** This service is almost entirely waiting on Postgres and Redis.

---

## Verified

`./gradlew test` — 17 unit tests pass (cursor encoding, page-size clamping, phone normalisation
including Bangla numerals).

End-to-end against real Postgres and Redis, 32 checks, all passing: public reads; sign-up → OTP →
session; member-token-refused-on-admin and admin-token-refused-on-member; server-side pricing
(1500 + 1200 + free under-five = 2700); submit; payment report; duplicate TrxID refused by the
partial unique index (409); admin login and wrong-password refusal; queue contents; rejection without
a reason refused (400); bulk approve with one good id and one unknown id → 1 updated, 1 skipped with
the reason named; bulk payment confirmation; stats; both decisions present in `review`; and a scoped
coordinator who cannot see, decide, or administer outside their batch (403).

Pagination walked separately over 24 submissions: 3 pages of 10/10/4, 24 unique ids with no
duplicates or gaps, newest-first order, and a corrupt cursor starting from the top instead of failing.

---

## Not built yet

The ambassador console, CSV import and the dedupe queue, merge/unmerge, campaign sending, exports,
check-in, and the media/album/post content types. They are specified in
[`docs/00-SYSTEM-DESIGN.md`](../docs/00-SYSTEM-DESIGN.md) §5 and have schema room here, but this pass
covers what the web app actually calls. SMS delivery is also stubbed: `OtpService` logs the code
where the gateway call belongs.
