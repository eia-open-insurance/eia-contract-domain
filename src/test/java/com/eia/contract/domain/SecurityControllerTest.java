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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityController.class)
class SecurityControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID SECURITY_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final UUID PROVIDER = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");
    private static final String SECURITY = "/api/v1/contracts/{contractId}/security";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SecurityService service;

    @Test
    void listsSecurity() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(security("OUTSTANDING")));

        mvc.perform(get(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SECURITY_ID.toString()))
                .andExpect(jsonPath("$[0].securityType").value("LETTER_OF_CREDIT"))
                .andExpect(jsonPath("$[0].requiredAmount").value("5000000.0000"))
                .andExpect(jsonPath("$[0].heldAmount").value("4500000.0000"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].providerPartyId").value(PROVIDER.toString()))
                .andExpect(jsonPath("$[0].expiryDate").value("2027-03-31"))
                .andExpect(jsonPath("$[0].approvalRef").value("APR-2026-0007"))
                .andExpect(jsonPath("$[0].status").value("OUTSTANDING"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-04-01T09:30:00Z"));
    }

    @Test
    void createsSecurity() throws Exception {
        CreateSecurityCommand command = new CreateSecurityCommand(
                "LETTER_OF_CREDIT", new BigDecimal("5000000"), new BigDecimal("4500000"),
                "USD", PROVIDER, null, null, null, LocalDate.of(2027, 3, 31), null, null,
                null, null, "APR-2026-0007", NOW);
        when(service.create(TENANT, CONTRACT_ID, command)).thenReturn(security("OUTSTANDING"));

        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "heldAmount": "4500000", "currency": "USD",
                                 "providerPartyId": "%s", "expiryDate": "2027-03-31",
                                 "approvalRef": "APR-2026-0007",
                                 "approvedAt": "2026-04-01T09:30:00Z"}
                                """.formatted(PROVIDER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SECURITY_ID.toString()))
                .andExpect(jsonPath("$.securityType").value("LETTER_OF_CREDIT"))
                .andExpect(jsonPath("$.requiredAmount").value("5000000.0000"))
                .andExpect(jsonPath("$.heldAmount").value("4500000.0000"))
                .andExpect(jsonPath("$.status").value("OUTSTANDING"));

        verify(service).create(TENANT, CONTRACT_ID, command);
    }

    @Test
    void acceptsAnAbsentBookForCompatibility() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(security("OUTSTANDING")));

        mvc.perform(get(SECURITY, CONTRACT_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].securityType").value("LETTER_OF_CREDIT"));
    }

    @Test
    void acceptsTheAssumedBookCaseInsensitively() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID)).thenReturn(List.of(security("OUTSTANDING")));

        mvc.perform(get(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "assumed"))
                .andExpect(status().isOk());
    }

    @Test
    void hidesTheEndpointFromAnotherBook() throws Exception {
        mvc.perform(get(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        "Contract is an ASSUMED-only vertical; X-EIA-Book must be ASSUMED"));

        verifyNoInteractions(service);
    }

    @Test
    void hidesTheCreateEndpointFromAnotherBook() throws Exception {
        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        verifyNoInteractions(service);
    }

    @Test
    void requiresTheTenantHeader() throws Exception {
        mvc.perform(get(SECURITY, CONTRACT_ID).header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsSecurityWithoutRequiredAmount() throws Exception {
        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUIRED_AMOUNT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnUnknownSecurityTypeBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "DEPOSIT", "requiredAmount": "5000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SECURITY_TYPE"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNegativeHeldCollateralBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "heldAmount": "-1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HELD_AMOUNT"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnExpiryBeforeTheEffectiveDateBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "effectiveFrom": "2026-06-01", "expiryDate": "2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPIRY_DATE"))
                .andExpect(jsonPath("$.details[0].field").value("expiryDate"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsTooLongApprovalReferenceBeforeCallingTheExpert() throws Exception {
        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "approvalRef": "%s"}
                                """.formatted("A".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APPROVAL_REF"));

        verifyNoInteractions(service);
    }

    @Test
    void preservesTheExpertConflictProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), any(CreateSecurityCommand.class)))
                .thenThrow(new ContractConflictException("COLLATERAL_EXCEEDS_OBLIGATION",
                        "Held collateral 7500000.0000 exceeds the secured obligation 5000000.0000"));

        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "heldAmount": "7500000"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLATERAL_EXCEEDS_OBLIGATION"))
                .andExpect(jsonPath("$.message").value(
                        "Held collateral 7500000.0000 exceeds the secured obligation 5000000.0000"));
    }

    @Test
    void preservesTheExpertNotFoundProblem() throws Exception {
        when(service.findAll(TENANT, CONTRACT_ID))
                .thenThrow(new ResourceNotFoundException("Contract 22222222 was not found"));

        mvc.perform(get(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Contract 22222222 was not found"));
    }

    @Test
    void preservesTheExpertValidationProblem() throws Exception {
        when(service.create(eq(TENANT), eq(CONTRACT_ID), any(CreateSecurityCommand.class)))
                .thenThrow(new ContractValidationException("INVALID_SECURITY_TYPE", "securityType",
                        "Unknown securityType: DEPOSIT"));

        mvc.perform(post(SECURITY, CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SECURITY_TYPE"))
                .andExpect(jsonPath("$.message").value("Unknown securityType: DEPOSIT"));
    }

    private static SecurityRecord security(String status) {
        return new SecurityRecord(SECURITY_ID, "LETTER_OF_CREDIT",
                new BigDecimal("5000000.0000"), new BigDecimal("4500000.0000"), "USD",
                PROVIDER, LocalDate.of(2027, 3, 31), "APR-2026-0007", status, NOW);
    }
}
