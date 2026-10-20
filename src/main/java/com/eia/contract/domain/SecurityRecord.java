package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of a contract security record (SPEC 11.3.10 security,
 * SPEC 11.3.4 {@code contract_security}).
 *
 * <p>{@code requiredAmount} is the secured obligation and {@code heldAmount} the
 * collateral actually held; collateral above the obligation is only accepted
 * with an approval reference, which is the over-collateralisation control the
 * expert enforces. Money is a decimal string (SPEC 10.3). The expert owns the
 * currency reconciliation, the effective dates, and the lifecycle status, so
 * the public projection carries only the fields the Security tab needs.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityRecord(
        UUID id,
        String securityType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal requiredAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal heldAmount,
        String currency,
        UUID providerPartyId,
        LocalDate expiryDate,
        String approvalRef,
        String status,
        Instant createdAt
) {
}
