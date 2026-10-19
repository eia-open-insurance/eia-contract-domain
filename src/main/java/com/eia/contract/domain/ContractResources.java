package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read models for the contract's child resources.
 *
 * <p>They mirror the expert projections and the SPEC 11.3.4 entity dictionary.
 * Money is a decimal string (SPEC 10.3) and percentages carry six decimals.</p>
 */
public final class ContractResources {

    private ContractResources() {
    }

    public record Layer(
            UUID id,
            UUID tenantId,
            UUID contractId,
            UUID sectionId,
            int layerNumber,
            String layerType,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal attachmentAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal limitAmount,
            String currency,
            String limitBasis,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aggregateLimitAmount,
            Integer reinstatementCount,
            String reinstatementType,
            String note,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Participant(
            UUID id,
            UUID tenantId,
            UUID contractId,
            UUID layerId,
            UUID partyId,
            String participantRole,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal sharePct,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal signedLinePct,
            Integer orderNumber,
            boolean leadFlag,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal capacityAmount,
            String securityRating,
            boolean collateralRequired,
            String signatureStatus,
            Instant signedAt,
            String authorizationRef,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Term(
            UUID id,
            UUID tenantId,
            UUID contractId,
            String termCode,
            String termValue,
            String valueType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String sourceClause,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Security(
            UUID id,
            UUID tenantId,
            UUID contractId,
            String securityType,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal requiredAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal heldAmount,
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
            Instant approvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Cession(
            UUID id,
            UUID tenantId,
            UUID contractId,
            UUID layerId,
            String cessionNumber,
            String sourceVertical,
            String sourceType,
            String policyRef,
            String claimRef,
            String cessionType,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cessionPct,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cededPremiumAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cededLossAmount,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Integer treatyYear,
            String status,
            UUID bordereauId,
            Instant recordedAt,
            int version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Bordereau(
            UUID id,
            UUID tenantId,
            UUID contractId,
            String bordereauNumber,
            String bordereauType,
            LocalDate periodFrom,
            LocalDate periodTo,
            Instant receivedAt,
            UUID sourceDocumentId,
            String sourceFormat,
            String sourceReference,
            String status,
            int lineCount,
            Integer validLineCount,
            Integer exceptionCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount,
            String currency,
            String checksum,
            Instant processedAt,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record Commutation(
            UUID id,
            UUID tenantId,
            UUID contractId,
            String commutationNumber,
            String commutationType,
            LocalDate effectiveDate,
            LocalDate valuationDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal agreedAmount,
            String currency,
            String basis,
            String status,
            UUID releaseDocumentId,
            LocalDate releaseDate,
            UUID paymentId,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal savingsAmount,
            UUID approvedBy,
            Instant approvedAt,
            Instant executedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** Tower position returned by the layer summary. */
    public record TowerSummary(UUID contractId, int layerCount,
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal capacity,
                              List<String> gaps) {}

    /** Required terms for the contract type and the ones still missing. */
    public record TermRequirements(String contractType, List<String> required, List<String> missing) {}

    /** One bind precondition (SPEC 11.3.8 `contract.bind-eligible`). */
    public record BindCheck(String code, boolean passed, String detail) {}

    /** Bind readiness with the individual checks. */
    public record BindEligibility(UUID contractId, String contractNumber, String status,
                                  boolean bindable, List<BindCheck> checks) {}

    /** A bound contract and the eligibility snapshot that authorised it. */
    public record BindResult(ContractDto contract, BindEligibility eligibility) {}

    /** Result of executing a commutation. */
    public record CommutationExecution(Commutation commutation, ContractDto contract,
                                       int releasedSecurities, boolean contractClosed) {}
}
