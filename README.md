# Doorstep Vehicle Service Booking

Spring Boot API that books doorstep battery services, assigns technicians under zone/skill/shift rules, and prevents double-booking with MySQL row locks.

**Stack:** Java 21, Spring Boot 3.4.x, Maven, MySQL 8.4, Flyway, JPA, Redis (rate limits; started by Compose), Testcontainers, Docker Compose.

---

## How to run

### Prerequisites

- **Java 21** (JDK)
- **Docker** (recommended for MySQL and full stack)
- **Redis** comes up with `docker compose up`. A host-run app still starts if Redis is down and skips rate limiting (see [Rate limiting](#rate-limiting-bucket4j--redis)).

### Option A — App + MySQL with Docker Compose (recommended)

```bash
cp .env.example .env   # optional; defaults work with compose
docker compose up --build
```

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- MySQL: `localhost:3306` (user `app` / password `app`, database `doorstep`)
- Redis: `localhost:6379` (rate limiting is on for the Compose app)

Flyway runs migrations and seed data on startup.

### Option B — App on host, MySQL in Docker

```bash
docker compose up -d mysql
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile points at the same MySQL URL as Compose (`application-local.properties`). Ensure port `3306` is free.

### Option C — JAR

```bash
docker compose up -d mysql
./mvnw -DskipTests package
java -jar target/doorstep-booking-0.0.1-SNAPSHOT.jar
```

Override datasource or port via environment (see [Environment](#environment)).

### Verify

```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP"}  (+ X-Request-Id header)
```

---

## How to run tests

### Full suite (needs Docker)

Integration tests use **Testcontainers** with MySQL 8.4. Docker Desktop (or equivalent) must be running.

```bash
./mvnw test
```

### Without Docker (unit / slice tests only)

```bash
./mvnw test -Dtest=SchedulingRulesTest,BookingAssignmentTest,TimeServiceTest,GlobalExceptionHandlerTest,RequestIdFilterTest,RequestIdFilterWebMvcTest,RateLimitFilterTest,RateLimitFilterWebMvcTest,ActuatorHealthMockMvcTest
```

Tests disable Redis rate limiting via `src/test/resources/application.properties` (`rate-limit.enabled=false`).

### What is covered

| Area | Main test classes |
|------|-------------------|
| Scheduling rules and assignment | `SchedulingRulesTest`, `BookingAssignmentTest` |
| Error JSON | `GlobalExceptionHandlerTest`, `BookingApiIntegrationTest` |
| Booking / cancel / list / schedule | `BookingApiIntegrationTest` |
| Concurrency / locks | `BookingConcurrencyIntegrationTest` (see [Concurrency live check](#concurrency-live-check)) |
| Request ID | `RequestIdFilterTest`, `RequestIdFilterWebMvcTest` |
| Rate limit 429 | `RateLimitFilterTest`, `RateLimitFilterWebMvcTest` |
| Actuator health | `ActuatorHealthMockMvcTest` |

---

## Concurrency live check

Two ways to watch two `POST /bookings` calls race for the same technician. Docker must be running for the Testcontainers test. The script needs the API from [How to run](#how-to-run) already up on port 8080.

### Testcontainers

`BookingConcurrencyIntegrationTest` starts MySQL 8.4 in Docker, applies the Flyway migrations, and sends overlapping creates through the API. In the log, look for the MySQL container starting, then the four cases:

- same slot, different idempotency keys → one **201**, one **409**
- same idempotency key → one booking, the other call returns that same id
- adjacent slots inside the travel buffer → one **201**, one **409**
- non-overlapping slots → both **201**, different technicians

```bash
./mvnw test -Dtest=BookingConcurrencyIntegrationTest
```

### Script

`scripts/concurrent-bookings.sh` fires two curls at the same time. Both use the same JSON body and a different `Idempotency-Key`. Pincode `400058` and service `1` (battery replacement) have one technician, so the calls contend for that slot.

```bash
./scripts/concurrent-bookings.sh
```

Expect one **201** and one **409**. The default `startTime` is tomorrow at 15:00 IST. Run the same time again and both return **409**, because that slot is already taken. Pick another slot with:

```bash
START_TIME='2026-10-21T15:00:00+05:30' ./scripts/concurrent-bookings.sh
```

`startTime` must use `+05:30` and fall between 2 hours and 7 days from now. `BASE_URL` overrides the host (default `http://localhost:8080`).

---

## API examples

Base URL: `http://localhost:8080`. All booking API responses may include **`X-Request-Id`**.

**Customer identity (no login):** send **`X-Customer-Phone`** (10-digit Indian mobile) on `GET /bookings`, `GET /bookings/{id}`, and `POST /bookings/{id}/cancel`. It must match the booking’s `customerPhone` (or the `phone` query param on list). Wrong or missing header → **400**; wrong owner on get/cancel → **404** `UNKNOWN_BOOKING`. Create booking uses `customerPhone` in the JSON body only.

Create booking requires **`Idempotency-Key`**.

### Availability

```bash
curl -s "http://localhost:8080/availability?serviceId=1&pincode=400001&date=2026-10-20"
```

Returns `availableSlots` for the day (advisory; final slot confirmed only on `POST /bookings`).

### Create booking

```bash
curl -s -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d '{
    "customerPhone": "9876543210",
    "serviceId": 1,
    "pincode": "400001",
    "startTime": "2026-10-20T10:00:00+05:30"
  }'
```

- First success: **201** + `Location: /bookings/{id}`
- Same key + same body again: **200** with the same booking (idempotent)

### Get and cancel

```bash
curl -s http://localhost:8080/bookings/1 \
  -H "X-Customer-Phone: 9876543210"

curl -s -X POST http://localhost:8080/bookings/1/cancel \
  -H "X-Customer-Phone: 9876543210"
```

Cancel: **200** on success; **404** unknown id; **409** too late, already cancelled, or completed.

### List bookings by phone (paginated)

```bash
curl -s "http://localhost:8080/bookings?phone=9876543210&page=0&size=20&sort=startTime,desc" \
  -H "X-Customer-Phone: 9876543210"

# Optional filters (IST dates, inclusive); header must match phone=
curl -s "http://localhost:8080/bookings?phone=9876543210&status=CONFIRMED&startDate=2026-10-20&endDate=2026-10-22" \
  -H "X-Customer-Phone: 9876543210"
```

### Technician schedule (paginated)

```bash
curl -s "http://localhost:8080/technicians/1/schedule?date=2026-10-20&page=0&size=20"

curl -s "http://localhost:8080/technicians/1/schedule?date=2026-10-20&startDate=2026-10-20&endDate=2026-10-21&status=CANCELLED&sort=startTime,desc"
```

Schedule body: `{ "technicianId", "date", "bookings": { "content", "page", "size", "totalElements", "totalPages" } }`.

### Endpoint summary

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/availability` | `serviceId`, `pincode`, `date` |
| `GET` | `/bookings` | `phone` required; optional `status`, `startDate`, `endDate`, `page`, `size`, `sort` |
| `POST` | `/bookings` | `Idempotency-Key` header; JSON body |
| `GET` | `/bookings/{id}` | |
| `POST` | `/bookings/{id}/cancel` | |
| `GET` | `/technicians/{id}/schedule` | `date` required; optional range, `status` (default: every status), pagination |
| `GET` | `/actuator/health` | Not rate-limited |

---

## Ambiguous requirements — assumptions we made

The brief leaves several points open. We picked one reasonable interpretation for each, implemented it, and documented it here (reviewers: this is intentional).

| Topic | What was unclear | **Our assumption** | Where it lives |
|--------|------------------|--------------------|----------------|
| **Auth** | “Customer” with no login spec | **No OAuth/API keys.** Customer identity = **10-digit phone**. Reads/cancel require **`X-Customer-Phone`** matching the booking; no SMS OTP (trusted client, e.g. app already logged the user in). | `CustomerIdentity`, `BookingService`, README |
| **Identity leaks** | Whether wrong customer gets 403 vs 404 on `GET /bookings/{id}` | **404 `UNKNOWN_BOOKING`** when phone doesn’t match (same as missing id) so callers can’t probe ids. | `BookingService.requireBookingOwnedBy` |
| **Time zone** | “Single timezone” vs storage | **IST only** on the API (`+05:30` / `Asia/Kolkata`); **UTC instants** in MySQL internally. All `date` query params = **IST calendar days**. | `TimeService`, `application.properties`, tests |
| **Availability vs book** | Can two users book the same slot? | **`GET /availability` is advisory** (no locks). **`POST /bookings`** is authoritative; second caller gets **409** or no technician after DB locks. | `AvailabilityService`, `BookingTxService`, `DESIGN.md` |
| **Horizon** | Inclusive/exclusive “2h–7d” window | **`now + 2h` ≤ start ≤ `now + 7d`** (both ends inclusive on instant comparison). | `SchedulingRules.withinHorizon` |
| **Slot shape** | Buffer before vs after job | **30 min after** each **confirmed** job (travel), not before. Slot alignment on **IST** half-hours. | `SchedulingRules` |
| **History vs blocking** | Do past/completed jobs block? | Only **`CONFIRMED`** blocks; **cancelled** and **completed** do not. | `SchedulingRules.conflicts`, repositories |
| **Assignment** | Tie when multiple techs qualify | Fewest **non-cancelled** jobs that **start that IST day**, counted from the rows locked in the booking transaction, then **lowest technician id**. | `BookingTxService.chooseTechnician` |
| **Cancellation** | Cutoff wording | Cancel allowed iff **`now + 1h ≤ start`**; cannot cancel **completed** bookings. | `SchedulingRules`, `BookingService.cancel` |
| **Technician schedule** | Customer vs ops API, and whether cancelled jobs appear | **`GET /technicians/{id}/schedule`** is **open** (no phone header)—ops/dispatch view. Default returns **every status**, in start-time order. `status` narrows it. | `BookingController`, `BookingQueryService` |
| **Shift vs midnight** | Does a job that ends at 00:00 still “fit” a daytime shift? | **No.** Start and end must both fall inside the shift on the **start date**. Comparing clock times alone would treat 00:00 as before 20:00. | `SchedulingRules.fitsShift` |
| **Rate limiting** | Per user vs per IP vs global | **Per client IP** (first `X-Forwarded-For` hop), **20 req/min** default. Compose starts Redis and turns limiting on. A host-run app with Redis down does not limit. | `RateLimitFilter`, `docker-compose.yml` |
| **Idempotency** | Which operations | **`POST /bookings` only**; hash = phone + service + pincode + startTime. | `BookingService`, `IdempotencyService` |
| **Notifications** | Required at submit time | **Not implemented**; booking API stays sync. **Outbox + workers** described in `DESIGN.md` for SMS/WhatsApp later. | `DESIGN.md` §4 |
| **Multi-instance** | In-memory limits useless | **Bucket4j + Redis** for shared counters; not required for single-node dev. | `RedisRateLimitOperations` |

If a product owner chooses differently (e.g. OTP auth, slot holds, per-phone rate limits), the table above is the baseline we coded against.

### Can I use AI coding assistants?

I used Cursor as a guide and to help write the code out. The service is long, and typing every class by hand was not practical in the time we had. The ideas, the design, and the logic in the code are mine.

---

## Assumptions (enforced rules)

These rules are enforced in code (`SchedulingRules`, `BookingTxService`, seed data). See `DESIGN.md` for concurrency and scale discussion.

### Time and locale (IST only)

- **Single timezone:** **Asia/Kolkata (IST)** for all business rules, query dates (`date`, `startDate`, `endDate`), shifts, and API datetimes.
- **Requests:** `startTime` on create must use **`+05:30`** or **`Asia/Kolkata`**; other zones (e.g. `Z`) → **400** `VALIDATION_ERROR`.
- **Responses:** booking and slot times are returned in IST (`+05:30`); error `timestamp` is IST.
- **Storage:** `start_time` / `end_time` in MySQL are **UTC instants** (internal); clients always think in IST.
- Technician shifts: **minutes from midnight** IST wall-clock.

### Slots and horizon

- Appointments start on **30-minute** boundaries.
- Service duration comes from the `services` table; booking interval is **`[start, end)`**.
- Bookings allowed only from **`now + 2 hours`** through **`now + 7 days`** (inclusive window; uses injectable `Clock`).
- **`GET /availability` does not lock slots**; two customers can see the same slot; `POST /bookings` is authoritative.
- Availability and booking both look at confirmed work from **30 minutes before** the IST day through **30 minutes after** it, so a buffer that crosses midnight is visible on both paths.

### Travel buffer and assignment

- **30 minutes** after each **confirmed** job before the same technician can start another (travel buffer).
- Only **`CONFIRMED`** bookings block slots; **cancelled** and **completed** do not. Completed jobs still count toward that day's load.
- The job's start and end must both fall inside the technician's shift on the booking's **IST start date**. An end after midnight is outside the shift.
- Eligible technicians: correct **zone** (pincode), **skill** for service, and shift covering the slot.
- Assignment: technician with **fewest non-cancelled** jobs that **start** that IST day, then **lowest technician id**. The count uses the bookings locked in that transaction.

### Cancellation

- Allowed only if **`now + 1 hour <= start`**.
- Cannot cancel already **cancelled** or **completed** bookings.

### Customer and API conventions

- **No authentication** — identity is the **customer phone** (`X-Customer-Phone` header on reads/cancel; body on create). This is **not** OTP-verified; treat as a trusted-channel assumption (e.g. mobile app already authenticated the user).
- **Indian mobile** numbers: 10 digits, leading digit **6–9** (`customerPhone`, `GET /bookings?phone=`, `X-Customer-Phone`).
- **Idempotency:** `Idempotency-Key` required on create; same key + same payload → same booking (**200**); different payload → **409** `IDEMPOTENCY_CONFLICT`.

### Infrastructure (not product rules)

- **Single MySQL** primary for writes; no read replicas in this repo.
- **Rate limiting** per client IP (default 20 requests/minute on booking routes). Compose runs Redis; without Redis the app still starts and does not limit.
- **No** WhatsApp/SMS/push notifications in this codebase yet (outbox described in `DESIGN.md`).

---

## Timezone policy

- **IST everywhere at the API boundary** (`TimeService`, `spring.jackson.time-zone=Asia/Kolkata`, `Clock` in IST).
- **UTC in the database** only — not exposed as a client-facing zone.

---

## Pagination and list APIs

**`GET /bookings`** — `PageResponse`: `content`, `page`, `size`, `totalElements`, `totalPages`. Default `size=20`, `sort=startTime,desc`.

**`GET /technicians/{id}/schedule`** — `bookings` nested `PageResponse`. Default `sort=startTime,asc` and **every status**. Pass `status` to keep one status only.

---

## Error responses

All API errors use `ErrorResponse`: `timestamp`, `status`, `error`, `code`, `message`, `path`.

```json
{
  "timestamp": "2026-10-18T14:00:00+05:30",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "VALIDATION_ERROR",
  "message": "Field 'customerPhone': must be a valid Indian mobile number",
  "path": "/bookings"
}
```

| HTTP | `code` (examples) |
|------|-------------------|
| 400 | `VALIDATION_ERROR`, `INVALID_PARAMETER`, `MALFORMED_REQUEST`, `INVALID_SLOT`, `OUTSIDE_HORIZON` |
| 404 | `UNKNOWN_SERVICE`, `UNKNOWN_PINCODE`, `UNKNOWN_BOOKING`, `UNKNOWN_TECHNICIAN` |
| 409 | `NO_TECHNICIAN_AVAILABLE`, `IDEMPOTENCY_CONFLICT`, `INVALID_CANCELLATION`, `LATE_CANCELLATION`, `CONFLICT` |
| 429 | `RATE_LIMIT_EXCEEDED` |
| 500 | `INTERNAL_ERROR` |

Message patterns: `Field '…'`, `Parameter '…'`, `Header '…' is required` — see `GlobalExceptionHandler` and README history in git for full table.

---

## Logging and request correlation

- **JSON logs** via `logback-spring.xml` + Logstash encoder.
- **`X-Request-Id`**: client may send; otherwise UUID; echoed on response; MDC key `requestId`.
- **Access log** for `GET /availability`, `POST /bookings`, `GET /bookings`, `GET /bookings/{id}`, and `POST /bookings/{id}/cancel`. One JSON line per call with `requestId`, `httpMethod`, `path`, `query`, `status`, `requestBody`, and `responseBody`.
- **Service debug logs** (`com.booking` at DEBUG): assignment, availability counts, idempotent replay, list, and schedule totals.
- **WARN and ERROR include `stack_trace`**. Client failures (4xx) are WARN. Unexpected failures (5xx) are ERROR. The Redis connection failure is WARN with the exception.

---

## Rate limiting (Bucket4j + Redis)

`docker compose up` starts Redis and points the app at it, so limiting is active in that stack. On a host run, Redis defaults to `localhost:6379`; if it is down, limiting is skipped and the app still serves traffic.

Shared token bucket per IP on `/availability`, `/bookings`, `/technicians/{id}/schedule` (not actuator).

| Property | Default |
|----------|---------|
| `rate-limit.enabled` | `true` |
| `rate-limit.requests-per-minute` | `20` |
| `rate-limit.window-minutes` | `1` |
| `rate-limit.redis.host` | `localhost` |
| `rate-limit.redis.port` | `6379` |

Env aliases: `RATE_LIMIT_ENABLED`, `RATE_LIMIT_REQUESTS_PER_MINUTE`, `REDIS_HOST`, `REDIS_PORT`.

Host-only Redis, if you are not using Compose:

```bash
docker run -d --name doorstep-redis -p 6379:6379 redis:7-alpine
```

Exceeded limit → **429** + `RATE_LIMIT_EXCEEDED`.

---

## Environment

`.env.example`:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SERVER_PORT` (default `8080`)
- `REDIS_HOST`, `REDIS_PORT`, `RATE_LIMIT_ENABLED`

---

## Package layout

`com.booking` → `config`, `controller`, `dto`, `entity`, `enums`, `exception`, `ratelimit`, `repository`, `service`, `validation`, `web`

---

## Further reading

**`DESIGN.md`** — concurrency (row locks), scale, availability caching, notifications outbox, reassignment, and roadmap.
