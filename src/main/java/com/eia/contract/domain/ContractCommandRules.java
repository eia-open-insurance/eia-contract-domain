package com.eia.contract.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * Domain-side shape validation for the child-resource commands.
 *
 * <p>The domain service owns business validation and rejects malformed input with a
 * structured 400 before the expert is called (SPEC 7.1, SPEC 12). Aggregate
 * invariants that need stored state — share totals, tower overlap, capacity, approval
 * evidence — stay in the expert and surface as 409.</p>
 */
final class ContractCommandRules {

    static final Set<String> LAYER_TYPES = Set.of(
            "PROPORTIONAL", "EXCESS_OF_LOSS", "AGGREGATE_EXCESS", "STOP_LOSS",
            "CATASTROPHE", "CLASH", "FACULTATIVE", "TOP_AND_DROP");

    static final Set<String> LIMIT_BASES = Set.of(
            "PER_OCCURRENCE", "PER_RISK", "PER_EVENT", "ANNUAL_AGGREGATE", "POLICY_YEAR", "PER_LOSS");

    static final Set<String> REINSTATEMENT_TYPES = Set.of(
            "NONE", "FREE", "PAID_PRO_RATA", "PAID_FULL_RATE", "PER_LOSS");

    static final Set<String> LAYER_STATUSES = Set.of(
            "QUOTED", "BOUND", "IN_FORCE", "EXHAUSTED", "EXPIRED", "CANCELLED");

    static final Set<String> PARTICIPANT_ROLES = Set.of(
            "REINSURER", "CEDENT", "LEAD", "FOLLOW", "FRONTING", "RETROCESSIONAIRE", "BROKER");

    static final Set<String> SIGNATURE_STATUSES = Set.of("PENDING", "SIGNED", "DECLINED", "WITHDRAWN");

    static final Set<String> PARTICIPANT_STATUSES = Set.of("ACTIVE", "ENDED", "REPLACED");

    static final Set<String> SECURITY_TYPES = Set.of(
            "LETTER_OF_CREDIT", "TRUST_FUND", "FUNDS_WITHHELD", "CASH_ADVANCE",
            "PARENT_GUARANTEE", "BANK_GUARANTEE", "SECURITIES");

    static final Set<String> SECURITY_STATUSES = Set.of(
            "REQUIRED", "OUTSTANDING", "PARTIAL", "RELEASED", "EXPIRED", "BREACH");

    static final Set<String> COLLATERAL_QUALITIES = Set.of(
            "CASH", "GOVERNMENT_SECURITIES", "BANK_GUARANTEE", "CORPORATE_GUARANTEE", "OTHER");

    static final Set<String> SOURCE_TYPES = Set.of("POLICY", "CLAIM", "LOSS_ADVICE");

    static final Set<String> CESSION_TYPES = Set.of(
            "QUOTA_SHARE", "SURPLUS", "FACULTATIVE", "EXCESS_OF_LOSS", "AGGREGATE", "RETROCESSION");

    static final Set<String> CESSION_STATUSES = Set.of("RECORDED", "CONFIRMED", "REVERSED", "DISPUTED");

    static final Set<String> BORDEREAU_STATUSES = Set.of("RECEIVED", "PROCESSED", "FAILED");

    static final Set<String> COMMUTATION_TYPES = Set.of(
            "FULL", "PARTIAL", "LOSS_PORTFOLIO", "BUYOUT", "NOVATION");

    static final Set<String> COMMUTATION_BASES = Set.of(
            "NEGOTIATED", "CONTRACTUAL", "INDUCED", "REGULATORY", "INSOLVENCY");

    static final Set<String> TECHNICAL_ACCOUNT_STATUSES = Set.of(
            "DRAFT", "ISSUED", "AGREED", "SETTLED", "DISPUTED", "SUPERSEDED");

    static final Set<String> CASH_CALL_STATUSES = Set.of("ISSUED", "SETTLED", "CANCELLED");

    private ContractCommandRules() {
    }

