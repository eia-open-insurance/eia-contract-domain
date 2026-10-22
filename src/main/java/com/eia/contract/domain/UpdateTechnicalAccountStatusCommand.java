package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Moves one technical account through its lifecycle. The expert validates the
 * transition and keeps the financial history append-only (SPEC 11.3.4 status
 * enum: {@code DRAFT}, {@code ISSUED}, {@code AGREED}, {@code SETTLED},
 * {@code DISPUTED}, {@code SUPERSEDED}).
 *
 * <p>{@code reference} optionally records the settlement reference. It is
 * meaningful only on the {@code SETTLED} transition; the client omits it from
 * every other status payload.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateTechnicalAccountStatusCommand(String status, String reference) {

    /** A transition without a settlement reference. */
    public UpdateTechnicalAccountStatusCommand(String status) {
        this(status, null);
    }
}
