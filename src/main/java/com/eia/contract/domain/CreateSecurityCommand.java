package com.eia.contract.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records collateral held against a contract's obligations (SPEC 11.3.10
 * security).
 *
 * <p>The tenant is never in the body (it comes from {@code X-EIA-Tenant}), the
 * contract id is never in the body (it is the path), and the book is always
 * ASSUMED. The domain validates the request shape; the expert owns the currency
 * reconciliation, the over-collateralisation approval, the effective dates, and
 * the initial status.</p>
 */
public record CreateSecurityCommand(
        String securityType,
        BigDecimal requiredAmount,
        BigDecimal heldAmount,
        String currency,
        UUID providerPartyId,
        UUID beneficiaryPartyId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate expiryDate,
        String status,
        String collateralQuality,
        UUID documentId,
        String releaseConditions,
        String approvalRef,
        Instant approvedAt
) {
}
