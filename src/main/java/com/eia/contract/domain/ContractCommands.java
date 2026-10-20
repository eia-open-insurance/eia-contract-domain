package com.eia.contract.domain;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Write commands for the contract's child resources. The tenant is never in the body
 * (it comes from {@code X-EIA-Tenant}) and the contract id is never in the body (it is
 * the path). The domain validates shape and enumerations; the expert re-validates and
 * owns the aggregate invariants (SPEC CTR-01..CTR-17).
 */
public final class ContractCommands {

    private ContractCommands() {
    }

    public record CreateLayer(
            Integer layerNumber,
            String layerType,
            BigDecimal attachmentAmount,
            BigDecimal limitAmount,
            String currency,
            String limitBasis,
            BigDecimal aggregateLimitAmount,
            Integer reinstatementCount,
            String reinstatementType,
            String note,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status
    ) {}

    public record CreateParticipant(
            UUID layerId,
            UUID partyId,
            String participantRole,
            BigDecimal sharePct,
            BigDecimal signedLinePct,
            Integer orderNumber,
            Boolean leadFlag,
            BigDecimal capacityAmount,
            String securityRating,
            Boolean collateralRequired,
            String signatureStatus,
            Instant signedAt,
            String authorizationRef,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status
    ) {}

    public record CreateTerm(
            String termCode,
            String termValue,
            String valueType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String sourceClause
    ) {}

    public record CreateSecurity(
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
    ) {}

    public record CreateCession(
            UUID layerId,
            String cessionNumber,
            String sourceVertical,
            String sourceType,
            String policyRef,
            String claimRef,
            String cessionType,
            BigDecimal cessionPct,
            BigDecimal cededPremiumAmount,
            BigDecimal cededLossAmount,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Integer treatyYear,
            String status,
            UUID bordereauId
    ) {}

    public record CreateCommutation(
            String commutationNumber,
            String commutationType,
            LocalDate effectiveDate,
            LocalDate valuationDate,
            BigDecimal agreedAmount,
            String currency,
            String basis,
            UUID releaseDocumentId,
            UUID paymentId,
            BigDecimal savingsAmount
    ) {}

    public record ApproveCommutation(@NotNull UUID approvedBy, String note) {}

    public record ExecuteCommutation(UUID releaseDocumentId, LocalDate releaseDate) {}

    public record Bind(@NotNull UUID approvedBy, @NotNull Integer expectedVersion) {}
}
