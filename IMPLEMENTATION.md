# Implementation state

The domain service owns the public Contract API and the business decisions that
do not need aggregate state. It validates every command, forwards it to the
expert, and translates the expert's structured errors back into the public error
codes.

## Implemented public API

All paths are under `/api/v1/contracts/{contractId}` and require the
`X-EIA-Tenant` header (SPEC 11.3.10 shape).

| Method | Path | Purpose |
|---|---|---|
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
- Expert error translation: 404 -> `NOT_FOUND`, 409 -> the expert's code (409),
  400 -> the expert's code (400), 5xx/unreachable -> 502 `EXPERT_UNAVAILABLE`.
- The aggregate invariants themselves (share totals, tower overlap, capacity,
  approval evidence) live in the expert because they need the stored aggregate.

## Tests

- `ContractControllerTest` (existing behaviour plus the bind-command guard),
  `ContractDetailControllerTest` (every new endpoint, success and failure),
  `ContractCommandRulesTest` (validation rules), `ContractStatusTest` — 81 tests,
  run with `./gradlew test`.

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
endorse, renew, technical accounts, reinstatement application, loss-aggregate,
documents, lineage, mass-tort profile, authority limits, calculations and test
vectors (SPEC 7.19), Schedule F, or reinsurance tax. Events are not published.
The public error body remains the vertical `ApiError` rather than the canonical
Problem envelope (MISMATCHES M-02), and pagination/ETag/idempotency follow the
existing MISMATCHES entries.
