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

@WebMvcTest(CashCallController.class)
class CashCallControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID CASH_CALL_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");
    private static final String CASH_CALLS = "/api/v1/contracts/{contractId}/cash-calls";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CashCallService service;

    @Test
    void listsCashCalls() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(cashCall("REQUESTED")));

        mvc.perform(get(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cashCallId").value(CASH_CALL_ID.toString()))
                .andExpect(jsonPath("$[0].callNumber").value("CC-2026-00042"))
                .andExpect(jsonPath("$[0].contractId").value(CONTRACT_ID.toString()))
                .andExpect(jsonPath("$[0].periodStart").value("2026-01-01"))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-03-31"))
                .andExpect(jsonPath("$[0].amount").value("250000.0000"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-04-15"))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$[0].notes").value("Large loss funding"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-04-01T09:30:00Z"));
    }

    @Test
    void createsCashCall() throws Exception {
        CreateCashCallCommand command = new CreateCashCallCommand(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                new BigDecimal("250000.0000"), "USD", LocalDate.of(2026, 4, 15),
                "Large loss funding");
        when(service.create(TENANT, CONTRACT_ID, command)).thenReturn(cashCall("REQUESTED"));

        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "amount": "250000.0000", "currency": "USD",
                                 "dueDate": "2026-04-15", "notes": "Large loss funding"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashCallId").value(CASH_CALL_ID.toString()))
                .andExpect(jsonPath("$.callNumber").value("CC-2026-00042"))
                .andExpect(jsonPath("$.amount").value("250000.0000"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        verify(service).create(TENANT, CONTRACT_ID, command);
    }

    @Test
    void updatesCashCallStatus() throws Exception {
        UpdateCashCallStatusCommand command = new UpdateCashCallStatusCommand("ISSUED");
        when(service.updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID, command))
                .thenReturn(cashCall("ISSUED"));

        mvc.perform(patch(CASH_CALLS + "/{cashCallId}/status", CONTRACT_ID, CASH_CALL_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ISSUED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashCallId").value(CASH_CALL_ID.toString()))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        verify(service).updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID, command);
    }

    @Test
    void settlesCashCallWithASettlementReference() throws Exception {
        UpdateCashCallStatusCommand command =
                new UpdateCashCallStatusCommand("SETTLED", "STL-2026-0007");
        when(service.updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID, command))
                .thenReturn(cashCall("SETTLED", "STL-2026-0007"));

        mvc.perform(patch(CASH_CALLS + "/{cashCallId}/status", CONTRACT_ID, CASH_CALL_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SETTLED", "reference": "STL-2026-0007"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashCallId").value(CASH_CALL_ID.toString()))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settlementReference").value("STL-2026-0007"));

        verify(service).updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID, command);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(cashCall("REQUESTED")));

        mvc.perform(get(CASH_CALLS, CONTRACT_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].callNumber").value("CC-2026-00042"));
    }

    @Test
    void acceptsTheAssumedBookCaseInsensitively() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(cashCall("REQUESTED")));

        mvc.perform(get(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "assumed"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesTheEndpointFromAnotherBook() throws Exception {
        mvc.perform(get(CASH_CALLS, CONTRACT_ID)
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
        mvc.perform(get(CASH_CALLS, CONTRACT_ID).header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsACommandWithoutPeriodStart() throws Exception {
        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodEnd": "2026-03-31", "amount": "250000",
                                 "currency": "USD", "dueDate": "2026-04-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD_START"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnInvertedPeriodBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-03-31", "periodEnd": "2026-01-01",
                                 "amount": "250000", "currency": "USD",
                                 "dueDate": "2026-04-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.details[0].field").value("periodEnd"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAZeroAmountBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "amount": "0", "currency": "USD", "dueDate": "2026-04-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsACommandWithoutCurrency() throws Exception {
        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "amount": "250000", "dueDate": "2026-04-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsACommandWithoutDueDate() throws Exception {
        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "amount": "250000", "currency": "USD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DUE_DATE"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnUnknownStatusBeforeCallingTheExpert() throws Exception {
        mvc.perform(patch(CASH_CALLS + "/{cashCallId}/status", CONTRACT_ID, CASH_CALL_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsRequestedAsAStatusTarget() throws Exception {
        mvc.perform(patch(CASH_CALLS + "/{cashCallId}/status", CONTRACT_ID, CASH_CALL_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REQUESTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.updateStatus(eq(TENANT), eq(CONTRACT_ID), eq(CASH_CALL_ID),
                any(UpdateCashCallStatusCommand.class)))
                .thenThrow(new ContractConflictException("INVALID_STATUS_TRANSITION",
                        "Cash-call status cannot move from SETTLED to ISSUED"));

        mvc.perform(patch(CASH_CALLS + "/{cashCallId}/status", CONTRACT_ID, CASH_CALL_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ISSUED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        "Cash-call status cannot move from SETTLED to ISSUED"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID))
                .thenThrow(new ResourceNotFoundException("Contract 22222222 was not found"));

        mvc.perform(get(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Contract 22222222 was not found"));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), any(CreateCashCallCommand.class)))
                .thenThrow(new ContractValidationException("INVALID_AMOUNT", "amount",
                        "amount must be greater than zero"));

        mvc.perform(post(CASH_CALLS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "amount": "250000", "currency": "USD",
                                 "dueDate": "2026-04-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"))
                .andExpect(jsonPath("$.message").value("amount must be greater than zero"));
    }

    private static CashCall cashCall(String status) {
        return cashCall(status, null);
    }

    private static CashCall cashCall(String status, String settlementReference) {
        return new CashCall(CASH_CALL_ID, "CC-2026-00042", CONTRACT_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                new BigDecimal("250000.0000"), "USD", LocalDate.of(2026, 4, 15),
                status, settlementReference, "Large loss funding", NOW);
    }
}
