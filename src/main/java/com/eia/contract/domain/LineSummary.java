package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The lines of one technical account plus their financial totals (SPEC 11.3.4
 * {@code technical_account_line}).
 *
 * <p>The expert owns the stored lines and normally returns the totals with
 * them; the domain normalizes a missing item list and derives any missing total
 * from the lines. The totals group the line types into the statement's four
 * header components: premium, loss, commission, and other. A richer projection
 * may use the SPEC 11.3.4 detail types; each one still rolls into exactly one
 * component (paid, outstanding, IBNR, and ALAE into loss; brokerage,
 * reinstatement, cash call, funds withheld, tax, and adjustment into other).
 * Money is a decimal string (SPEC 10.3).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineSummary(
        List<TechnicalAccountLine> items,
        Totals totals
) {
    public LineSummary {
        items = items == null ? List.of() : List.copyOf(items);
        totals = totals == null ? new Totals(null, null, null, null) : totals;
    }

    /**
     * The four financial buckets of the statement. A missing value is reported
     * as zero so an older expert projection still produces a well-formed body.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premium,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal loss,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal commission,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal other
    ) {
        public Totals {
            premium = premium == null ? BigDecimal.ZERO : premium;
            loss = loss == null ? BigDecimal.ZERO : loss;
            commission = commission == null ? BigDecimal.ZERO : commission;
            other = other == null ? BigDecimal.ZERO : other;
        }
    }
}
