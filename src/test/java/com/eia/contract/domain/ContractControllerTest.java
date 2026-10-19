package com.eia.contract.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContractController.class)
class ContractControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID CEDENT = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID REINSURER = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContractClient client;

    private static ContractDto contract(String status, int version) {
        return new ContractDto(CONTRACT_ID, TENANT, "XOL-2025-00091", "EXCESS_OF_LOSS", CEDENT, REINSURER,
                status, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "USD", version, NOW, NOW);
    }

    private static String createBody(String effectiveDate, String expirationDate) {
        return """
                {
                  "contractNumber": "XOL-2025-00091",
                  "contractType": "EXCESS_OF_LOSS",
                  "cedentPartyId": "%s",
                  "reinsurerPartyId": "%s",
                  "effectiveDate": "%s",
                  "expirationDate": "%s",
                  "currency": "USD"
                }
                """.formatted(CEDENT, REINSURER, effectiveDate, expirationDate);
    }

    @Test
    void requiresTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/contracts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"))
                .andExpect(jsonPath("$.details[0].field").value("X-EIA-Tenant"));

        verifyNoInteractions(client);
    }

    @Test
    void listsContractsForTenant() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(contract("DRAFT", 0)));

        mvc.perform(get("/api/v1/contracts").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractNumber").value("XOL-2025-00091"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void createsDraftContract() throws Exception {
        when(client.create(eq(TENANT), any(CreateContractCommand.class), eq(ContractType.EXCESS_OF_LOSS)))
                .thenReturn(contract("DRAFT", 0));

        mvc.perform(post("/api/v1/contracts")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2025-01-01", "2025-12-31")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractNumber").value("XOL-2025-00091"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(client).create(eq(TENANT), any(CreateContractCommand.class), eq(ContractType.EXCESS_OF_LOSS));
    }

    @Test
    void rejectsExpirationBeforeEffectiveDate() throws Exception {
        mvc.perform(post("/api/v1/contracts")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2025-12-31", "2025-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.details[0].field").value("expirationDate"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsUnknownContractType() throws Exception {
        String body = createBody("2025-01-01", "2025-12-31").replace("EXCESS_OF_LOSS", "MYSTERY_TREATY");

        mvc.perform(post("/api/v1/contracts")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTRACT_TYPE"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsLowercaseCurrency() throws Exception {
        String body = createBody("2025-01-01", "2025-12-31").replace("\"USD\"", "\"usd\"");

        mvc.perform(post("/api/v1/contracts")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(client);
    }

    @Test
    void mapsDuplicateContractNumberToConflict() throws Exception {
        when(client.create(eq(TENANT), any(CreateContractCommand.class), any(ContractType.class)))
                .thenThrow(new DuplicateContractNumberException("XOL-2025-00091"));

        mvc.perform(post("/api/v1/contracts")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2025-01-01", "2025-12-31")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CONTRACT_NUMBER"));
    }

    @Test
    void returnsNotFoundForUnknownContract() throws Exception {
        when(client.findById(TENANT, CONTRACT_ID))
                .thenThrow(new ContractNotFoundException(CONTRACT_ID));

        mvc.perform(get("/api/v1/contracts/{id}", CONTRACT_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsIllegalStatusTransition() throws Exception {
        when(client.findById(TENANT, CONTRACT_ID)).thenReturn(contract("DRAFT", 0));

        mvc.perform(patch("/api/v1/contracts/{id}/status", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SIGNED", "expectedVersion": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.details[0].field").value("allowedNextStatuses"));

        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
    }

    @Test
    void appliesLegalStatusTransition() throws Exception {
        when(client.findById(TENANT, CONTRACT_ID)).thenReturn(contract("DRAFT", 0));
        when(client.updateStatus(TENANT, CONTRACT_ID, ContractStatus.QUOTED, 0))
                .thenReturn(contract("QUOTED", 1));

        mvc.perform(patch("/api/v1/contracts/{id}/status", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "QUOTED", "expectedVersion": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.version").value(1));

        verify(client).updateStatus(TENANT, CONTRACT_ID, ContractStatus.QUOTED, 0);
    }

    @Test
    void rejectsStatusChangeWithoutExpectedVersion() throws Exception {
        when(client.findById(TENANT, CONTRACT_ID)).thenReturn(contract("DRAFT", 0));

        mvc.perform(patch("/api/v1/contracts/{id}/status", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "QUOTED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
    }

    @Test
    void reportsExpertUnavailable() throws Exception {
        when(client.findAll(TENANT))
                .thenThrow(new ExpertIntegrationException("connection refused", new RuntimeException("down")));

        mvc.perform(get("/api/v1/contracts").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXPERT_UNAVAILABLE"));
    }

    /** SPEC 11.3.8: bind is a command with preconditions and approval, not a bare PATCH. */
    @Test
    void rejectsBoundTransitionThroughStatusPatch() throws Exception {
        when(client.findById(TENANT, CONTRACT_ID)).thenReturn(contract("PLACED", 2));

        mvc.perform(patch("/api/v1/contracts/{id}/status", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "BOUND", "expectedVersion": 2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIND_REQUIRES_COMMAND"));

        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
    }
}
