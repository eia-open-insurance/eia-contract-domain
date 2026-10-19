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
`terms/requirements`), `security`, `cessions`, `bordereaux`, `commutations`
(propose, approve, execute), `bind-eligibility`, and `bind`. All requests carry
`X-EIA-Tenant`; money and percentages are decimal strings.

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
