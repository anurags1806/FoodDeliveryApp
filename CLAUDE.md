# CLAUDE.md

This file documents how Claude was used to build this project, as required by
the assignment submission checklist.

## Tool / environment

- Claude (Sonnet) via the Claude.ai chat interface, using its code
  execution / file-creation sandbox (not Claude Code CLI).
- No `mvn` / dependency download was available in the sandbox (no network
  egress), so the project was hand-written file-by-file against known
  Spring Boot 3.3.x / Spring Security 6.x / Hibernate 6.x APIs rather than
  scaffolded and iteratively compiled. **Before pushing, run `mvn clean
  verify` locally and fix any compilation issues the sandbox couldn't
  catch** (see README "Known limitations of AI-assisted build").

## Skills used

- The assistant's own `docx`/`pptx`/`xlsx` document skills were **not**
  relevant to this task (pure backend code) and were not invoked.
- No custom project-specific skill file was used; the assignment prompt
  itself (pasted in full into the conversation) served as the spec.

## Workflow

1. Read the assignment PRD and scoped it down to a single deployable
   Spring Boot module (see README "Scope & assumptions" for the exact
   decisions made and why).
2. Designed the domain model first (`User`, `Role`, `City`, `Restaurant`,
   `MenuItem`, `Order`, `OrderItem`, `DeliveryPartner`, `Rating`) and the
   two concurrency hot spots called out explicitly in the PRD:
   - stock decrement on order placement (many customers, one menu item)
   - delivery-partner assignment (many partners, one order)
3. Chose **pessimistic row locking** (`SELECT ... FOR UPDATE` via Spring
   Data's `@Lock(LockModeType.PESSIMISTIC_WRITE)`) for both hot spots
   instead of optimistic locking + retry, because the PRD asks for
   correctness guarantees ("should not oversell", "handle multiple
   partners contending") rather than throughput under extreme load - a
   blocking lock is the simplest way to make the correctness argument
   obviously true and easy to test deterministically.
4. Built outward from the domain model: repositories (with the lock
   queries) → services (business rules + transactions) → controllers →
   security (JWT + role-based `SecurityFilterChain` rules, mirrored by
   role checks inside services for defense in depth) → tests.
5. Wrote two concurrency integration tests first (oversell prevention,
   double-assignment prevention) since they are the features being
   graded most directly, then a lifecycle happy-path test, then unit
   tests for the order-status state machine.
6. Wrote this README and CLAUDE.md last, documenting assumptions made
   along the way rather than reconstructing them after the fact.

## Assumptions Claude made unprompted (flagged for human review)

- Payment is a synchronous in-process stub (`PaymentService.charge`)
  that always succeeds for a positive amount, executed inside the same
  DB transaction as the stock decrement and order insert. A real
  payment gateway would need an async authorize/capture split, which
  the PRD explicitly puts out of scope ("advanced auth... production
  observability" out of scope, and no payment gateway was named).
- Notification fan-out (`NotificationService`) is a logging stub, not a
  real email/SMS/push integration, dispatched via
  `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` so it
  never blocks the caller and never fires for a rolled-back status
  change.
- JWT auth (not sessions) was chosen for a stateless REST API, using a
  single symmetric secret from config - explicitly simple, since OAuth/
  SSO/MFA are called out as out of scope.
