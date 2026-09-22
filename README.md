# Food Delivery Order Management

A Spring Boot REST API for a multi-restaurant, multi-city food delivery
platform: menu management, order placement with concurrency-safe stock
handling, delivery-partner assignment under contention, async status
notifications, and post-delivery ratings.

Built for the DMG Companies Java Developer take-home assignment.

## Stack

- **Java 25, Spring Boot 3.3.4** (Web, Data JPA, Security, Validation)
- **H2** (in-memory) for `dev`/`test` profiles, **PostgreSQL** wired for `prod`
- **JJWT** for stateless JWT auth
- **JUnit 5 + AssertJ + Spring Boot Test** for unit/integration tests,
  including two dedicated concurrency tests using real multi-threaded
  `ExecutorService` races against the actual (H2) database.

## Running it

```bash
mvn spring-boot:run
# API on http://localhost:8080, H2 console on /h2-console (dev profile only)
```

```bash
mvn test
```

> **Note on this submission's provenance:** this was built with AI
> assistance in an environment with no Maven/network access, so the code
> was hand-written against known Spring Boot 3.3 / Hibernate 6 APIs
> rather than compiled iteratively. Run `mvn clean verify` before
> relying on it and treat any compile error as a (hopefully small)
> integration bug to fix, not a design problem. See `CLAUDE.md` for the
> full AI workflow.

## Scope & assumptions

The PRD is intentionally open-ended; here's what was built and why.

