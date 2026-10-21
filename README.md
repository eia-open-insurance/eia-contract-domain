# eia-contract-domain

- GitHub organization: `eia-open-insurance`
- Repository URL: https://github.com/eia-open-insurance/eia-contract-domain
- Type: Spring Boot service
- Deployable: Yes
- Owner: Contract
- Runtime: Java 22 and Spring Boot
- Data store: eia_contract (tx schema)
- Depends on: eia-contract-expert, eia-contracts, eia-platform/libs

## Purpose

Contract business rules, transaction catalog, orchestration, approvals, and APIs. Reinsurance contract administration, layers, participations, accounts, and bordereaux.

## Implemented slice

The public API exposes the contract header and its children under
`/api/v1/contracts/{contractId}`: `layers`, `participants`, `terms` (plus
`terms/requirements`), `security` (list, record, release/expire), `cessions`,
`bordereaux` (list, record, change status), `commutations` (propose, approve,
execute), `technical-accounts` (list, produce, change status, and its lines),
`cash-calls` (list, request, change status), `bind-eligibility`, and `bind`.
All requests carry `X-EIA-Tenant`; money and percentages are decimal strings.

Bordereau, technical-account, cash-call, and security calls also forward the
tenant and the ASSUMED book to the expert (`X-EIA-Tenant` and
`X-EIA-Book: ASSUMED`); a supplied `X-EIA-Book` other than ASSUMED is hidden as
404 `BOOK_SCOPE_VIOLATION`. A security record carries `securityType`,
`requiredAmount`, `heldAmount`, `currency`, `providerPartyId`, `expiryDate`, and
`approvalRef`; the projection returns the id, those fields, `status`, and
`createdAt`. The expert derives the initial status; the status action then moves
the record to `RELEASED` or `EXPIRED` (`RELEASED` is terminal). A bordereau
records `periodStart`, `periodEnd`, `receivedDate`, `currency`, `premiumAmount`,
`lossAmount`, `commissionAmount`, `otherChargesAmount`, `lineCount`, and
optional `notes`; it starts `RECEIVED` and its status then moves only to
`PROCESSED` or `FAILED`. A cash call records `periodStart`, `periodEnd`,
`amount`, `currency`, `dueDate`, and optional `notes`; the expert assigns the
call number, the call starts `REQUESTED`, and the status then moves only to
`ISSUED`, `SETTLED`, or `CANCELLED` (with `REQUESTED` rejected as a target).
A technical-account line records one header component (`lineType`: `PREMIUM`,
`LOSS`, `COMMISSION`, or `OTHER`), optional `reference` and `description`, and
a positive `amount`; `GET .../lines` returns the lines plus `totals` for
premium, loss, commission, and other, and `DELETE .../lines/{lineId}` removes a
line. The expert owns the DRAFT-only line write guard, the line ordering, and
the reconciliation against the account.

`GET /api/v1/contracts/search?q=<term>&limit=10` returns the shared shell search
projection (`query`, `limit`, `items[]` with `id`, `type`, `label`, `secondary`,
`status`, `href`). It matches contract number or contract type, is tenant-scoped,
and enforces the ASSUMED book wall.

The domain validates every command, enforces the ASSUMED book wall, forwards to
the expert, and preserves the expert's structured error codes for 400, 404, and
409 responses. Binding requires the preconditions and an approver, so
`PATCH /api/v1/contracts/{id}/status` rejects `BOUND` with
`409 BIND_REQUIRES_COMMAND`.

`scripts/live-smoke.ps1` runs the full slice against a real PostgreSQL database
(create -> layers -> participants -> bind -> cession -> commutation -> close).
`IMPLEMENTATION.md` records the surface, tests, and remaining gaps.

## Responsibilities

- Keep this repository focused on one deployable component or one platform concern.
- Follow the repository standard in `../CONVENTIONS.md` and the specification in `../../SPEC.md`.
- Publish and consume contracts through `eia-contracts`.
- Maintain README, CODEOWNERS, SECURITY, CHANGELOG, tests, and local verification.
- Contain no business logic in shared libraries or front-end remotes.

## Verification

- Lint and format
- Unit tests with 100% business-logic coverage where applicable
- Contract tests
- Integration or Playwright tests where applicable
- Security scan and SBOM
- Build and publish

## Notes

Owns the business logic for this vertical.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
