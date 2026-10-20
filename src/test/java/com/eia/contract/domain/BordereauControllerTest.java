package com.eia.contract.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BordereauController.class)
class BordereauControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID BORDEREAU_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID TECHNICAL_ACCOUNT_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-04-02T09:30:00Z");
    private static final String BASE = "/api/v1/contracts/{contractId}/bordereaux";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BordereauService service;

    @Test
    void listsBordereaux() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(bordereau("RECEIVED")));

        mvc.perform(get(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bordereauId").value(BORDEREAU_ID.toString()))
                .andExpect(jsonPath("$[0].bordereauNumber").value("BRD-2026-000412"))
                .andExpect(jsonPath("$[0].contractId").value(CONTRACT_ID.toString()))
                .andExpect(jsonPath("$[0].periodStart").value("2026-01-01"))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-03-31"))
                .andExpect(jsonPath("$[0].receivedDate").value("2026-04-02"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].premiumAmount").value("400000.0000"))
                .andExpect(jsonPath("$[0].lossAmount").value("240000.0000"))
                .andExpect(jsonPath("$[0].commissionAmount").value("100000.0000"))
                .andExpect(jsonPath("$[0].otherChargesAmount").value("5000.0000"))
                .andExpect(jsonPath("$[0].lineCount").value(10))
                .andExpect(jsonPath("$[0].notes").value("Q1 claims bordereau"))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].technicalAccountId").value(TECHNICAL_ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[0].createdAt").value("2026-04-02T09:30:00Z"));
    }

    @Test
    void returnsOneBordereau() throws Exception {
        when(service.findOne(TENANT, CONTRACT_ID, BORDEREAU_ID)).thenReturn(bordereau("RECEIVED"));

        mvc.perform(get(BASE + "/{bordereauId}", CONTRACT_ID, BORDEREAU_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bordereauId").value(BORDEREAU_ID.toString()))
                .andExpect(jsonPath("$.bordereauNumber").value("BRD-2026-000412"));
    }

    @Test
    void recordsBordereau() throws Exception {
        CreateBordereauCommand command = new CreateBordereauCommand(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 2), "USD",
                new BigDecimal("400000.0000"), new BigDecimal("240000.0000"),
                new BigDecimal("100000.0000"), new BigDecimal("5000.0000"),
                10, "Q1 claims bordereau");
        when(service.create(TENANT, CONTRACT_ID, command)).thenReturn(bordereau("RECEIVED"));

        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "receivedDate": "2026-04-02", "currency": "USD",
                                 "premiumAmount": "400000.0000", "lossAmount": "240000.0000",
                                 "commissionAmount": "100000.0000",
                                 "otherChargesAmount": "5000.0000",
                                 "lineCount": 10, "notes": "Q1 claims bordereau"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bordereauNumber").value("BRD-2026-000412"))
                .andExpect(jsonPath("$.premiumAmount").value("400000.0000"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        verify(service).create(TENANT, CONTRACT_ID, command);
    }

    @Test
    void changesBordereauStatus() throws Exception {
        UpdateBordereauStatusCommand command =
                new UpdateBordereauStatusCommand("POSTED", "Posted to TA-2026-00077");
        when(service.updateStatus(TENANT, CONTRACT_ID, BORDEREAU_ID, command))
                .thenReturn(bordereau("POSTED"));

        mvc.perform(patch(BASE + "/{bordereauId}/status", CONTRACT_ID, BORDEREAU_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "POSTED", "notes": "Posted to TA-2026-00077"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bordereauId").value(BORDEREAU_ID.toString()))
                .andExpect(jsonPath("$.status").value("POSTED"));

        verify(service).updateStatus(TENANT, CONTRACT_ID, BORDEREAU_ID, command);
    }

    @Test
    void changesBordereauStatusWithoutNotes() throws Exception {
        UpdateBordereauStatusCommand command = new UpdateBordereauStatusCommand("POSTED", null);
        when(service.updateStatus(TENANT, CONTRACT_ID, BORDEREAU_ID, command))
                .thenReturn(bordereau("POSTED"));

        mvc.perform(patch(BASE + "/{bordereauId}/status", CONTRACT_ID, BORDEREAU_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"POSTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(bordereau("RECEIVED")));

        mvc.perform(get(BASE, CONTRACT_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bordereauNumber").value("BRD-2026-000412"));
    }

    @Test
    void acceptsTheAssumedBookCaseInsensitively() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(bordereau("RECEIVED")));

        mvc.perform(get(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "assumed"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesTheEndpointFromAnotherBook() throws Exception {
        mvc.perform(get(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Contract is an ASSUMED-only vertical; X-EIA-Book must be ASSUMED"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(BASE, CONTRACT_ID).header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnInvertedPeriodBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-03-31", "periodEnd": "2026-01-01",
                                 "receivedDate": "2026-04-02", "currency": "USD",
                                 "premiumAmount": "400000", "lossAmount": "240000",
                                 "commissionAmount": "100000", "otherChargesAmount": "5000",
                                 "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.details[0].field").value("periodEnd"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsACommandWithoutPeriod() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receivedDate": "2026-04-02", "currency": "USD",
                                 "premiumAmount": "400000", "lossAmount": "240000",
                                 "commissionAmount": "100000", "otherChargesAmount": "5000",
                                 "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD_START"));
    }

    @Test
    void rejectsACommandWithoutReceivedDate() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "currency": "USD", "premiumAmount": "400000",
                                 "lossAmount": "240000", "commissionAmount": "100000",
                                 "otherChargesAmount": "5000", "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECEIVED_DATE"));
    }

    @Test
    void rejectsACommandWithoutCurrency() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "receivedDate": "2026-04-02", "premiumAmount": "400000",
                                 "lossAmount": "240000", "commissionAmount": "100000",
                                 "otherChargesAmount": "5000", "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    void rejectsACommandWithoutAmounts() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "receivedDate": "2026-04-02", "currency": "USD",
                                 "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PREMIUM_AMOUNT"));
    }

    @Test
    void rejectsNegativeMoneyBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "receivedDate": "2026-04-02", "currency": "USD",
                                 "premiumAmount": "400000", "lossAmount": "-1",
                                 "commissionAmount": "100000", "otherChargesAmount": "5000",
                                 "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOSS_AMOUNT"));
    }

    @Test
    void rejectsACommandWithoutLineCount() throws Exception {
        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "receivedDate": "2026-04-02", "currency": "USD",
                                 "premiumAmount": "400000", "lossAmount": "240000",
                                 "commissionAmount": "100000", "otherChargesAmount": "5000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LINE_COUNT"));
    }

    @Test
    void rejectsAnUnknownStatusBeforeCallingTheExpert() throws Exception {
        mvc.perform(patch(BASE + "/{bordereauId}/status", CONTRACT_ID, BORDEREAU_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnEmptyStatus() throws Exception {
        mvc.perform(patch(BASE + "/{bordereauId}/status", CONTRACT_ID, BORDEREAU_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.updateStatus(eq(TENANT), eq(CONTRACT_ID), eq(BORDEREAU_ID),
                any(UpdateBordereauStatusCommand.class)))
                .thenThrow(new ContractConflictException("INVALID_BORDEREAU_TRANSITION",
                        "Bordereau BRD-2026-000412 cannot move from RECEIVED to POSTED"));

        mvc.perform(patch(BASE + "/{bordereauId}/status", CONTRACT_ID, BORDEREAU_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"POSTED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BORDEREAU_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        "Bordereau BRD-2026-000412 cannot move from RECEIVED to POSTED"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID))
                .thenThrow(new ResourceNotFoundException("Contract 22222222 was not found"));

        mvc.perform(get(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Contract 22222222 was not found"));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), any(CreateBordereauCommand.class)))
                .thenThrow(new ContractValidationException("BORDEREAU_CURRENCY_MISMATCH", null,
                        "Bordereau currency EUR does not match contract currency USD"));

        mvc.perform(post(BASE, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "receivedDate": "2026-04-02", "currency": "EUR",
                                 "premiumAmount": "400000", "lossAmount": "240000",
                                 "commissionAmount": "100000", "otherChargesAmount": "5000",
                                 "lineCount": 10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BORDEREAU_CURRENCY_MISMATCH"))
                .andExpect(jsonPath("$.message").value(
                        "Bordereau currency EUR does not match contract currency USD"));
    }

    private static Bordereau bordereau(String status) {
        return new Bordereau(BORDEREAU_ID, "BRD-2026-000412", CONTRACT_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 2), "USD",
                new BigDecimal("400000.0000"), new BigDecimal("240000.0000"),
                new BigDecimal("100000.0000"), new BigDecimal("5000.0000"),
                10, "Q1 claims bordereau", status, TECHNICAL_ACCOUNT_ID, NOW);
    }
}