    static void validate(ContractCommands.CreateLayer command) {
        integer(command.layerNumber(), "layerNumber", 1, 999, true);
        oneOf(LAYER_TYPES, command.layerType(), "layerType", true);
        money(command.attachmentAmount(), "attachmentAmount", true);
        BigDecimal limit = money(command.limitAmount(), "limitAmount", true);
        if (limit.signum() <= 0) {
            throw new ContractValidationException("INVALID_LIMIT_AMOUNT", "limitAmount",
                    "limitAmount must be greater than zero");
        }
        currency(command.currency(), "currency");
        oneOf(LIMIT_BASES, command.limitBasis(), "limitBasis", true);
        money(command.aggregateLimitAmount(), "aggregateLimitAmount", false);
        integer(command.reinstatementCount(), "reinstatementCount", 0, 99, false);
        oneOf(REINSTATEMENT_TYPES, command.reinstatementType(), "reinstatementType", false);
        dateRange(command.effectiveFrom(), command.effectiveTo());
        oneOf(LAYER_STATUSES, command.status(), "status", false);
    }

    static void validate(ContractCommands.CreateParticipant command) {
        required(command.partyId(), "partyId");
        oneOf(PARTICIPANT_ROLES, command.participantRole(), "participantRole", true);
        percent(command.sharePct(), "sharePct", true);
        percent(command.signedLinePct(), "signedLinePct", false);
        integer(command.orderNumber(), "orderNumber", 1, 999, false);
        text(command.securityRating(), "securityRating", 8);
        oneOf(SIGNATURE_STATUSES, command.signatureStatus(), "signatureStatus", false);
        oneOf(PARTICIPANT_STATUSES, command.status(), "status", false);
        dateRange(command.effectiveFrom(), command.effectiveTo());
    }

    static void validate(ContractCommands.CreateTerm command) {
        text(command.termCode(), "termCode", 48);
        text(command.termValue(), "termValue", 400);
        text(command.valueType(), "valueType", 16);
        dateRange(command.effectiveFrom(), command.effectiveTo());
        text(command.sourceClause(), "sourceClause", 48);
    }

    static void validate(CreateSecurityCommand command) {
        oneOf(SECURITY_TYPES, command.securityType(), "securityType", true);
        money(command.requiredAmount(), "requiredAmount", true);
        money(command.heldAmount(), "heldAmount", false);
        currency(command.currency(), "currency");
        oneOf(SECURITY_STATUSES, command.status(), "status", false);
        oneOf(COLLATERAL_QUALITIES, command.collateralQuality(), "collateralQuality", false);
        text(command.approvalRef(), "approvalRef", 64);
        dateRange(command.effectiveFrom(), command.effectiveTo());
        if (command.expiryDate() != null && command.effectiveFrom() != null
                && command.expiryDate().isBefore(command.effectiveFrom())) {
            throw new ContractValidationException("INVALID_EXPIRY_DATE", "expiryDate",
                    "expiryDate must not be before effectiveFrom");
        }
    }

    static void validate(ContractCommands.CreateCession command) {
        if (command.sourceVertical() != null && !command.sourceVertical().isBlank()
                && !"ASSUMED".equalsIgnoreCase(command.sourceVertical().trim())) {
            throw new ContractValidationException("BOOK_WALL_VIOLATION", "sourceVertical",
                    "Contract is ASSUMED book only; " + command.sourceVertical() + " is not allowed");
        }
        String sourceType = oneOf(SOURCE_TYPES, command.sourceType(), "sourceType", true);
        oneOf(CESSION_TYPES, command.cessionType(), "cessionType", true);
        if ("CLAIM".equals(sourceType) && blank(command.claimRef())) {
            throw new ContractValidationException("CES_CLAIM_REF_REQUIRED", "claimRef",
                    "A CLAIM cession requires claimRef");
        }
        if ("POLICY".equals(sourceType) && blank(command.policyRef())) {
            throw new ContractValidationException("CES_POLICY_REF_REQUIRED", "policyRef",
                    "A POLICY cession requires policyRef");
        }
        BigDecimal premium = money(command.cededPremiumAmount(), "cededPremiumAmount", false);
        BigDecimal loss = money(command.cededLossAmount(), "cededLossAmount", false);
        if (premium == null && loss == null) {
            throw new ContractValidationException("CES_AMOUNT_REQUIRED", "cededLossAmount",
                    "A cession requires cededPremiumAmount or cededLossAmount");
        }
        percent(command.cessionPct(), "cessionPct", false);
        currency(command.currency(), "currency");
        integer(command.treatyYear(), "treatyYear", 1900, 2100, false);
        oneOf(CESSION_STATUSES, command.status(), "status", false);
        dateRange(command.effectiveFrom(), command.effectiveTo());
    }

