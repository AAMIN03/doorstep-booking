# Doorstep Vehicle Service Booking

Spring Boot API that books doorstep battery services, assigns technicians under zone/skill/shift rules, and prevents double-booking with MySQL row locks.

## Stack

Java 21, Spring Boot 3.4.x, Maven, MySQL 8.4, Flyway, JPA, Testcontainers, Docker Compose.

## Run with Docker

```bash
cp .env.example .env
docker compose up --build
```

API: `http://localhost:8080`  
Health: `http://localhost:8080/actuator/health`

## Run locally (MySQL already up)

```bash
docker compose up -d mysql
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Logging and request correlation

- Logs are **structured JSON** on the console via `logback-spring.xml` and `logstash-logback-encoder` (`@timestamp`, `level`, `logger_name`, `message`, MDC, plus `service` from `spring.application.name`).
- Every HTTP request gets a correlation id: clients may send **`X-Request-Id`**; if omitted, the API generates a UUID.
- The id is stored in SLF4J MDC as **`requestId`** (included in JSON logs) and returned on the response as **`X-Request-Id`** (`RequestIdFilter`, registered at highest filter precedence).

Micrometer distributed tracing is **not** enabled; use `X-Request-Id` for log correlation.

## Rate limiting (Bucket4j + Redis)

Multiple app instances share counters in **Redis** via [Bucket4j](https://bucket4j.com/) (`bucket4j-core` + `bucket4j-redis` with Lettuce). A servlet filter (`RateLimitFilter`) applies a **token bucket** per client IP to booking API routes: `/availability`, `/bookings`, `/technicians/{id}/schedule`. Actuator is excluded.

| Property | Default | Meaning |
|----------|---------|---------|
| `rate-limit.enabled` | `true` | Master switch (`RATE_LIMIT_ENABLED`) |
| `rate-limit.requests-per-minute` | `20` | Bucket capacity and greedy refill per window |
| `rate-limit.window-minutes` | `1` | Refill window length |
| `rate-limit.redis.host` | `localhost` | Redis host (`REDIS_HOST`) |
| `rate-limit.redis.port` | `6379` | Redis port (`REDIS_PORT`) |

If Redis is **not reachable at startup**, the app **still starts** and rate limiting is **skipped** (logged warning, no-op). Tests set `rate-limit.enabled=false` in `src/test/resources/application.properties`.

When the limit is exceeded, the API returns **HTTP 429** with `ErrorResponse` code `RATE_LIMIT_EXCEEDED` and message `Rate limit exceeded. Try again later.`

**Run Redis locally:**

```bash
docker run -d --name doorstep-redis -p 6379:6379 redis:7-alpine
```

## Tests

```bash
./mvnw test
```

Requires Docker for Testcontainers MySQL 8.4 integration tests. Unit/slice tests (`SchedulingRulesTest`, `GlobalExceptionHandlerTest`, `RequestIdFilterTest`, `RequestIdFilterWebMvcTest`, `RateLimitFilterTest`, `RateLimitFilterWebMvcTest`, `ActuatorHealthMockMvcTest`) run without Docker.

`ActuatorHealthMockMvcTest` hits `GET /actuator/health` with a minimal Spring context (no datasource): expects **200**, `{"status":"UP"}`, and **`X-Request-Id`**.

**`BookingApiIntegrationTest`** — `GET /bookings?phone=` (paginated list):

| Test method | Scenario |
|-------------|----------|
| `listBookingsByPhoneHappyPath` | 200, `PageResponse` shape, one booking |
| `listBookingsByPhonePagination` | `page` / `size`, second page, empty page past end |
| `listBookingsByPhoneEmptyResult` | Valid phone, zero bookings |
| `listBookingsByPhoneInvalidPhone` | 400 `VALIDATION_ERROR` |
| `listBookingsMissingPhoneParameter` | 400 `INVALID_PARAMETER` |
| `listBookingsByPhoneStatusFilter` | `status=CONFIRMED` / `CANCELLED` |
| `listBookingsByPhoneDateRangeFilter` | `startDate` / `endDate` (IST, inclusive) |
| `listBookingsByPhoneInvalidDateRange` | 400 when `endDate` before `startDate` |
| `listBookingsByPhoneSort` | `sort=startTime,asc` / `desc` |

**`GET /technicians/{id}/schedule`** (pagination + filters):

| Test method | Scenario |
|-------------|----------|
| `technicianScheduleOrdered` | 200, nested `PageResponse` |
| `technicianScheduleStatusFilter` | `status=CANCELLED` |
| `technicianScheduleDateRangeFilter` | `startDate` / `endDate` vs single `date` |
| `technicianScheduleInvalidDateRange` | 400 when range inverted |
| `technicianScheduleSort` | default asc; `sort=startTime,desc` |

**Cancel coverage:**

| Test method | Endpoint | Expected |
|-------------|----------|----------|
| `createGetCancelFlow` | `POST …/cancel` | 200 success |
| `cancelUnknownBookingReturnsNotFound` | `POST /bookings/999999/cancel` | 404 `UNKNOWN_BOOKING` |
| `lateCancellationReturnsConflict` | `POST …/cancel` | 409 `LATE_CANCELLATION` |
| `cancelAlreadyCancelledBookingReturnsConflict` | `POST …/cancel` (twice) | 409 `INVALID_CANCELLATION` |
| `cancelCompletedBookingReturnsConflict` | `POST …/cancel` | 409 `INVALID_CANCELLATION` (completed) |

All error tests assert full `ErrorResponse` JSON via `ApiErrorTestSupport`.

## Timezone policy

- Business rules use **Asia/Kolkata**.
- API accepts/returns `ZonedDateTime` with offset (e.g. `+05:30`).
- Booking `start_time` / `end_time` stored as **UTC** `DATETIME(6)`.
- Technician shifts stored as **minutes from midnight** (IST wall-clock).

## API examples

```bash
curl "http://localhost:8080/availability?serviceId=1&pincode=400001&date=2026-10-20"

