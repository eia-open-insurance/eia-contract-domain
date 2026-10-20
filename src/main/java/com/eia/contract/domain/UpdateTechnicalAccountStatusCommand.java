package com.eia.contract.domain;

/**
 * Moves one technical account through its lifecycle. The expert validates the
 * transition and keeps the financial history append-only (SPEC 11.3.4 status
 * enum: {@code DRAFT}, {@code ISSUED}, {@code AGREED}, {@code SETTLED},
 * {@code DISPUTED}, {@code SUPERSEDED}).
 */
public record UpdateTechnicalAccountStatusCommand(String status) {
}
