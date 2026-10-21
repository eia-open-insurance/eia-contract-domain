package com.eia.contract.domain;

/**
 * Moves one security record to its lifecycle outcome (SPEC 11.3.4 security
 * status enum: {@code RELEASED}, {@code EXPIRED}).
 *
 * <p>Collateral is released when the underlying obligation ends and expires
 * when the instrument lapses; {@code RELEASED} is terminal, while an
 * {@code EXPIRED} record can still be released. The expert validates the
 * transition against the stored row and surfaces an illegal or repeated move as
 * a 409 conflict.</p>
 */
public record UpdateSecurityStatusCommand(String status) {
}
