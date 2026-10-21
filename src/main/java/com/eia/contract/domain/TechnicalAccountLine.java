package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of one technical-account line (SPEC 11.3.4
 * {@code technical_account_line}).
 *
 * <p>A line is one financial component of the periodic statement: premium,
 * commission, brokerage, paid or outstanding loss, IBNR, ALAE, reinstatement,
 * cash call, funds withheld, tax, or an adjustment. The expert owns the line
 * ordering and the reconciliation against the account; the domain proxies the
 * projection unchanged. Money is a decimal string (SPEC 10.3).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TechnicalAccountLine(
        @JsonAlias("id") UUID lineId,
        @JsonAlias("technicalAccountId") UUID accountId,
        String lineType,
        @JsonAlias("sourceRef") String reference,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        Instant createdAt
) {
}
