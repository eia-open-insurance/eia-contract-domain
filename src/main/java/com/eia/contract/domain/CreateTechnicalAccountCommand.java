package com.eia.contract.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Produces a technical account for one period (SPEC 11.3.9
 * {@code PRODUCE_TECHNICAL_ACCOUNT}).
 *
 * <p>The tenant is never in the body (it comes from {@code X-EIA-Tenant}) and the
 * contract id is never in the body (it is the path). The domain validates the
 * request shape; the expert owns the period-overlap, contract-window, currency,
 * and balance invariants (CTR-07, CTR-08, CTR-17).</p>
 */
public record CreateTechnicalAccountCommand(
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        BigDecimal cededPremium,
        BigDecimal cededLosses,
        BigDecimal commission,
        BigDecimal otherCharges
) {
}
