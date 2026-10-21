# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Public APIs for layers, participants, terms, security, cessions, bordereaux
  (list, record, change status), commutations (propose, approve, execute),
  technical accounts (list, produce, change status, add and delete lines), bind
  eligibility, and bind under `/api/v1/contracts/{contractId}/...`.
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
- Technical-account line API:
  `GET/POST /api/v1/contracts/{contractId}/technical-accounts/{accountId}/lines`
  and `DELETE .../lines/{lineId}`. The proxy forwards the tenant and the ASSUMED
  book on every call and preserves the expert's `400`/`404`/`409` code and
  message. `GET` returns the `TechnicalAccountLine` rows plus `totals` grouped
  into premium, loss, commission, and other, with an optional detailed per-type
  map; the domain derives any missing total. `POST` records one line (`lineType`
  is `PREMIUM`, `COMMISSION`, `BROKERAGE`, `PAID_LOSS`, `OUTSTANDING_LOSS`,
  `IBNR`, `ALAE`, `REINSTATEMENT`, `CASH_CALL`, `FUNDS_WITHHELD`, `TAX`,
  `ADJUSTMENT`, or `OTHER`; the retired `LOSS` type is rejected, plus optional
  `reference` and `description`) with a strictly positive `amount`, and
  `DELETE` returns 204.
- Cash-call API: `GET/POST /api/v1/contracts/{contractId}/cash-calls` and
  `PATCH /api/v1/contracts/{contractId}/cash-calls/{cashCallId}/status`. The
  proxy forwards the tenant and the ASSUMED book on every call and preserves the
  expert's `400`/`404`/`409` code and message; a supplied non-ASSUMED book is
  hidden as `404 BOOK_SCOPE_VIOLATION`. The projection carries the call number,
  contract, period, amount, currency, due date, status, notes, and creation
  time. A call starts `REQUESTED` and the domain accepts only `ISSUED`,
  `SETTLED`, or `CANCELLED` as a status target.
- Security API: `GET/POST /api/v1/contracts/{contractId}/security` and
  `PATCH /api/v1/contracts/{contractId}/security/{securityId}/status`. The proxy
  forwards the tenant and the ASSUMED book on every call and preserves the
  expert's `400`/`404`/`409` code and message; a supplied non-ASSUMED book is
  hidden as `404 BOOK_SCOPE_VIOLATION`. The `SecurityRecord` projection carries
  the id, security type, required and held amounts, currency, provider party,
  expiry date, approval reference, status, and creation time. The status action
  accepts only `RELEASED` or `EXPIRED`; it forwards to the expert's public
  security lifecycle endpoint, which owns the stored transition.
- Domain command validation for every new resource, including the ASSUMED book
  wall, enumerations, money, percentages, currency, and date rules.
- Expert error translation that preserves the structured `code` and status
  (404, 409, 400, 502).
- `scripts/live-smoke.ps1`, a 41-check live PostgreSQL smoke covering create,
  layers, participants, bind, cession, capacity rejection, commutation, and close.
- 293 JUnit tests.

### Changed

- Technical-account lines accept the SPEC 11.3.4 detail vocabulary
  (`PREMIUM`, `COMMISSION`, `BROKERAGE`, `PAID_LOSS`, `OUTSTANDING_LOSS`,
  `IBNR`, `ALAE`, `REINSTATEMENT`, `CASH_CALL`, `FUNDS_WITHHELD`, `TAX`,
  `ADJUSTMENT`, and `OTHER`). The retired `LOSS` aggregate is rejected on create
  and the expert migrates stored rows to `PAID_LOSS`; a legacy `LOSS` total is
  still read into the loss bucket. The line summary keeps the four statement
  buckets and adds the optional detailed per-type totals map. `BROKERAGE` rolls
  into commission; `REINSTATEMENT` and `CASH_CALL` roll into loss; funds
  withheld, tax, adjustment, and other roll into other.
- `PATCH /api/v1/contracts/{id}/status` rejects `BOUND` with
  `409 BIND_REQUIRES_COMMAND`; bind goes through `POST .../bind`.

### Fixed

- Domain test for an illegal transition now uses a non-bind edge so the bind
  guard and the transition table are tested separately.
