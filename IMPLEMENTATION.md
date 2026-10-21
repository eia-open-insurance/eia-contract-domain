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
| PATCH | `/security/{securityId}/status` | Release or expire a security (`RELEASED`, `EXPIRED`) |
| GET/POST | `/cessions` | Cessions |
| GET/POST | `/bordereaux` | Bordereaux / record a received bordereau |
| PATCH | `/bordereaux/{bordereauId}/status` | Process or fail a bordereau (`PROCESSED`, `FAILED`) |
| GET/POST | `/technical-accounts` | Technical accounts / produce an account |
| GET/POST | `/technical-accounts/{accountId}/lines` | Line summary / record a line |
| DELETE | `/technical-accounts/{accountId}/lines/{lineId}` | Delete a line |
| PATCH | `/technical-accounts/{accountId}/status` | Change the account lifecycle status |
| GET/POST | `/cash-calls` | Cash calls / request an interim cash call |
| PATCH | `/cash-calls/{cashCallId}/status` | Issue, settle, or cancel a cash call |
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
- Technical-account, bordereau, cash-call, and security calls forward the tenant
  and the ASSUMED book on every expert call. A supplied `X-EIA-Book` other than
  ASSUMED is hidden as 404 `BOOK_SCOPE_VIOLATION`; an absent header is accepted
  for compatibility with the other contract proxy routes.
- Cash-call calls follow the same book wall and forward the tenant and ASSUMED
  book. A cash call records `periodStart`, `periodEnd`, `amount`, `currency`,
  `dueDate`, and optional `notes`; the amount must be strictly positive, the
  period ordered, the currency an ISO code, and the due date present. The expert
  assigns the call number and starts the call `REQUESTED`; the domain accepts
  only `ISSUED`, `SETTLED`, or `CANCELLED` as a status target. The client uses
  the expert's `/internal/v1/contracts/{contractId}/cash-calls` API, which maps
  to the same controller as the public cash-call path.
- Technical-account line calls use the same book wall and forward the tenant and
  ASSUMED book to the expert's
  `/internal/v1/contracts/{contractId}/technical-accounts/{accountId}/lines`
  API. A line records one SPEC line type (`PREMIUM`, `COMMISSION`, `BROKERAGE`,
  `PAID_LOSS`, `OUTSTANDING_LOSS`, `IBNR`, `ALAE`, `REINSTATEMENT`, `CASH_CALL`,
  `FUNDS_WITHHELD`, `TAX`, `ADJUSTMENT`, or `OTHER`), optional `reference` (64
  characters) and `description` (300 characters), and a strictly positive
  `amount`. The retired aggregate `LOSS` type is rejected on create because the
  expert migrated stored rows to `PAID_LOSS`. `GET .../lines` returns the
  `TechnicalAccountLine` rows plus `totals` grouped into premium, loss,
  commission, and other, with the optional detailed per-type map; the domain
  derives any total or detail the expert omits. The bucket mapping matches the
  expert: `PREMIUM` is premium; `PAID_LOSS`, `OUTSTANDING_LOSS`, `IBNR`, `ALAE`,
  `REINSTATEMENT`, and `CASH_CALL` are loss; `COMMISSION` and `BROKERAGE` are
  commission; `FUNDS_WITHHELD`, `TAX`, `ADJUSTMENT`, and `OTHER` are other.
  `DELETE .../lines/{lineId}` returns 204. The DRAFT-only write guard, the line
  ordering, and the reconciliation against the account stay in the expert and
  surface as 409.
- Security calls use the same book wall and forward the tenant and ASSUMED book
  to the expert's `/internal/v1/contracts/{contractId}/security` API. A security
  record carries `securityType`, `requiredAmount`, `heldAmount`, `currency`,
  `providerPartyId`, `expiryDate`, and `approvalRef`; the `SecurityRecord`
  projection returns the id, those fields, `status`, and `createdAt`. The type is
  an enumeration, the amounts are non-negative, the currency is an ISO code, and
  the approval reference is at most 64 characters. Currency reconciliation, the
  over-collateralisation approval, the effective dates, and the initial status
  stay in the expert and surface as 409. The status action accepts only
  `RELEASED` or `EXPIRED`; it uses the expert's public
  `/api/v1/contracts/{contractId}/security/{securityId}/status` action, which
  owns the stored transition and returns 409 for an illegal or repeated move.
- The period-bordereau client uses the expert's public
  `/api/v1/contracts/{contractId}/bordereaux` API, where the legacy internal
  prefix remains reserved for the ingest-header slice. A bordereau is created
  `RECEIVED`; the domain accepts only `PROCESSED` or `FAILED` as a status target.
- Expert error translation: 404 -> `NOT_FOUND`, 409 -> the expert's code (409),
  400 -> the expert's code (400), 5xx/unreachable -> 502 `EXPERT_UNAVAILABLE`.
- The aggregate invariants themselves (share totals, tower overlap, capacity,
  approval evidence) live in the expert because they need the stored aggregate.

## Tests

- `ContractControllerTest` (existing behaviour, the bind-command guard, and the
  search projection), `ContractDetailControllerTest` (every new endpoint,
  success and failure), `BordereauControllerTest` (bordereau proxy, book wall,
  and command validation), `BordereauClientTest` (tenant and ASSUMED forwarding
  plus expert 400/404/409 preservation with `MockRestServiceServer`),
  `TechnicalAccountControllerTest` (technical-account proxy and book wall),
  `TechnicalAccountClientTest` (tenant and ASSUMED forwarding plus expert
  400/404/409 preservation with `MockRestServiceServer`),
  `TechnicalAccountLineControllerTest` (line summary, every accepted line type,
  create, delete, book wall, and line validation), `TechnicalAccountLineClientTest`
  (tenant and ASSUMED forwarding, totals and detailed-map normalization, and
  expert 400/404/409 preservation with `MockRestServiceServer`),
  `LineSummaryTest` (bucket defaults, the optional detailed map, and its decimal
  string serialization),
  `CashCallControllerTest` (cash-call proxy and book wall),
  `CashCallClientTest` (tenant and ASSUMED forwarding plus expert 400/404/409
  preservation with `MockRestServiceServer`),
  `SecurityControllerTest` (security proxy, book wall, status action, and command
  validation), `SecurityClientTest` (tenant and ASSUMED forwarding plus expert
  400/404/409 preservation with `MockRestServiceServer`, including the status
  action),
  `ContractCommandRulesTest` (validation rules), `ContractStatusTest` — run with
  `./gradlew test`.

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