curl "http://localhost:8080/bookings?phone=9876543210&page=0&size=20&sort=startTime,desc"

curl -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-1" \
  -d '{"customerPhone":"9876543210","serviceId":1,"pincode":"400001","startTime":"2026-10-20T10:00:00+05:30"}'

curl http://localhost:8080/bookings/1
curl -X POST http://localhost:8080/bookings/1/cancel  # 409 if too late, already cancelled, or completed; 404 if unknown id
curl "http://localhost:8080/technicians/1/schedule?date=2026-10-20&page=0&size=20"
```

## Pagination and list APIs

**`GET /bookings`** — customer history (phone **required**).

| Query param | Required | Description |
|-------------|----------|-------------|
| `phone` | yes | 10-digit Indian mobile (`6`–`9` start) |
| `status` | no | `CONFIRMED`, `CANCELLED`, `COMPLETED` |
| `startDate`, `endDate` | no | ISO date; filter booking `startTime` (IST, inclusive days) |
| `page`, `size`, `sort` | no | Spring Data pagination (default `size=20`, `sort=startTime,desc`) |

Response: `PageResponse` — `content`, `page`, `size`, `totalElements`, `totalPages`.

**`GET /technicians/{id}/schedule`** — `date` required; optional `startDate`/`endDate` (IST range), `status`, `page`/`size`/`sort`. Bookings nested under `bookings` as a `PageResponse` (default `sort=startTime,asc`; omits `CANCELLED` when `status` omitted).

**`GET /availability`** — unchanged (slot list, not paginated).

## Error responses

All API errors from `GlobalExceptionHandler` use the same JSON shape:

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

- **`error`** — HTTP status name (`BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, …).
- **`code`** — application `ErrorCode` (see below).
- **`message`** — human-readable detail using these conventions:
  - **Body validation** (`VALIDATION_ERROR`, 400): `Field '{field}': {detail}`
  - **Missing header** (`VALIDATION_ERROR`, 400): `Header '{name}' is required`
  - **Query/path parameter** (`INVALID_PARAMETER`, 400): `Parameter '{name}': {detail}` (e.g. `is required`, `invalid value '…'`)
  - **Unreadable JSON** (`MALFORMED_REQUEST`, 400): `Request body is malformed or unreadable`
  - **Domain rules** (`ApiException`): fixed strings from services (e.g. slot/horizon, not found, conflict)

