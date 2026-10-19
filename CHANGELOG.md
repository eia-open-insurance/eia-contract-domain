# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Public APIs for layers, participants, terms, security, cessions, bordereaux,
  commutations (propose, approve, execute), bind eligibility, and bind under
  `/api/v1/contracts/{contractId}/...`.
- Domain command validation for every new resource, including the ASSUMED book
  wall, enumerations, money, percentages, currency, and date rules.
- Expert error translation that preserves the structured `code` and status
  (404, 409, 400, 502).
- `scripts/live-smoke.ps1`, a 41-check live PostgreSQL smoke covering create,
  layers, participants, bind, cession, capacity rejection, commutation, and close.
- 81 JUnit tests.

### Changed

- `PATCH /api/v1/contracts/{id}/status` rejects `BOUND` with
  `409 BIND_REQUIRES_COMMAND`; bind goes through `POST .../bind`.

### Fixed

- Domain test for an illegal transition now uses a non-bind edge so the bind
  guard and the transition table are tested separately.