    /**
     * A received bordereau always belongs to the ASSUMED book: the period and
     * receipt date must be present and ordered, the currency must be an ISO
     * code, every financial component is required and non-negative, and the
     * line count is a non-negative integer. The expert owns the currency
     * reconciliation against the contract, the technical-account linkage, and
     * the lifecycle transitions.
     */
    static void validate(CreateBordereauCommand command) {
        required(command.periodStart(), "periodStart");
        required(command.periodEnd(), "periodEnd");
        if (command.periodStart() != null && command.periodEnd() != null
                && !command.periodEnd().isAfter(command.periodStart())) {
            throw new ContractValidationException("INVALID_DATE_RANGE", "periodEnd",
                    "periodEnd must be after periodStart");
        }
        required(command.receivedDate(), "receivedDate");
        requiredCurrency(command.currency(), "currency");
        money(command.premiumAmount(), "premiumAmount", true);
        money(command.lossAmount(), "lossAmount", true);
        money(command.commissionAmount(), "commissionAmount", true);
        money(command.otherChargesAmount(), "otherChargesAmount", true);
        integer(command.lineCount(), "lineCount", 0, 10_000_000, true);
        text(command.notes(), "notes", 4000);
    }

    static void validate(UpdateBordereauStatusCommand command) {
        String status = oneOf(BORDEREAU_STATUSES, command.status(), "status", true);
        if ("RECEIVED".equals(status)) {
            throw new ContractValidationException("INVALID_STATUS", "status",
                    "Only PROCESSED and FAILED can be requested; RECEIVED is the creation state");
        }
        text(command.notes(), "notes", 4000);
    }

    static void validate(ContractCommands.CreateCommutation command) {
        oneOf(COMMUTATION_TYPES, command.commutationType(), "commutationType", false);
        required(command.effectiveDate(), "effectiveDate");
        required(command.valuationDate(), "valuationDate");
        if (command.valuationDate().isAfter(command.effectiveDate())) {
            throw new ContractValidationException("INVALID_COMMUTATION_DATES", "valuationDate",
                    "valuationDate must be on or before effectiveDate");
        }
        money(command.agreedAmount(), "agreedAmount", true);
        money(command.savingsAmount(), "savingsAmount", false);
        currency(command.currency(), "currency");
        oneOf(COMMUTATION_BASES, command.basis(), "basis", false);
    }

    static void validate(ContractCommands.Bind command) {
        if (command.expectedVersion() != null && command.expectedVersion() < 0) {
            throw new ContractValidationException("INVALID_EXPECTED_VERSION", "expectedVersion",
                    "expectedVersion must not be negative");
        }
    }

    /**
     * Producing an account stays within the ASSUMED treaty: the period must be
     * complete and ordered, currency must be present and an ISO code, and every
     * monetary component is required and non-negative. Period overlap, the
     * contract window, and the balance reconciliation are aggregate rules
     * enforced by the expert (CTR-07, CTR-08, CTR-17).
     */
    static void validate(CreateTechnicalAccountCommand command) {
        required(command.periodStart(), "periodStart");
        required(command.periodEnd(), "periodEnd");
        if (command.periodStart() != null && command.periodEnd() != null
                && !command.periodEnd().isAfter(command.periodStart())) {
            throw new ContractValidationException("INVALID_DATE_RANGE", "periodEnd",
                    "periodEnd must be after periodStart");
        }
        requiredCurrency(command.currency(), "currency");
        money(command.cededPremium(), "cededPremium", true);
        money(command.cededLosses(), "cededLosses", true);
        money(command.commission(), "commission", true);
        money(command.otherCharges(), "otherCharges", true);
    }

