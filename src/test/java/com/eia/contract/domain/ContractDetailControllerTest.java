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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContractDetailController.class)
class ContractDetailControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID LAYER_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
    private static final UUID PARTY = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final UUID APPROVER = UUID.fromString("66666666-6666-7666-8666-666666666666");
    private static final UUID DOCUMENT = UUID.fromString("77777777-7777-7777-8777-777777777777");
    private static final UUID COMMUTATION_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContractDetailClient client;

    private static ContractDto contract(String status) {
        return new ContractDto(CONTRACT_ID, TENANT, "XOL-2026-00091", "EXCESS_OF_LOSS",
                UUID.fromString("33333333-3333-7333-8333-333333333333"), PARTY, status,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "USD", 2, NOW, NOW);
    }

    private static ContractResources.Layer layer() {
        return new ContractResources.Layer(LAYER_ID, TENANT, CONTRACT_ID, null, 1, "EXCESS_OF_LOSS",
                new BigDecimal("1000000.0000"), new BigDecimal("2000000.0000"), "USD",
                "PER_OCCURRENCE", null, null, "NONE", null, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), "QUOTED", NOW, NOW);
    }

    private static ContractResources.Participant participant() {
        return new ContractResources.Participant(UUID.randomUUID(), TENANT, CONTRACT_ID, LAYER_ID,
                PARTY, "REINSURER", new BigDecimal("100.000000"), null, 1, true, null, "A+",
                false, "PENDING", null, null, LocalDate.of(2026, 1, 1), null, "ACTIVE", NOW, NOW);
    }

    private static ContractResources.Term term() {
        return new ContractResources.Term(UUID.randomUUID(), TENANT, CONTRACT_ID,
                "CEDING_COMMISSION_PCT", "25.000000", "PERCENT", LocalDate.of(2026, 1, 1), null,
                "FOLLOW_THE_SETTLEMENTS", NOW, NOW);
    }

    private static ContractResources.Security security() {
        return new ContractResources.Security(UUID.randomUUID(), TENANT, CONTRACT_ID,
                "LETTER_OF_CREDIT", new BigDecimal("5000000.0000"), new BigDecimal("5000000.0000"),
                "USD", PARTY, null, LocalDate.of(2026, 1, 1), null, LocalDate.of(2027, 1, 1),
                "OUTSTANDING", "CASH", null, null, null, null, NOW, NOW);
    }

    private static ContractResources.Cession cession() {
        return new ContractResources.Cession(UUID.randomUUID(), TENANT, CONTRACT_ID, null,
                "CES-2026-00001", "ASSUMED", "CLAIM", "POL-2026-0001", "CLM-2026-0007",
                "QUOTA_SHARE", new BigDecimal("40.000000"), new BigDecimal("400000.0000"),
                new BigDecimal("240000.0000"), "USD", LocalDate.of(2026, 1, 1), null, 2026,
                "RECORDED", null, NOW, 0, NOW, NOW);
    }

    private static ContractResources.Commutation commutation(String status) {
        return new ContractResources.Commutation(COMMUTATION_ID, TENANT, CONTRACT_ID,
                "COM-2026-00001", "FULL", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 30),
                new BigDecimal("2650000.0000"), "USD", "NEGOTIATED", status, DOCUMENT,
                null, null, new BigDecimal("230000.0000"), APPROVER, NOW,
                "EXECUTED".equals(status) ? NOW : null, NOW, NOW);
    }

    // ---------------------------------------------------------------- layers

    @Test
    void listsLayers() throws Exception {
        when(client.findLayers(TENANT, CONTRACT_ID)).thenReturn(List.of(layer()));

        mvc.perform(get("/api/v1/contracts/{id}/layers", CONTRACT_ID).header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].layerNumber").value(1))
                .andExpect(jsonPath("$[0].attachmentAmount").value("1000000.0000"))
                .andExpect(jsonPath("$[0].limitAmount").value("2000000.0000"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void reportsTowerSummary() throws Exception {
        when(client.layerSummary(TENANT, CONTRACT_ID)).thenReturn(new ContractResources.TowerSummary(
                CONTRACT_ID, 2, new BigDecimal("3000000.0000"), List.of("Gap between layer 1 top 1000000")));

        mvc.perform(get("/api/v1/contracts/{id}/layers/summary", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layerCount").value(2))
                .andExpect(jsonPath("$.capacity").value("3000000.0000"))
                .andExpect(jsonPath("$.gaps.length()").value(1));
    }

    @Test
    void createsLayer() throws Exception {
        when(client.createLayer(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.CreateLayer.class)))
                .thenReturn(layer());

        mvc.perform(post("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layerNumber": 1, "layerType": "EXCESS_OF_LOSS",
                                 "attachmentAmount": "1000000", "limitAmount": "2000000",
                                 "limitBasis": "PER_OCCURRENCE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.layerNumber").value(1))
                .andExpect(jsonPath("$.attachmentAmount").value("1000000.0000"));
    }

    @Test
    void rejectsUnknownLayerTypeBeforeCallingTheExpert() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layerNumber": 1, "layerType": "MAGIC",
                                 "attachmentAmount": "1000000", "limitAmount": "2000000",
                                 "limitBasis": "PER_OCCURRENCE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LAYER_TYPE"))
                .andExpect(jsonPath("$.details[0].field").value("layerType"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsZeroLayerLimit() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layerNumber": 1, "layerType": "EXCESS_OF_LOSS",
                                 "attachmentAmount": "0", "limitAmount": "0",
                                 "limitBasis": "PER_OCCURRENCE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT_AMOUNT"));
    }

    @Test
    void rejectsLowercaseCurrencyOnLayer() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layerNumber": 1, "layerType": "EXCESS_OF_LOSS",
                                 "attachmentAmount": "0", "limitAmount": "1000000",
                                 "limitBasis": "PER_OCCURRENCE", "currency": "usd"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    @Test
    void mapsOverlappingLayerConflictFromTheExpert() throws Exception {
        when(client.createLayer(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.CreateLayer.class)))
                .thenThrow(new ContractConflictException("LAYER_OVERLAP",
                        "Layer 2 attaches at 500000, below the top of layer 1"));

        mvc.perform(post("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layerNumber": 2, "layerType": "EXCESS_OF_LOSS",
                                 "attachmentAmount": "500000", "limitAmount": "1000000",
                                 "limitBasis": "PER_OCCURRENCE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAYER_OVERLAP"))
                .andExpect(jsonPath("$.message").value(
                        "Layer 2 attaches at 500000, below the top of layer 1"));
    }

    // ---------------------------------------------------------- participants

    @Test
    void listsParticipants() throws Exception {
        when(client.findParticipants(TENANT, CONTRACT_ID)).thenReturn(List.of(participant()));

        mvc.perform(get("/api/v1/contracts/{id}/participants", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantRole").value("REINSURER"))
                .andExpect(jsonPath("$[0].sharePct").value("100.000000"))
                .andExpect(jsonPath("$[0].leadFlag").value(true));
    }

    @Test
    void createsParticipant() throws Exception {
        when(client.createParticipant(eq(TENANT), eq(CONTRACT_ID),
                any(ContractCommands.CreateParticipant.class))).thenReturn(participant());

        mvc.perform(post("/api/v1/contracts/{id}/participants", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partyId": "44444444-4444-7444-8444-444444444444",
                                 "participantRole": "REINSURER", "sharePct": "100"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sharePct").value("100.000000"));
    }

    @Test
    void rejectsParticipantWithoutParty() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/participants", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantRole": "REINSURER", "sharePct": "100"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARTY_ID"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsShareAboveOneHundred() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/participants", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partyId": "44444444-4444-7444-8444-444444444444",
                                 "participantRole": "REINSURER", "sharePct": "101"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHARE_PCT"));
    }

    @Test
    void mapsUnbalancedSharesConflictFromTheExpert() throws Exception {
        when(client.createParticipant(eq(TENANT), eq(CONTRACT_ID),
                any(ContractCommands.CreateParticipant.class)))
                .thenThrow(new ContractConflictException("SHARES_EXCEED_100",
                        "Participant shares would total 110% for this layer"));

        mvc.perform(post("/api/v1/contracts/{id}/participants", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partyId": "44444444-4444-7444-8444-444444444444",
                                 "participantRole": "REINSURER", "sharePct": "40"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHARES_EXCEED_100"));
    }

    // ----------------------------------------------------------------- terms

    @Test
    void listsTerms() throws Exception {
        when(client.findTerms(TENANT, CONTRACT_ID)).thenReturn(List.of(term()));

        mvc.perform(get("/api/v1/contracts/{id}/terms", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].termCode").value("CEDING_COMMISSION_PCT"))
                .andExpect(jsonPath("$[0].termValue").value("25.000000"));
    }

    @Test
    void reportsTermRequirements() throws Exception {
        when(client.termRequirements(TENANT, CONTRACT_ID)).thenReturn(
                new ContractResources.TermRequirements("EXCESS_OF_LOSS",
                        List.of("GOVERNING_LAW", "REINSTATEMENT_PROVISION", "ULTIMATE_NET_LOSS"),
                        List.of("ULTIMATE_NET_LOSS")));

        mvc.perform(get("/api/v1/contracts/{id}/terms/requirements", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractType").value("EXCESS_OF_LOSS"))
                .andExpect(jsonPath("$.required.length()").value(3))
                .andExpect(jsonPath("$.missing[0]").value("ULTIMATE_NET_LOSS"));
    }

    @Test
    void createsTerm() throws Exception {
        when(client.createTerm(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.CreateTerm.class)))
                .thenReturn(term());

        mvc.perform(post("/api/v1/contracts/{id}/terms", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termCode": "CEDING_COMMISSION_PCT", "termValue": "25",
                                 "sourceClause": "FOLLOW_THE_SETTLEMENTS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.termValue").value("25.000000"));
    }

    @Test
    void rejectsInvertedTermEffectiveRange() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/terms", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termCode": "GOVERNING_LAW", "termValue": "New York",
                                 "effectiveFrom": "2026-06-01", "effectiveTo": "2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    // -------------------------------------------------------------- security

    @Test
    void listsSecurity() throws Exception {
        when(client.findSecurity(TENANT, CONTRACT_ID)).thenReturn(List.of(security()));

        mvc.perform(get("/api/v1/contracts/{id}/security", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].securityType").value("LETTER_OF_CREDIT"))
                .andExpect(jsonPath("$[0].heldAmount").value("5000000.0000"));
    }

    @Test
    void createsSecurity() throws Exception {
        when(client.createSecurity(eq(TENANT), eq(CONTRACT_ID),
                any(ContractCommands.CreateSecurity.class))).thenReturn(security());

        mvc.perform(post("/api/v1/contracts/{id}/security", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "heldAmount": "5000000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUTSTANDING"));
    }

    @Test
    void rejectsSecurityWithoutRequiredAmount() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/security", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUIRED_AMOUNT"));
    }

    @Test
    void mapsCollateralAboveObligationConflictFromTheExpert() throws Exception {
        when(client.createSecurity(eq(TENANT), eq(CONTRACT_ID),
                any(ContractCommands.CreateSecurity.class)))
                .thenThrow(new ContractConflictException("COLLATERAL_EXCEEDS_OBLIGATION",
                        "Held collateral 7500000.0000 exceeds the secured obligation 5000000.0000"));

        mvc.perform(post("/api/v1/contracts/{id}/security", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityType": "LETTER_OF_CREDIT", "requiredAmount": "5000000",
                                 "heldAmount": "7500000"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLATERAL_EXCEEDS_OBLIGATION"));
    }

    // -------------------------------------------------------------- cessions

    @Test
    void listsCessions() throws Exception {
        when(client.findCessions(TENANT, CONTRACT_ID)).thenReturn(List.of(cession()));

        mvc.perform(get("/api/v1/contracts/{id}/cessions", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cessionNumber").value("CES-2026-00001"))
                .andExpect(jsonPath("$[0].cededLossAmount").value("240000.0000"))
                .andExpect(jsonPath("$[0].sourceVertical").value("ASSUMED"));
    }

    @Test
    void recordsCession() throws Exception {
        when(client.createCession(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.CreateCession.class)))
                .thenReturn(cession());

        mvc.perform(post("/api/v1/contracts/{id}/cessions", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType": "CLAIM", "claimRef": "CLM-2026-0007",
                                 "policyRef": "POL-2026-0001", "cessionType": "QUOTA_SHARE",
                                 "cessionPct": "40", "cededPremiumAmount": "400000",
                                 "cededLossAmount": "240000"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.cededPremiumAmount").value("400000.0000"));
    }

    /** The ASSUMED book wall is enforced in the domain as well as in the expert. */
    @Test
    void rejectsDirectBookCession() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/cessions", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceVertical": "DIRECT", "sourceType": "CLAIM",
                                 "claimRef": "CLM-2026-0007", "cessionType": "QUOTA_SHARE",
                                 "cededLossAmount": "240000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOK_WALL_VIOLATION"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsCessionWithoutAmount() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/cessions", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType": "CLAIM", "claimRef": "CLM-2026-0007",
                                 "cessionType": "QUOTA_SHARE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CES_AMOUNT_REQUIRED"));
    }

    @Test
    void mapsCessionBeyondLimitConflictFromTheExpert() throws Exception {
        when(client.createCession(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.CreateCession.class)))
                .thenThrow(new ContractConflictException("CESSION_EXCEEDS_CONTRACT_LIMIT",
                        "Ceded loss 1100000.0000 exceeds 1000000.0000 available on the contract tower"));

        mvc.perform(post("/api/v1/contracts/{id}/cessions", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType": "CLAIM", "claimRef": "CLM-2026-0007",
                                 "cessionType": "QUOTA_SHARE", "cededLossAmount": "200000"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CESSION_EXCEEDS_CONTRACT_LIMIT"));
    }

    // ------------------------------------------------------------ commutations

    @Test
    void listsCommutations() throws Exception {
        when(client.findCommutations(TENANT, CONTRACT_ID)).thenReturn(List.of(commutation("PROPOSED")));

        mvc.perform(get("/api/v1/contracts/{id}/commutations", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commutationNumber").value("COM-2026-00001"))
                .andExpect(jsonPath("$[0].agreedAmount").value("2650000.0000"));
    }

    @Test
    void proposesCommutation() throws Exception {
        when(client.createCommutation(eq(TENANT), eq(CONTRACT_ID),
                any(ContractCommands.CreateCommutation.class))).thenReturn(commutation("PROPOSED"));

        mvc.perform(post("/api/v1/contracts/{id}/commutations", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commutationType": "FULL", "effectiveDate": "2026-12-31",
                                 "valuationDate": "2026-11-30", "agreedAmount": "2650000",
                                 "basis": "NEGOTIATED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    void rejectsCommutationValuedAfterItTakesEffect() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/commutations", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commutationType": "FULL", "effectiveDate": "2026-12-31",
                                 "valuationDate": "2027-01-31", "agreedAmount": "2650000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COMMUTATION_DATES"));
    }

    @Test
    void approvesCommutation() throws Exception {
        when(client.approveCommutation(eq(TENANT), eq(CONTRACT_ID), eq(COMMUTATION_ID),
                any(ContractCommands.ApproveCommutation.class))).thenReturn(commutation("APPROVED"));

        mvc.perform(post("/api/v1/contracts/{id}/commutations/{commutationId}/approve",
                        CONTRACT_ID, COMMUTATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\": \"" + APPROVER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(APPROVER.toString()));
    }

    @Test
    void rejectsApprovalWithoutApprover() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/commutations/{commutationId}/approve",
                        CONTRACT_ID, COMMUTATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("approvedBy"));

        verify(client, never()).approveCommutation(any(), any(), any(), any());
    }

    @Test
    void executesCommutationAndClosesTheContract() throws Exception {
        when(client.executeCommutation(eq(TENANT), eq(CONTRACT_ID), eq(COMMUTATION_ID),
                any(ContractCommands.ExecuteCommutation.class))).thenReturn(
                new ContractResources.CommutationExecution(commutation("EXECUTED"), contract("CLOSED"), 2, true));

        mvc.perform(post("/api/v1/contracts/{id}/commutations/{commutationId}/execute",
                        CONTRACT_ID, COMMUTATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseDocumentId\": \"" + DOCUMENT + "\", \"releaseDate\": \"2026-12-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractClosed").value(true))
                .andExpect(jsonPath("$.releasedSecurities").value(2))
                .andExpect(jsonPath("$.commutation.status").value("EXECUTED"))
                .andExpect(jsonPath("$.contract.status").value("CLOSED"));
    }

    @Test
    void mapsCommutationApprovalConflictFromTheExpert() throws Exception {
        when(client.executeCommutation(eq(TENANT), eq(CONTRACT_ID), eq(COMMUTATION_ID), any()))
                .thenThrow(new ContractConflictException("COMMUTATION_NOT_APPROVED",
                        "Commutation COM-2026-00001 is PROPOSED; it must be APPROVED before it can be executed"));

        mvc.perform(post("/api/v1/contracts/{id}/commutations/{commutationId}/execute",
                        CONTRACT_ID, COMMUTATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseDocumentId\": \"" + DOCUMENT + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUTATION_NOT_APPROVED"));
    }

    // ------------------------------------------------------------------ bind

    @Test
    void reportsBindEligibility() throws Exception {
        when(client.bindEligibility(TENANT, CONTRACT_ID)).thenReturn(new ContractResources.BindEligibility(
                CONTRACT_ID, "XOL-2026-00091", "PLACED", false,
                List.of(new ContractResources.BindCheck("CONTRACT_PLACED", true, "Contract is PLACED"),
                        new ContractResources.BindCheck("SHARES_BALANCED", false,
                                "layer 1 shares total 80.000000% (must total 100 +/- 0.0001)"))));

        mvc.perform(get("/api/v1/contracts/{id}/bind-eligibility", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindable").value(false))
                .andExpect(jsonPath("$.checks.length()").value(2))
                .andExpect(jsonPath("$.checks[1].code").value("SHARES_BALANCED"))
                .andExpect(jsonPath("$.checks[1].passed").value(false));
    }

    @Test
    void bindsContract() throws Exception {
        when(client.bind(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.Bind.class))).thenReturn(
                new ContractResources.BindResult(contract("BOUND"),
                        new ContractResources.BindEligibility(CONTRACT_ID, "XOL-2026-00091", "PLACED",
                                true, List.of(new ContractResources.BindCheck("CONTRACT_PLACED", true,
                                "Contract is PLACED")))));

        mvc.perform(post("/api/v1/contracts/{id}/bind", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\": \"" + APPROVER + "\", \"expectedVersion\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract.status").value("BOUND"))
                .andExpect(jsonPath("$.eligibility.bindable").value(true));
    }

    @Test
    void rejectsBindWithoutApprover() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/bind", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("approvedBy"));

        verifyNoInteractions(client);
    }

    @Test
    void mapsBindPreconditionConflictFromTheExpert() throws Exception {
        when(client.bind(eq(TENANT), eq(CONTRACT_ID), any(ContractCommands.Bind.class)))
                .thenThrow(new ContractConflictException("BIND_PRECONDITION_FAILED",
                        "Contract cannot bind: SHARES_BALANCED, REQUIRED_TERMS_PRESENT"));

        mvc.perform(post("/api/v1/contracts/{id}/bind", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\": \"" + APPROVER + "\", \"expectedVersion\": 2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIND_PRECONDITION_FAILED"));
    }

    // --------------------------------------------------------------- failures

    @Test
    void requiresTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/contracts/{id}/layers", CONTRACT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT"));

        verifyNoInteractions(client);
    }

    @Test
    void mapsUnknownResourceToNotFound() throws Exception {
        when(client.findLayers(TENANT, CONTRACT_ID))
                .thenThrow(new ResourceNotFoundException("Contract 22222222 was not found"));

        mvc.perform(get("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void mapsExpertOutageToBadGateway() throws Exception {
        when(client.findLayers(TENANT, CONTRACT_ID))
                .thenThrow(new ExpertIntegrationException("expert down", new RuntimeException("refused")));

        mvc.perform(get("/api/v1/contracts/{id}/layers", CONTRACT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXPERT_UNAVAILABLE"));
    }
}
