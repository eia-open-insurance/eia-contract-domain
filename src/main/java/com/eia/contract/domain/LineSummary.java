package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lines of one technical account plus their financial totals (SPEC 11.3.4
 * {@code technical_account_line}).
 *
 * <p>The expert owns the stored lines and normally returns the totals with
 * them; the domain normalizes a missing item list and derives any missing total
 * from the lines. The totals keep the statement's four financial buckets:
 * premium, loss (paid, outstanding, IBNR, ALAE, reinstatement, and cash call),
 * commission (commission and brokerage), and other (funds withheld, tax,
 * adjustment, and other). A detailed map carries the per-line-type totals when
 * the expert supplies them; it is omitted when there is no detail. Money is a
 * decimal string (SPEC 10.3).</p>
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
     * The four financial buckets of the statement plus the optional detailed
     * map. A missing bucket value is reported as zero so an older expert
     * projection still produces a well-formed body; a missing detailed map is
     * an empty map and is omitted from the JSON body.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premium,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal loss,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal commission,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal other,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @JsonSerialize(contentUsing = MoneySerializer.class)
            Map<String, BigDecimal> details
    ) {
        public Totals {
            premium = premium == null ? BigDecimal.ZERO : premium;
            loss = loss == null ? BigDecimal.ZERO : loss;
            commission = commission == null ? BigDecimal.ZERO : commission;
            other = other == null ? BigDecimal.ZERO : other;
            details = details == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        }

        /** Compatibility constructor for a projection without detailed totals. */
        public Totals(BigDecimal premium, BigDecimal loss,
                      BigDecimal commission, BigDecimal other) {
            this(premium, loss, commission, other, Map.of());
        }

        @Override
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonSerialize(contentUsing = MoneySerializer.class)
        public Map<String, BigDecimal> details() {
            return details;
        }
    }

    /**
     * Writes one detailed money value as a decimal string, matching the public
     * money representation used by the bucket totals (SPEC 10.3).
     */
    public static final class MoneySerializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator,
                              SerializerProvider serializers) throws IOException {
            generator.writeString(value == null ? "0" : value.toPlainString());
        }
    }
}
