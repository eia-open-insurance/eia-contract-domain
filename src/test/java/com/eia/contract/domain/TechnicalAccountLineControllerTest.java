package com.eia.contract.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TechnicalAccountLineController.class)
class TechnicalAccountLineControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID LINE_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final Instant CREATED_AT = Instant.parse("2026-04-01T09:35:00Z");
    private static final String LINES =
            "/api/v1/contracts/{contractId}/technical-accounts/{accountId}/lines";
    private static final String LINE = LINES + "/{lineId}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TechnicalAccountLineService service;

    @Test
    void listsTheAccountLines() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID)).thenReturn(summary());

        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineId").value(LINE_ID.toString()))
                .andExpect(jsonPath("$.items[0].accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.items[0].lineType").value("PREMIUM"))
                .andExpect(jsonPath("$.items[0].reference").value("BOR-2026-0001"))
                .andExpect(jsonPath("$.items[0].description").value("Ceded premium Q1"))
                .andExpect(jsonPath("$.items[0].amount").value("1000000.0000"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-04-01T09:35:00Z"))
                .andExpect(jsonPath("$.totals.premium").value("1000000.0000"))
                .andExpect(jsonPath("$.totals.loss").value("0"))
                .andExpect(jsonPath("$.totals.commission").value("0"))
                .andExpect(jsonPath("$.totals.other").value("0"));
    }

    @Test
    void listsTheDetailedTotalsAlongsideTheFourBuckets() throws Exception {
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        details.put("PREMIUM", new BigDecimal("100.0000"));
        details.put("PAID_LOSS", new BigDecimal("40.0000"));
        details.put("OUTSTANDING_LOSS", new BigDecimal("10.0000"));
        details.put("IBNR", new BigDecimal("7.0000"));
        details.put("ALAE", new BigDecimal("3.0000"));
        details.put("COMMISSION", new BigDecimal("3.0000"));
        details.put("BROKERAGE", new BigDecimal("2.0000"));
        details.put("REINSTATEMENT", new BigDecimal("5.0000"));
        details.put("CASH_CALL", new BigDecimal("1.0000"));
        details.put("FUNDS_WITHHELD", new BigDecimal("1.5000"));
        details.put("TAX", new BigDecimal("1.0000"));
        details.put("ADJUSTMENT", new BigDecimal("2.5000"));
        details.put("OTHER", new BigDecimal("0.5000"));
        when(service.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID))
                .thenReturn(new LineSummary(List.of(line()), new LineSummary.Totals(
                        new BigDecimal("100.0000"), new BigDecimal("66.0000"),
                        new BigDecimal("5.0000"), new BigDecimal("5.5000"), details)));

        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.premium").value("100.0000"))
                .andExpect(jsonPath("$.totals.loss").value("66.0000"))
                .andExpect(jsonPath("$.totals.commission").value("5.0000"))
                .andExpect(jsonPath("$.totals.other").value("5.5000"))
                .andExpect(jsonPath("$.totals.details.PREMIUM").value("100.0000"))
                .andExpect(jsonPath("$.totals.details.PAID_LOSS").value("40.0000"))
                .andExpect(jsonPath("$.totals.details.OUTSTANDING_LOSS").value("10.0000"))
                .andExpect(jsonPath("$.totals.details.IBNR").value("7.0000"))
                .andExpect(jsonPath("$.totals.details.ALAE").value("3.0000"))
                .andExpect(jsonPath("$.totals.details.COMMISSION").value("3.0000"))
                .andExpect(jsonPath("$.totals.details.BROKERAGE").value("2.0000"))
                .andExpect(jsonPath("$.totals.details.REINSTATEMENT").value("5.0000"))
                .andExpect(jsonPath("$.totals.details.CASH_CALL").value("1.0000"))
                .andExpect(jsonPath("$.totals.details.FUNDS_WITHHELD").value("1.5000"))
                .andExpect(jsonPath("$.totals.details.TAX").value("1.0000"))
                .andExpect(jsonPath("$.totals.details.ADJUSTMENT").value("2.5000"))
                .andExpect(jsonPath("$.totals.details.OTHER").value("0.5000"));
    }

    @Test
    void createsLine() throws Exception {
        CreateTechnicalAccountLineCommand command = new CreateTechnicalAccountLineCommand(
                "PREMIUM", "BOR-2026-0001", "Ceded premium Q1",
                new BigDecimal("1000000.0000"));
        when(service.create(TENANT, CONTRACT_ID, ACCOUNT_ID, command)).thenReturn(line());

        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "reference": "BOR-2026-0001",
                                 "description": "Ceded premium Q1",
                                 "amount": "1000000.0000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineId").value(LINE_ID.toString()))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.lineType").value("PREMIUM"))
                .andExpect(jsonPath("$.reference").value("BOR-2026-0001"))
                .andExpect(jsonPath("$.amount").value("1000000.0000"));

        verify(service).create(TENANT, CONTRACT_ID, ACCOUNT_ID, command);
    }

    @Test
    void acceptsCommissionAndOtherLineTypes() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), eq(ACCOUNT_ID),
                any(CreateTechnicalAccountLineCommand.class)))
                .thenReturn(new TechnicalAccountLine(LINE_ID, ACCOUNT_ID, "COMMISSION",
                        "COM-2026-0001", "Q1 ceding commission", new BigDecimal("250000.0000"),
                        CREATED_AT));

        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "COMMISSION", "reference": "COM-2026-0001",
                                 "description": "Q1 ceding commission", "amount": "250000.0000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lineType").value("COMMISSION"))
                .andExpect(jsonPath("$.reference").value("COM-2026-0001"));

        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "OTHER", "amount": "5000.0000"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void acceptsEveryTechnicalAccountLineType() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), eq(ACCOUNT_ID),
                any(CreateTechnicalAccountLineCommand.class)))
                .thenAnswer(invocation -> {
                    CreateTechnicalAccountLineCommand command = invocation.getArgument(3);
                    return new TechnicalAccountLine(LINE_ID, ACCOUNT_ID, command.lineType(),
                            "REF-" + command.lineType(), "Test " + command.lineType() + " line",
                            command.amount(), CREATED_AT);
                });

        for (String lineType : List.of(
                "PREMIUM", "COMMISSION", "BROKERAGE", "PAID_LOSS", "OUTSTANDING_LOSS",
                "IBNR", "ALAE", "REINSTATEMENT", "CASH_CALL", "FUNDS_WITHHELD", "TAX",
                "ADJUSTMENT", "OTHER")) {
            mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                            .header("X-EIA-Tenant", TENANT.toString())
                            .header("X-EIA-Book", "ASSUMED")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"lineType": "%s", "amount": "100.0000"}
                                    """.formatted(lineType)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lineType").value(lineType));
        }
    }

    @Test
    void rejectsTheRetiredLossLineTypeBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "LOSS", "amount": "250000.0000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LINE_TYPE"));

        verifyNoInteractions(service);
    }

    @Test
    void deletesLine() throws Exception {
        mvc.perform(delete(LINE, CONTRACT_ID, ACCOUNT_ID, LINE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNoContent());

        verify(service).delete(TENANT, CONTRACT_ID, ACCOUNT_ID, LINE_ID);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID)).thenReturn(summary());

        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineType").value("PREMIUM"));
    }

    @Test
    void acceptsTheAssumedBookCaseInsensitively() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID)).thenReturn(summary());

        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "assumed"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesTheEndpointFromAnotherBook() throws Exception {
        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Contract is an ASSUMED-only vertical; X-EIA-Book must be ASSUMED"));

        verifyNoInteractions(service);
    }

    @Test
    void hidesTheDeleteFromAnotherBook() throws Exception {
        mvc.perform(delete(LINE, CONTRACT_ID, ACCOUNT_ID, LINE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID).header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnUnknownLineTypeBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "FEE", "amount": "100.0000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LINE_TYPE"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAMissingAmountBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "description": "No amount"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAZeroAmountBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "amount": "0"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsANegativeAmountBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "amount": "-1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnOverlongReferenceBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "reference": "%s", "amount": "1"}
                                """.formatted("R".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFERENCE"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnOverlongDescriptionBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "description": "%s", "amount": "1"}
                                """.formatted("D".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DESCRIPTION"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), eq(ACCOUNT_ID),
                any(CreateTechnicalAccountLineCommand.class)))
                .thenThrow(new ContractConflictException("ACCOUNT_NOT_DRAFT",
                        "Lines can only be recorded on a DRAFT technical account"));

        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "amount": "100.0000"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_DRAFT"))
                .andExpect(jsonPath("$.message").value(
                        "Lines can only be recorded on a DRAFT technical account"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID))
                .thenThrow(new ResourceNotFoundException(
                        "Technical account aaaaaaaa was not found"));

        mvc.perform(get(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "Technical account aaaaaaaa was not found"));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), eq(ACCOUNT_ID),
                any(CreateTechnicalAccountLineCommand.class)))
                .thenThrow(new ContractValidationException("LINE_AMOUNT_INVALID", "amount",
                        "The line amount does not reconcile with the account"));

        mvc.perform(post(LINES, CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineType": "PREMIUM", "amount": "100.0000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LINE_AMOUNT_INVALID"))
                .andExpect(jsonPath("$.message").value(
                        "The line amount does not reconcile with the account"));
    }

    @Test
    void preservesTheExpertNotFoundProblemOnDelete() throws Exception {
        doThrow(new ResourceNotFoundException(
                        "Technical account line bbbbbbbb was not found"))
                .when(service).delete(TENANT, CONTRACT_ID, ACCOUNT_ID, LINE_ID);

        mvc.perform(delete(LINE, CONTRACT_ID, ACCOUNT_ID, LINE_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static LineSummary summary() {
        return new LineSummary(List.of(line()), new LineSummary.Totals(
                new BigDecimal("1000000.0000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static TechnicalAccountLine line() {
        return new TechnicalAccountLine(LINE_ID, ACCOUNT_ID, "PREMIUM", "BOR-2026-0001",
                "Ceded premium Q1", new BigDecimal("1000000.0000"), CREATED_AT);
    }
}
