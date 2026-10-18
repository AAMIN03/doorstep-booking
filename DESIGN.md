# Doorstep booking — design document

## Part B — Design write-up

Bullets and reasoning for the Amara Raja–style review. Implementation detail lives in the appendix.

---

### 1. Your concurrency choice

**How we prevent double-booking**

`POST /bookings` runs in one transaction (`BookingTxService.create`):

1. Resolve eligible technicians (zone + skill).
2. `SELECT … FOR UPDATE` on those technician rows, **always in ascending id order** (deadlock avoidance).
3. `SELECT … FOR UPDATE` on **confirmed** bookings overlapping the slot window (plus travel buffer).
4. Pick a technician from **those locked rows**: drop anyone whose shift or buffer conflicts, then fewest non-cancelled jobs that **start** that IST day, then lowest id. The count is the locked list, not a second non-locking query. Under repeatable read that second query would keep the snapshot from the start of the transaction and could miss a booking the other transaction just committed, so two non-overlapping requests could both choose the same technician.
5. Insert booking (idempotency row in the same transaction).

The authoritative guarantee is **InnoDB row locks in MySQL**, not in-memory state. Multiple API instances are safe because contention is serialized in the database.

```text
  Client                    API (any instance)                 MySQL
    |  POST /bookings              |                              |
    |----------------------------->|  BEGIN                       |
    |                              |  FOR UPDATE technicians      |
    |                              |----------------------------->|
    |                              |  FOR UPDATE bookings (range) |
    |                              |----------------------------->|
    |                              |  insert booking              |
    |                              |  COMMIT                      |
    |<-----------------------------|  201                         |
```

**Why this over alternatives**

| Approach | Why we did / didn’t choose it |
|----------|-------------------------------|
| **Pessimistic row locks (chosen)** | Matches “assign one of N technicians under rules” in one transaction; second waiter blocks, then sees fresh rows. Fits InnoDB well. |
| **Optimistic versioning** | High conflict rate on hot slots → many retries and angry UX; still need a conflict story for buffer rules. |
| **Unique DB constraint on (technician_id, start_time)** | Doesn’t encode **duration**, **30‑min buffer**, or **cancelled vs confirmed**; false rejects or needs exclusion constraints we don’t have. |
| **Distributed lock (Redis/etcd)** | Extra failure mode and fencing; DB already holds the truth for bookings. Could add for cross-service flows, not needed for single-DB booking insert today. |

**Where it breaks**

- **`GET /availability` is advisory** — it does not take locks. Customers can see a slot that disappears before `POST` (by design unless we add holds or stronger coupling).
- **Coarse locking** — we lock *all* eligible technicians in the zone/skill set, not only the winner → more contention as pools grow.
- **Deadlocks / lock waits** — overlapping technician sets under load → `PessimisticLockingFailureException` → HTTP 409 “retry”; clients must retry; sustained contention raises latency.
- **No DB unique constraint on intervals** — safety depends on **every** write path using this protocol; a buggy admin script or future endpoint that inserts without locks could double-book.
- **Read replicas** — if we route availability reads to replicas with lag, displayed capacity can be stale (see §3).

---

### 2. Scale

**Scenario:** 50 cities, ~5,000 technicians, ~200k bookings/day (~2.3/s average), **10× Monday-morning spikes** on availability and booking.

**What changes first (in order)**

1. **`GET /availability`** — already the fan-out endpoint (slots × technicians). Add **short-TTL cache** (Redis) keyed by `(serviceId, pincode, date)`, rate limiting (implemented via Bucket4j + Redis), and **horizontal API scale** behind a load balancer.
2. **MySQL write path** — monitor **InnoDB row lock wait time** and deadlock rate; tune pool size; consider **narrowing locks** (lock only chosen technician after cheap pre-filter) before sharding.
3. **Read scaling** — read replicas for **history/list/schedule** queries; keep **creates on primary**.
4. **Data partitioning** — partition hot tables by **zone or city** (or separate schemas per region) once single-primary insert rate or lock contention dominates.
5. **Async side effects** — notifications and analytics off the booking transaction (outbox).

**What to measure before each step**

| Signal | Tells you |
|--------|-----------|
| p95/p99 latency on `/availability` and `POST /bookings` | User pain; cache vs lock tuning |
| InnoDB `row_lock_waits`, deadlocks, `Threads_running` | Need narrower locks or shard |
| Booking **409 conflict** rate and retry success | Contention vs capacity |
| MySQL connections, CPU, replication lag | Replica reads for availability safe or not |
| Redis hit rate / rate-limit 429s | Cache and edge protection working |
| Queue depth for notification workers | Backlog when SMS provider is slow |

---

### 3. Availability performance

**Goal:** Keep `GET /availability` fast at spike without promising slots that are **already** taken.

**Strategy (layered)**

- **Fast path:** Precompute the 48 half-hour slots in memory; one (or few) queries for confirmed bookings in `[dayStart, dayEnd)` for eligible technicians in the pincode’s zone — indexes on `(technician_id, start_time)` and `(status, start_time)` (see appendix).
- **Cache:** Redis entry per `(serviceId, pincode, date)` with **TTL 15–60s** (tune per spike). Invalidate or version-bump on booking create/cancel in that zone/day (or accept TTL-only for simplicity).
- **Honesty to the customer:** UI copy: “Slots shown are subject to confirmation at booking.” Optionally **soft hold** (reserve slot 30–60s in Redis) before payment — not implemented; would reduce “gone” moments at cost of complexity.
- **Authoritative book:** `POST /bookings` always re-runs locks and rules; **409 / no technician** if the slot was taken — never trust cache for the final commit.
- **Avoid:** Long-lived cache without invalidation; serving availability from **lagging replicas** without showing stale risk.

