package com.eia.contract.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractCommandRulesTest {

    private static final UUID PARTY = UUID.fromString("44444444-4444-7444-8444-444444444444");

    private static ContractCommands.CreateLayer layer(Integer number, String type, String attachment,
                                                     String limit, String currency, LocalDate from,
                                                     LocalDate to) {
        return new ContractCommands.CreateLayer(number, type,
                attachment == null ? null : new BigDecimal(attachment),
                limit == null ? null : new BigDecimal(limit),
                currency, "PER_OCCURRENCE", null, null, null, null, from, to, null);
    }

    @Test
    void acceptsAValidLayer() {
        assertThatCode(() -> ContractCommandRules.validate(
                layer(1, "EXCESS_OF_LOSS", "1000000", "1000000", "USD", null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingLayerNumber() {
        assertThatThrownBy(() -> ContractCommandRules.validate(
                layer(null, "EXCESS_OF_LOSS", "0", "1000000", null, null, null)))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_LAYER_NUMBER");
    }

    @Test
    void rejectsUnknownLayerType() {
        assertThatThrownBy(() -> ContractCommandRules.validate(
                layer(1, "MAGIC", "0", "1000000", null, null, null)))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_LAYER_TYPE");
    }

    @Test
    void rejectsZeroLimit() {
        assertThatThrownBy(() -> ContractCommandRules.validate(
                layer(1, "EXCESS_OF_LOSS", "0", "0", null, null, null)))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_LIMIT_AMOUNT");
    }

    @Test
    void rejectsInvertedLayerDates() {
        assertThatThrownBy(() -> ContractCommandRules.validate(
                layer(1, "EXCESS_OF_LOSS", "0", "1000000", null,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1))))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void rejectsLowercaseLayerCurrency() {
        assertThatThrownBy(() -> ContractCommandRules.validate(
                layer(1, "EXCESS_OF_LOSS", "0", "1000000", "usd", null, null)))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_CURRENCY");
    }

    @Test
    void rejectsParticipantWithoutParty() {
        ContractCommands.CreateParticipant command = new ContractCommands.CreateParticipant(
                null, null, "REINSURER", new BigDecimal("50"), null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_PARTY_ID");
    }

    @Test
    void rejectsParticipantShareAboveOneHundred() {
        ContractCommands.CreateParticipant command = new ContractCommands.CreateParticipant(
                null, PARTY, "REINSURER", new BigDecimal("101"), null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_SHARE_PCT");
    }

    @Test
    void rejectsDirectBookCession() {
        ContractCommands.CreateCession command = new ContractCommands.CreateCession(
                null, null, "DIRECT", "CLAIM", null, "CLM-1", "QUOTA_SHARE", null,
                new BigDecimal("100"), null, null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("BOOK_WALL_VIOLATION");
    }

    @Test
    void rejectsClaimCessionWithoutReference() {
        ContractCommands.CreateCession command = new ContractCommands.CreateCession(
                null, null, "ASSUMED", "CLAIM", null, null, "QUOTA_SHARE", null,
                new BigDecimal("100"), null, null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("CES_CLAIM_REF_REQUIRED");
    }

    @Test
    void rejectsCessionWithoutAmount() {
        ContractCommands.CreateCession command = new ContractCommands.CreateCession(
                null, null, null, "CLAIM", "POL-1", "CLM-1", "QUOTA_SHARE", null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("CES_AMOUNT_REQUIRED");
    }

    @Test
    void acceptsCessionWithoutExplicitBook() {
        ContractCommands.CreateCession command = new ContractCommands.CreateCession(
                null, null, null, "CLAIM", "POL-1", "CLM-1", "QUOTA_SHARE", new BigDecimal("40"),
                new BigDecimal("400000"), new BigDecimal("240000"), "USD", null, null, 2026,
                "RECORDED", null);

        assertThatCode(() -> ContractCommandRules.validate(command)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBordereauWithInvertedPeriod() {
        ContractCommands.CreateBordereau command = new ContractCommands.CreateBordereau(
                null, "CLAIMS", LocalDate.of(2026, 3, 31), LocalDate.of(2026, 1, 1), null, null,
                "CSV", null, null, 1, null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void rejectsBordereauWithTooManyValidLines() {
        ContractCommands.CreateBordereau command = new ContractCommands.CreateBordereau(
                null, "CLAIMS", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), null, null,
                "CSV", null, null, 5, 6, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_LINE_COUNT");
    }

    @Test
    void rejectsCommutationValuedAfterEffectiveDate() {
        ContractCommands.CreateCommutation command = new ContractCommands.CreateCommutation(
                null, "FULL", LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31),
                new BigDecimal("2650000"), "USD", "NEGOTIATED", null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_COMMUTATION_DATES");
    }

    @Test
    void rejectsCommutationWithoutAgreedAmount() {
        ContractCommands.CreateCommutation command = new ContractCommands.CreateCommutation(
                null, "FULL", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 30),
                null, "USD", "NEGOTIATED", null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_AGREED_AMOUNT");
    }

    @Test
    void acceptsCommutationWithReleaseDocument() {
        ContractCommands.CreateCommutation command = new ContractCommands.CreateCommutation(
                null, "FULL", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 30),
                new BigDecimal("2650000"), "USD", "NEGOTIATED",
                UUID.fromString("77777777-7777-7777-8777-777777777777"), null, new BigDecimal("230000"));

        assertThatCode(() -> ContractCommandRules.validate(command)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSecurityExpiryBeforeEffectiveDate() {
        ContractCommands.CreateSecurity command = new ContractCommands.CreateSecurity(
                "LETTER_OF_CREDIT", new BigDecimal("5000000"), new BigDecimal("5000000"), "USD",
                null, null, LocalDate.of(2026, 6, 1), null, LocalDate.of(2026, 1, 1), null,
                null, null, null, null, Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_EXPIRY_DATE");
    }

    @Test
    void rejectsSecurityWithoutType() {
        ContractCommands.CreateSecurity command = new ContractCommands.CreateSecurity(
                null, new BigDecimal("5000000"), null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_SECURITY_TYPE");
    }

    @Test
    void rejectsNegativeBindExpectedVersion() {
        ContractCommands.Bind command = new ContractCommands.Bind(UUID.randomUUID(), -1);

        assertThatThrownBy(() -> ContractCommandRules.validate(command))
                .isInstanceOf(ContractValidationException.class)
                .extracting(ex -> ((ContractValidationException) ex).code())
                .isEqualTo("INVALID_EXPECTED_VERSION");
    }
}
