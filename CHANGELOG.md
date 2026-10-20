# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Public APIs for layers, participants, terms, security, cessions, bordereaux
  (list, record, change status), commutations (propose, approve, execute),
  technical accounts (list, produce, change status), bind eligibility, and bind
  under `/api/v1/contracts/{contractId}/...`.
- `GET /api/v1/contracts/search` shell global-search projection with the common
  `query`/`limit`/`items[]` shape, tenant scoping, and the ASSUMED book wall.
- Bordereau proxy forwards the tenant and the ASSUMED book to the expert on
  every call and preserves the expert's `400`/`404`/`409` code and message; a
  supplied non-ASSUMED book is hidden as `404 BOOK_SCOPE_VIOLATION`. The
  projection carries the period, receipt date, currency, premium, loss,
  commission, other charges, line count, notes, lifecycle status, and the
  linked technical account. A bordereau is created `RECEIVED` and can move only
  to `PROCESSED` or `FAILED`.
- Technical-account proxy forwards the tenant and the ASSUMED book to the
  expert on every call and preserves the expert's `400`/`404`/`409` code and
  message; a supplied non-ASSUMED book is hidden as `404 BOOK_SCOPE_VIOLATION`.
- Cash-call API: `GET/POST /api/v1/contracts/{contractId}/cash-calls` and
  `PATCH /api/v1/contracts/{contractId}/cash-calls/{cashCallId}/status`. The
  proxy forwards the tenant and the ASSUMED book on every call and preserves the
  expert's `400`/`404`/`409` code and message; a supplied non-ASSUMED book is
  hidden as `404 BOOK_SCOPE_VIOLATION`. The projection carries the call number,
  contract, period, amount, currency, due date, status, notes, and creation
  time. A call starts `REQUESTED` and the domain accepts only `ISSUED`,
  `SETTLED`, or `CANCELLED` as a status target.
- Security API: `GET/POST /api/v1/contracts/{contractId}/security`. The proxy
  forwards the tenant and the ASSUMED book on every call and preserves the
  expert's `400`/`404`/`409` code and message; a supplied non-ASSUMED book is
  hidden as `404 BOOK_SCOPE_VIOLATION`. The `SecurityRecord` projection carries
  the id, security type, required and held amounts, currency, provider party,
  expiry date, approval reference, status, and creation time.
- Domain command validation for every new resource, including the ASSUMED book
  wall, enumerations, money, percentages, currency, and date rules.
- Expert error translation that preserves the structured `code` and status
  (404, 409, 400, 502).
- `scripts/live-smoke.ps1`, a 41-check live PostgreSQL smoke covering create,
  layers, participants, bind, cession, capacity rejection, commutation, and close.
- 218 JUnit tests.

### Changed

- `PATCH /api/v1/contracts/{id}/status` rejects `BOUND` with
  `409 BIND_REQUIRES_COMMAND`; bind goes through `POST .../bind`.

### Fixed

- Domain test for an illegal transition now uses a non-bind edge so the bind
  guard and the transition table are tested separately.