### Roles & RBAC
Four roles as specified: `ADMIN`, `RESTAURANT_OWNER`, `CUSTOMER`,
`DELIVERY_PARTNER`. Enforced at two layers:
1. **URL-level** in `SecurityConfig` (coarse: "only restaurant owners can
   hit `POST /menu-items`").
2. **Service-level** (fine: "only *this* order's *own* restaurant owner
   can accept/reject *this* order"; "only the delivery partner already
   assigned to this order can mark it out-for-delivery").

Auth is JWT-based (`POST /api/auth/register`, `POST /api/auth/login`);
`register` lets the caller pick their own role, since there's no
external identity provider in scope - in a real system, elevated roles
(`ADMIN`, `RESTAURANT_OWNER`) would be provisioned differently, not
self-service.

### The two concurrency requirements, specifically

**"Concurrent orders for the same menu item should not oversell limited
stock."**
`MenuItemRepository.findByIdForUpdate` takes a `PESSIMISTIC_WRITE` row
lock (`SELECT ... FOR UPDATE`) inside `OrderService.placeOrder`'s
`@Transactional` boundary. Two transactions racing to buy the last unit
of the same item are serialized at the DB row: the first to acquire the
lock reads-checks-decrements-commits; the second blocks until the first
commits, then sees the updated (possibly now-zero) stock and fails
cleanly with a 409 if there isn't enough left. When an order touches
multiple menu items, they're locked in ascending `id` order to avoid
deadlocking against another order that references the same two items in
the opposite order.

**"Partner assignment should handle multiple partners contending for the
same order."**
Same pattern: `DeliveryService.acceptOrder` takes a `PESSIMISTIC_WRITE`
lock on the `Order` row (so only one of several concurrent "accept"
calls can see `deliveryPartner == null` and win) and on the
`DeliveryPartner` row (so a partner can't win two different races at
once). Losers get a `409 Conflict` ("already claimed by another
partner"), not a silent overwrite.

Both are covered by dedicated multi-threaded integration tests
(`OrderConcurrencyIntegrationTest`, `DeliveryAssignmentConcurrencyIntegrationTest`)
that actually spin up N threads against a live H2 instance and assert
on the final row state, not just on "no exception thrown."

**Design choice, stated explicitly:** pessimistic locking over optimistic
locking + retry. Optimistic locking (`@Version`, used elsewhere in this
project for ordinary field edits) would give better throughput under
low contention but pushes retry logic onto every caller and makes "no
oversell" a property you have to prove statistically. Pessimistic
locking makes it a property you can prove by inspection and test
directly - the right tradeoff for a correctness-graded assignment
scoped explicitly *away* from "distributed systems" and "production
observability."

### Order placement atomicity
"Order placement must atomically reflect item stock, order state, and
payment." All three happen inside one `@Transactional` method
(`OrderService.placeOrder`): stock decrement, `Order`/`OrderItem`
insert, and the payment stub's result. Any failure (insufficient stock,
payment failure, a menu item not belonging to the requested restaurant)
throws, and Spring rolls back the entire transaction - no order is ever
persisted with a stock decrement that didn't "happen," and no stock is
ever decremented for an order that doesn't exist.

### Async status fan-out
"Status updates should fan out asynchronously... without blocking the
calling flow." `OrderService.updateStatus` publishes a Spring
application event after saving the new status; `NotificationService`
listens with `@TransactionalEventListener(phase = AFTER_COMMIT)` +
`@Async("notificationExecutor")`, so notification "delivery" (logged,
see below) happens on a separate thread pool *after* the status-update
transaction has actually committed - the HTTP caller gets their
response immediately, and nothing gets notified about a change that
later rolled back.

**Assumption:** actual delivery to customer/restaurant/partner (email,
push, SMS) is out of scope for a take-home without a provider
configured, so `NotificationService` logs each fan-out target instead.
The async, non-blocking, commit-gated *mechanism* is real; the delivery
*channel* is a stub.

### Order lifecycle
`PLACED → ACCEPTED → PREPARING → OUT_FOR_DELIVERY → DELIVERED`, with
`PLACED → REJECTED` and `{PLACED, ACCEPTED, PREPARING} → CANCELLED` as
the only other legal edges (`OrderStatus.canTransitionTo`). Cancelling
or rejecting an order releases its reserved stock back to the relevant
menu items (also inside a locked, transactional path). Delivering an
order frees up the delivery partner (`BUSY → AVAILABLE`) automatically.

### Ratings
One rating per order (`unique` constraint + explicit `existsByOrderId`
check), only by that order's own customer, only after `DELIVERED`.

### What's deliberately NOT built (per "Out of Scope")
- No UI.
- No Docker/CI/CD.
- No microservices - everything is one deployable module with layered
  packages (`model` / `repository` / `service` / `controller` /
  `security`), which is the right granularity for "avoid distributed
  systems" while still being organized.
- No OAuth/SSO/MFA - plain email+password with BCrypt, JWT issuance.
- No production observability (metrics/tracing/alerting) beyond
  Spring's default `/actuator/health`.

### Smaller assumptions
- Prices are snapshotted onto `OrderItem.priceAtOrder` at order time, so
  a later menu price change never rewrites history.
- A menu item's `available` flag is a manual owner toggle independent
  of `stockQuantity` (an owner might 86 an item with stock still on
  hand, or vice versa temporarily).
- Delivery partners are scoped to a single city (`DeliveryPartner.city`)
  and only see/accept orders from restaurants in that city.
- Multi-restaurant orders are **not** supported - one order belongs to
  exactly one restaurant, matching "customer order placement" being
  described per-restaurant in the PRD and keeping the stock/payment
  atomicity story simple.

## API summary

| Method | Path | Role |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| POST | `/api/admin/cities` | ADMIN |
| GET | `/api/cities` | public |
| POST | `/api/restaurants` | ADMIN, RESTAURANT_OWNER |
| GET | `/api/restaurants[?cityId=]` | public |
| GET | `/api/restaurants/{id}` | public |
| GET | `/api/restaurants/{id}/orders` | owner or ADMIN |
| GET | `/api/restaurants/{id}/ratings` | public |
| POST | `/api/restaurants/{id}/menu-items` | RESTAURANT_OWNER |
| GET | `/api/restaurants/{id}/menu-items` | public |
| PUT | `/api/menu-items/{id}` | RESTAURANT_OWNER |
| DELETE | `/api/menu-items/{id}` | RESTAURANT_OWNER |
| POST | `/api/orders` | CUSTOMER |
| GET | `/api/orders/mine` | CUSTOMER |
| GET | `/api/orders/{id}` | participants + ADMIN |
| PATCH | `/api/orders/{id}/status` | role- & ownership-checked per transition |
| POST | `/api/orders/{id}/rating` | CUSTOMER (own, delivered order) |
| POST | `/api/delivery/partners` | DELIVERY_PARTNER |
| GET | `/api/delivery/orders/assignable` | DELIVERY_PARTNER |
| POST | `/api/delivery/orders/{id}/accept` | DELIVERY_PARTNER |

All error responses share one JSON shape (`GlobalExceptionHandler` →
`ErrorResponse`): `timestamp`, `status`, `error`, `message`, optional
`details` (field-level validation errors).

## Testing approach

- **Unit**: `OrderStatusTransitionTest` - the status state machine in
  isolation, no Spring context.
- **Integration** (`@SpringBootTest`, real H2, real transactions):
  - `OrderConcurrencyIntegrationTest` - 30 threads race to buy 1 unit
    each of a 10-unit-stock item; asserts exactly 10 succeed, 20 fail
    with a stock conflict, and final stock is exactly 0 (no lost
    updates, no oversell).
  - `DeliveryAssignmentConcurrencyIntegrationTest` - 20 delivery
    partners race to accept the same order; asserts exactly 1 wins, 19
    get a conflict, and exactly 1 partner ends up `BUSY`.
  - `OrderLifecycleIntegrationTest` - full happy path
    (place → accept → prepare → assign → deliver → rate), stock release
    on cancellation, and RBAC rejection of an out-of-role status
    transition.

Concurrency tests use a `TestAuth` helper that sets
`SecurityContextHolder`'s authentication per-thread (it defaults to
`ThreadLocal`), since each simulated actor needs their own identity
inside the executor pool.
