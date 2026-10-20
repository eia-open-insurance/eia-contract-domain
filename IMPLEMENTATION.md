# Implementation state

The domain service owns the public Contract API and the business decisions that
do not need aggregate state. It validates every command, forwards it to the
expert, and translates the expert's structured errors back into the public error
codes.

## Implemented public API

All child paths are under `/api/v1/contracts/{contractId}` and require the
`X-EIA-Tenant` header (SPEC 11.3.10 shape). The header paths
`/api/v1/contracts`, `/api/v1/contracts/{id}`, and `/api/v1/contracts/search`
require it as well.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/contracts/search` | Shell global search (ASSUMED book) |
| GET | `/layers` | Layer tower |
| GET | `/layers/summary` | Tower size, capacity, documented gaps |
| POST | `/layers` | Record a layer |
| GET/POST | `/participants` | Participations |
| GET/POST | `/terms` | Contract terms |
| GET | `/terms/requirements` | Required terms for the type and the missing ones |
| GET/POST | `/security` | Security and collateral |
| GET/POST | `/cessions` | Cessions |
| GET/POST | `/bordereaux` | Bordereaux |
| GET | `/bordereaux/{bordereauId}` | One bordereau |
| GET/POST | `/technical-accounts` | Technical accounts / produce an account |
| PATCH | `/technical-accounts/{accountId}/status` | Change the account lifecycle status |
| GET/POST | `/commutations` | Commutations |
| POST | `/commutations/{commutationId}/approve` | Record approval |
| POST | `/commutations/{commutationId}/execute` | Execute, release security, close |
| GET | `/bind-eligibility` | Bind preconditions |
| POST | `/bind` | Bind with approval |

The existing `/api/v1/contracts`, `/api/v1/contracts/{id}`, and
`/api/v1/contracts/{id}/status` endpoints are unchanged, except that a `BOUND`
transition through `PATCH .../status` is now rejected with
`409 BIND_REQUIRES_COMMAND`; bind must go through `POST .../bind` so the
preconditions and approval are enforced.

## Domain responsibilities

- Command shape validation: required fields, enumerations, non-negative money,
  percentages in 0..100, ISO currency format, date ordering, and the ASSUMED book
  wall (`sourceVertical` must be `ASSUMED`). Errors are 400 with a specific code.
- Technical-account calls forward the tenant and the ASSUMED book on every
  expert call. A supplied `X-EIA-Book` other than ASSUMED is hidden as 404
  `BOOK_SCOPE_VIOLATION`; an absent header is accepted for compatibility with
  the other contract proxy routes.
- Expert error translation: 404 -> `NOT_FOUND`, 409 -> the expert's code (409),
  400 -> the expert's code (400), 5xx/unreachable -> 502 `EXPERT_UNAVAILABLE`.
- The aggregate invariants themselves (share totals, tower overlap, capacity,
  approval evidence) live in the expert because they need the stored aggregate.

## Tests

- `ContractControllerTest` (existing behaviour, the bind-command guard, and the
  search projection), `ContractDetailControllerTest` (every new endpoint,
  success and failure), `TechnicalAccountControllerTest` (technical-account
  proxy and book wall), `TechnicalAccountClientTest` (tenant and ASSUMED
  forwarding plus expert 400/404/409 preservation with
  `MockRestServiceServer`), `ContractCommandRulesTest` (validation rules),
  `ContractStatusTest` — run with `./gradlew test`.

## Live smoke

`scripts/live-smoke.ps1` runs the full slice against a real PostgreSQL database
and the two running services:

```
create -> quote -> place -> layers -> participants -> terms -> security
       -> bind -> cession -> (capacity rejection) -> in force
       -> commutation -> approval -> execution -> CLOSED
```

It asserts 41 checks. See IMPLEMENTATION.md in `eia-contract-expert` for the
schema and the invariant mapping.

## Remaining gaps

The domain does not yet implement: draft update (`PATCH /contracts/{id}`),
endorse, renew, reinstatement application, loss-aggregate,
documents, lineage, mass-tort profile, authority limits, calculations and test
vectors (SPEC 7.19), Schedule F, or reinsurance tax. Events are not published.
The public error body remains the vertical `ApiError` rather than the canonical
Problem envelope (MISMATCHES M-02), and pagination/ETag/idempotency follow the
existing MISMATCHES entries.
