package com.eia.contract.domain;

import java.util.Locale;
import java.util.Set;

/**
 * The technical-account line vocabulary and its roll-up into the statement's
 * four financial buckets (SPEC 11.3.4 {@code technical_account_line}).
 *
 * <p>The domain accepts the same detail types as the expert. Every type
 * contributes to exactly one bucket: {@code PREMIUM} is premium;
 * {@code COMMISSION} and {@code BROKERAGE} are commission; paid and outstanding
 * loss, IBNR, ALAE, reinstatement, and cash call are loss; funds withheld, tax,
 * adjustment, and other are other. The retired aggregate {@code LOSS} type is
 * still understood when reading an older expert projection, but the create
 * command no longer accepts it because the expert migrated those rows to
 * {@code PAID_LOSS}.</p>
 */
final class TechnicalAccountLineTypes {

    static final String PREMIUM = "PREMIUM";
    static final String COMMISSION = "COMMISSION";
    static final String BROKERAGE = "BROKERAGE";
    static final String PAID_LOSS = "PAID_LOSS";
    static final String OUTSTANDING_LOSS = "OUTSTANDING_LOSS";
    static final String IBNR = "IBNR";
    static final String ALAE = "ALAE";
    static final String REINSTATEMENT = "REINSTATEMENT";
    static final String CASH_CALL = "CASH_CALL";
    static final String FUNDS_WITHHELD = "FUNDS_WITHHELD";
    static final String TAX = "TAX";
    static final String ADJUSTMENT = "ADJUSTMENT";
    static final String OTHER = "OTHER";

    /** The retired aggregate loss type; only understood when reading old data. */
    static final String LEGACY_LOSS = "LOSS";

    /** The implemented SPEC line types; this is also the expert's vocabulary. */
    static final Set<String> DETAIL_TYPES = Set.of(
            PREMIUM, COMMISSION, BROKERAGE, PAID_LOSS, OUTSTANDING_LOSS, IBNR, ALAE,
            REINSTATEMENT, CASH_CALL, FUNDS_WITHHELD, TAX, ADJUSTMENT, OTHER);

    private TechnicalAccountLineTypes() {
    }

    /** One of the four header buckets a line contributes to. */
    enum Bucket {
        PREMIUM,
        LOSS,
        COMMISSION,
        OTHER
    }

    /** @return the type trimmed and uppercased, or null for blank input */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /** @return whether the public create command accepts the type */
    static boolean accepts(String raw) {
        String type = normalize(raw);
        return type != null && DETAIL_TYPES.contains(type);
    }

    /**
     * Maps a line type to its bucket. A type outside the known vocabulary is
     * treated as other, which keeps an older or newer expert projection
     * well-formed instead of rejecting the whole read model.
     */
    static Bucket bucketOf(String raw) {
        String type = normalize(raw);
        if (type == null) {
            return Bucket.OTHER;
        }
        return switch (type) {
            case PREMIUM -> Bucket.PREMIUM;
            case COMMISSION, BROKERAGE -> Bucket.COMMISSION;
            case LEGACY_LOSS, PAID_LOSS, OUTSTANDING_LOSS, IBNR, ALAE,
                    REINSTATEMENT, CASH_CALL -> Bucket.LOSS;
            default -> Bucket.OTHER;
        };
    }
}