    static void validate(UpdateTechnicalAccountStatusCommand command) {
        oneOf(TECHNICAL_ACCOUNT_STATUSES, command.status(), "status", true);
    }

    /**
     * A cash call is a forward payment request on one contract period: the
     * period must be complete and ordered, the amount is required and strictly
     * positive, the currency must be an ISO code, and the due date is required.
     * Call-number generation, the contract window, and the lifecycle are
     * aggregate rules enforced by the expert (CTR-07, CTR-08, CASH-05).
     */
    static void validate(CreateCashCallCommand command) {
        required(command.periodStart(), "periodStart");
        required(command.periodEnd(), "periodEnd");
        if (command.periodStart() != null && command.periodEnd() != null
                && !command.periodEnd().isAfter(command.periodStart())) {
            throw new ContractValidationException("INVALID_DATE_RANGE", "periodEnd",
                    "periodEnd must be after periodStart");
        }
        BigDecimal amount = money(command.amount(), "amount", true);
        if (amount.signum() == 0) {
            throw new ContractValidationException("INVALID_AMOUNT", "amount",
                    "amount must be greater than zero");
        }
        requiredCurrency(command.currency(), "currency");
        required(command.dueDate(), "dueDate");
        text(command.notes(), "notes", 4000);
    }

    static void validate(UpdateCashCallStatusCommand command) {
        oneOf(CASH_CALL_STATUSES, command.status(), "status", true);
    }

    private static String oneOf(Set<String> allowed, String raw, String field, boolean required) {
        if (blank(raw)) {
            if (required) {
                throw invalid(field, field + " is required");
            }
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw invalid(field, "Unknown " + field + ": " + raw);
        }
        return value;
    }

    private static BigDecimal money(BigDecimal raw, String field, boolean required) {
        if (raw == null) {
            if (required) {
                throw invalid(field, field + " is required");
            }
            return null;
        }
        if (raw.signum() < 0) {
            throw invalid(field, field + " must not be negative");
        }
        return raw;
    }

    private static BigDecimal percent(BigDecimal raw, String field, boolean required) {
        if (raw == null) {
            if (required) {
                throw invalid(field, field + " is required");
            }
            return null;
        }
        if (raw.signum() < 0 || raw.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw invalid(field, field + " must be between 0 and 100");
        }
        return raw;
    }

    private static Integer integer(Integer raw, String field, int min, int max, boolean required) {
        if (raw == null) {
            if (required) {
                throw invalid(field, field + " is required");
            }
            return null;
        }
        if (raw < min || raw > max) {
            throw invalid(field, field + " must be between " + min + " and " + max);
        }
        return raw;
    }

    private static void currency(String raw, String field) {
        if (blank(raw)) {
            return;
        }
        if (!raw.trim().matches("[A-Z]{3}")) {
            throw invalid(field, field + " must be a three-letter uppercase ISO currency code");
        }
    }

    private static void requiredCurrency(String raw, String field) {
        if (blank(raw)) {
            throw invalid(field, field + " is required");
        }
        currency(raw, field);
    }

    private static void text(String raw, String field, int max) {
        if (raw != null && raw.trim().length() > max) {
            throw invalid(field, field + " must be at most " + max + " characters");
        }
    }

    private static void dateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ContractValidationException("INVALID_DATE_RANGE", "effectiveTo",
                    "effectiveTo must be after effectiveFrom");
        }
    }

    private static void required(Object value, String field) {
        if (value == null) {
            throw invalid(field, field + " is required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ContractValidationException invalid(String field, String message) {
        return new ContractValidationException("INVALID_" + snake(field), field, message);
    }

    private static String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }
}
