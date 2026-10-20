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

@WebMvcTest(TechnicalAccountController.class)
class TechnicalAccountControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");
    private static final String ACCOUNTS = "/api/v1/contracts/{contractId}/technical-accounts";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TechnicalAccountService service;

    @Test
    void listsTechnicalAccounts() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(account("ISSUED")));

        mvc.perform(get(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[0].accountNumber").value("TA-2026-00077"))
                .andExpect(jsonPath("$[0].contractId").value(CONTRACT_ID.toString()))
                .andExpect(jsonPath("$[0].periodStart").value("2026-01-01"))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-03-31"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].cededPremium").value("400000.0000"))
                .andExpect(jsonPath("$[0].cededLosses").value("240000.0000"))
                .andExpect(jsonPath("$[0].commission").value("100000.0000"))
                .andExpect(jsonPath("$[0].otherCharges").value("5000.0000"))
                .andExpect(jsonPath("$[0].balance").value("355000.0000"))
                .andExpect(jsonPath("$[0].status").value("ISSUED"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-04-01T09:30:00Z"));
    }

    @Test
    void producesTechnicalAccount() throws Exception {
        CreateTechnicalAccountCommand command = new CreateTechnicalAccountCommand(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "USD",
                new BigDecimal("400000.0000"), new BigDecimal("240000.0000"),
                new BigDecimal("100000.0000"), new BigDecimal("5000.0000"));
        when(service.create(TENANT, CONTRACT_ID, command)).thenReturn(account("ISSUED"));

        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "currency": "USD", "cededPremium": "400000.0000",
                                 "cededLosses": "240000.0000", "commission": "100000.0000",
                                 "otherCharges": "5000.0000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("TA-2026-00077"))
                .andExpect(jsonPath("$.balance").value("355000.0000"))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        verify(service).create(TENANT, CONTRACT_ID, command);
    }

    @Test
    void updatesTechnicalAccountStatus() throws Exception {
        UpdateTechnicalAccountStatusCommand command = new UpdateTechnicalAccountStatusCommand("SETTLED");
        when(service.updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID, command))
                .thenReturn(account("SETTLED"));

        mvc.perform(patch(ACCOUNTS + "/{accountId}/status", CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SETTLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.status").value("SETTLED"));

        verify(service).updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID, command);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(account("ISSUED")));

        mvc.perform(get(ACCOUNTS, CONTRACT_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("TA-2026-00077"));
    }

    @Test
    void acceptsTheAssumedBookCaseInsensitively() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(account("ISSUED")));

        mvc.perform(get(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "assumed"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesTheEndpointFromAnotherBook() throws Exception {
        mvc.perform(get(ACCOUNTS, CONTRACT_ID)
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
        mvc.perform(get(ACCOUNTS, CONTRACT_ID).header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnInvertedPeriodBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-03-31", "periodEnd": "2026-01-01",
                                 "currency": "USD", "cededPremium": "400000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.details[0].field").value("periodEnd"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsACommandWithoutPeriod() throws Exception {
        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency": "USD", "cededPremium": "400000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD_START"));
    }

    @Test
    void rejectsACommandWithoutCurrency() throws Exception {
        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "cededPremium": "400000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    void rejectsACommandWithoutAmounts() throws Exception {
        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "currency": "USD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CEDED_PREMIUM"));
    }

    @Test
    void rejectsNegativeMoneyBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "currency": "USD", "cededPremium": "-1",
                                 "cededLosses": "1", "commission": "1", "otherCharges": "1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CEDED_PREMIUM"));
    }

    @Test
    void rejectsAnUnknownStatusBeforeCallingTheExpert() throws Exception {
        mvc.perform(patch(ACCOUNTS + "/{accountId}/status", CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnEmptyStatus() throws Exception {
        mvc.perform(patch(ACCOUNTS + "/{accountId}/status", CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.updateStatus(eq(TENANT), eq(CONTRACT_ID), eq(ACCOUNT_ID),
                any(UpdateTechnicalAccountStatusCommand.class)))
                .thenThrow(new ContractConflictException("INVALID_ACCOUNT_TRANSITION",
                        "Technical account TA-2026-00077 cannot move from DRAFT to SETTLED"));

        mvc.perform(patch(ACCOUNTS + "/{accountId}/status", CONTRACT_ID, ACCOUNT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SETTLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        "Technical account TA-2026-00077 cannot move from DRAFT to SETTLED"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID))
                .thenThrow(new ResourceNotFoundException("Contract 22222222 was not found"));

        mvc.perform(get(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Contract 22222222 was not found"));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID),
                any(CreateTechnicalAccountCommand.class)))
                .thenThrow(new ContractValidationException("PERIOD_OVERLAP", null,
                        "Period 2026-01-01..2026-03-31 overlaps TA-2026-00076"));

        mvc.perform(post(ACCOUNTS, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                                 "currency": "USD", "cededPremium": "400000",
                                 "cededLosses": "240000", "commission": "100000",
                                 "otherCharges": "5000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_OVERLAP"))
                .andExpect(jsonPath("$.message").value(
                        "Period 2026-01-01..2026-03-31 overlaps TA-2026-00076"));
    }

    private static TechnicalAccount account(String status) {
        return new TechnicalAccount(ACCOUNT_ID, "TA-2026-00077", CONTRACT_ID,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "USD",
                new BigDecimal("400000.0000"), new BigDecimal("240000.0000"),
                new BigDecimal("100000.0000"), new BigDecimal("5000.0000"),
                new BigDecimal("355000.0000"), status, NOW);
    }
}
