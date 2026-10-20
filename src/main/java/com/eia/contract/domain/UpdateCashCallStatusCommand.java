package com.eia.contract.domain;

/**
 * Moves one cash call through its lifecycle. A cash call is created
 * {@code REQUESTED}; issuance and cancellation are the alternatives at that
 * point and settlement follows issuance. {@code REQUESTED} is therefore not a
 * valid status target, and both terminal statuses are immutable.
 */
public record UpdateCashCallStatusCommand(String status) {
}
