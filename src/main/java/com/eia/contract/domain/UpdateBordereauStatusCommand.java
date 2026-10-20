package com.eia.contract.domain;

/**
 * Moves one period bordereau to its terminal outcome. A bordereau is created
 * {@code RECEIVED}; processing either posts it to a technical account
 * ({@code PROCESSED}) or records the failure ({@code FAILED}). Both terminal
 * states are immutable, so only those two values can be requested. {@code notes}
 * is an optional amendment note.
 */
public record UpdateBordereauStatusCommand(String status, String notes) {
}