**Handler mapping** (`GlobalExceptionHandler`): binding problems never return 500; they use the rows below for 400.

| HTTP | `error` | `code` | When / typical `message` |
|------|---------|--------|---------------------------|
| 400 | `BAD_REQUEST` | `VALIDATION_ERROR` | `@Valid` body → `Field '{field}': {detail}`; missing header → `Header '{name}' is required` |
| 400 | `BAD_REQUEST` | `INVALID_PARAMETER` | Missing query param → `Parameter '{name}': is required`; type mismatch → `Parameter '{name}': invalid value '{value}'`; `@Validated` param → `Parameter '{path}': {violation}` |
| 400 | `BAD_REQUEST` | `MALFORMED_REQUEST` | `Request body is malformed or unreadable` |
| 400 | `BAD_REQUEST` | `INVALID_SLOT` | `Start time must align to 30-minute boundaries` |
| 400 | `BAD_REQUEST` | `OUTSIDE_HORIZON` | `Booking must be between 2 hours and 7 days from now` |
| 404 | `NOT_FOUND` | `UNKNOWN_SERVICE` | `Service not found` |
| 404 | `NOT_FOUND` | `UNKNOWN_PINCODE` | `Pincode not served` |
| 404 | `NOT_FOUND` | `UNKNOWN_BOOKING` | `Booking not found` |
| 404 | `NOT_FOUND` | `UNKNOWN_TECHNICIAN` | `Technician not found` |
| 409 | `CONFLICT` | `NO_TECHNICIAN_AVAILABLE` | `No technician is available for the requested slot.` |
| 409 | `CONFLICT` | `IDEMPOTENCY_CONFLICT` | `Idempotency key reused with different payload` |
| 409 | `CONFLICT` | `INVALID_CANCELLATION` | `Booking already cancelled` or `Completed booking cannot be cancelled` |
| 409 | `CONFLICT` | `LATE_CANCELLATION` | `Cancellation must be at least 1 hour before start` |
| 409 | `CONFLICT` | `CONFLICT` | `Booking temporarily contended, please retry` or `Idempotency conflict, retry` |
| 429 | `TOO_MANY_REQUESTS` | `RATE_LIMIT_EXCEEDED` | `Rate limit exceeded. Try again later.` |
| 500 | `INTERNAL_SERVER_ERROR` | `INTERNAL_ERROR` | `Unexpected error` |

`ErrorCode.NOT_FOUND` exists in the enum but is **not** used; 404 responses use `UNKNOWN_*` codes above.

## Booking rules (assumptions)

- Slots on 30-minute boundaries; service occupies `[start, end)`.
- 30-minute **post-job** travel buffer between confirmed bookings.
- Horizon: **inclusive** from `now + 2 hours` through `now + 7 days` (injectable `Clock`).
- Cancellation allowed when `now + 1 hour <= start`.
- Completed bookings stay in history but **do not** block future slots (only `CONFIRMED` blocks).
- Tie-break for assignment: lowest technician id after fewest non-cancelled bookings that IST day.
- Idempotent `POST /bookings`: same key + payload → **200** with same body; first create → **201**.

## Package layout

`com.booking` → `config`, `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `service`

## Environment

See `.env.example` (`SPRING_DATASOURCE_*`, `SERVER_PORT`).