---

### 4. Notifications

**Requirement:** After booking, customer gets WhatsApp/SMS; technician gets push. SMS provider is slow and sometimes down.

**Pattern: transactional outbox (not implemented yet; intended design)**

1. In the **same DB transaction** as booking insert, write an `notification_outbox` row (event type, booking id, payloads, `PENDING`).
2. **Commit** → API returns **201** immediately with booking body (idempotency unchanged).
3. **Async workers** poll outbox (or CDC), call SMS/WhatsApp/push providers with retries and exponential backoff.
4. Mark row `SENT` or `FAILED` after max retries; alert on `FAILED`; manual replay from ops tooling.
5. **Idempotent provider calls** (dedupe key per booking + channel) so retries don’t double-text.

**Why booking stays fast and reliable**

- No external HTTP in the booking transaction → provider outage does **not** roll back the booking.
- Slow provider only affects worker lag, not API latency.
- Optional: separate “notification status” on booking for support, not blocking create.

---

### 5. Change you didn’t plan for — sick-day reassignment

**Easy in the current model**

- Booking already has **`technician_id`**, **`start_time` / `end_time`**, **`status`**, zone/pincode/service — moving a job is “update assignee + prove no conflict.”
- The **same pessimistic lock + buffer re-check** used on create can run on reassignment: lock old and candidate technicians, lock bookings in range, move to new tech if valid.
- **Schedule and list APIs** already filter by technician and time.

**Hard / missing**

- **No audit trail** (who reassigned, from whom, why) — ops and disputes need `booking_events` or similar.
- **No admin/reassign API** — only create/cancel today.
- **Assignment heuristic** lives only in create path — bulk “move all of tech A’s jobs” needs a new solver (capacity, travel buffer chain across the day).
- **Notifications** — customers/techs must be told again; ties to outbox.
- **Partial day sickness** — need to select which jobs move vs which stay if tech recovers.

---

### 6. What you’d do next (another week)

| Priority | Work | Why first |
|----------|------|-----------|
| 1 | **Transactional outbox + notification worker** | Product requirement; decouples provider failures from booking. |
| 2 | **Availability cache + metrics** | Hottest read path before Monday spike; measure before over-optimizing locks. |
| 3 | **Narrow technician lock scope** | Reduce 409s and lock waits as technician pool grows. |
| 4 | **Reassign API + audit log** | Ops ask; builds on existing lock protocol. |
| 5 | **Dashboards** (latency, locks, outbox lag, 429s) | Know which of the above actually hurts in prod. |

Already in place: idempotency, structured logs + `X-Request-Id`, Redis rate limiting, paginated list/schedule APIs, Testcontainers integration tests.

---

## Appendix — Implementation reference

### A. Concurrency (code map)

| Step | Location |
|------|----------|
| Create transaction | `BookingTxService.create` |
| Technician `FOR UPDATE` | `TechnicianRepository.lockByIdsInOrder` |
| Booking `FOR UPDATE` | `BookingRepository.findNonCancelledInRangeForUpdate` |
| Cancel lock | `BookingRepository.findDetailedByIdForUpdate` |

Isolation: InnoDB **REPEATABLE READ** (default). Failed booking rolls back idempotency row with the transaction.

### B. Error handling

All API errors: `GlobalExceptionHandler` → `ErrorResponse` (`timestamp` IST, `status`, `error`, `code`, `message`, `path`).

Notable codes: `VALIDATION_ERROR`, `INVALID_PARAMETER`, `MALFORMED_REQUEST`, `UNKNOWN_*`, `NO_TECHNICIAN_AVAILABLE`, `IDEMPOTENCY_CONFLICT`, `LATE_CANCELLATION`, `INVALID_CANCELLATION`, `CONFLICT`, **`RATE_LIMIT_EXCEEDED` (429)**, `INTERNAL_ERROR`.

Tests: `GlobalExceptionHandlerTest`, `BookingApiIntegrationTest` (errors, cancel, list/schedule filters).

### C. Booking search and pagination

`BookingQueryService`: two-step id page + `findDetailedByIdIn`. Endpoints: `GET /bookings?phone=`, `GET /technicians/{id}/schedule` with optional `status` (omitted means every status, including cancelled), IST date range, `Pageable` (`startTime` sort).

### D. Logging and request correlation

JSON logs: `logback-spring.xml` + Logstash encoder. `RequestIdFilter`: `X-Request-Id`, MDC `requestId`. Tests: `RequestIdFilterTest`, `RequestIdFilterWebMvcTest`, `ActuatorHealthMockMvcTest` (`/actuator/health`).

### E. Rate limiting

Bucket4j + Redis (`LettuceBasedProxyManager`); `RateLimitFilter` on `/availability`, `/bookings`, `/technicians/{id}/schedule`. Default **20 req/min/IP**; `rate-limit.*` properties. `docker compose up` starts Redis and enables limiting. A host-run app with Redis down at startup does not limit. Tests: `RateLimitFilterTest`, `RateLimitFilterWebMvcTest`.

### F. Stack (implemented)

Java 21, Spring Boot 3.4, MySQL 8.4, Flyway, JPA pessimistic locks, idempotency table, Testcontainers MySQL 8.4 tests.
