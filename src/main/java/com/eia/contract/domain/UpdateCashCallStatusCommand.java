package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Moves one cash call through its lifecycle. A cash call is created
 * {@code REQUESTED}; issuance and cancellation are the alternatives at that
 * point and settlement follows issuance. {@code REQUESTED} is therefore not a
 * valid status target, and both terminal statuses are immutable.
 *
 * <p>{@code reference} optionally records the settlement reference. It is
 * meaningful only on the {@code SETTLED} transition; the client omits it from
 * every other status payload.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateCashCallStatusCommand(String status, String reference) {

    /** A transition without a settlement reference. */
    public UpdateCashCallStatusCommand(String status) {
        this(status, null);
    }
}
