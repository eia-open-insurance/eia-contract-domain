package com.eia.contract.domain;

/**
 * Moves one bordereau through its lifecycle. The expert validates the
 * transition and keeps the accounting history append-only (SPEC 11.3.4 status
 * enum: {@code RECEIVED}, {@code PARSING}, {@code VALIDATED},
 * {@code PARTIALLY_POSTED}, {@code POSTED}, {@code REJECTED},
 * {@code SUPERSEDED}). {@code notes} is an optional amendment note.
 */
public record UpdateBordereauStatusCommand(String status, String notes) {
}
