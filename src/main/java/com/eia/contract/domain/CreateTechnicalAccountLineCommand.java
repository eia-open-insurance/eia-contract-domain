package com.eia.contract.domain;

import java.math.BigDecimal;

/**
 * Records one technical-account line (SPEC 11.3.4
 * {@code technical_account_line}).
 *
 * <p>The tenant is never in the body (it comes from {@code X-EIA-Tenant}); the
 * contract and account ids come from the path. The domain validates the request
 * shape; the expert owns the account status guard, the line sequence, and the
 * reconciliation against the account totals.</p>
 */
public record CreateTechnicalAccountLineCommand(
        String lineType,
        String reference,
        String description,
        BigDecimal amount
) {
}
