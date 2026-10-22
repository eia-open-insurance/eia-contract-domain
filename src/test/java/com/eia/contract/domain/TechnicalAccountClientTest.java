package com.eia.contract.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TechnicalAccountClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final String BASE = "http://expert.test";
    private static final String ACCOUNTS =
            BASE + "/internal/v1/contracts/" + CONTRACT_ID + "/technical-accounts";
    private static final String STATUS = ACCOUNTS + "/" + ACCOUNT_ID + "/status";

    @Test
    void listsAccountsForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ACCOUNTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(TechnicalAccountClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountClient.BOOK_HEADER, "ASSUMED"))
                .andRespond(withSuccess("[" + accountJson() + "]", MediaType.APPLICATION_JSON));

        List<TechnicalAccount> accounts = client.findAll(TENANT, CONTRACT_ID);

        assertThat(accounts).hasSize(1);
        TechnicalAccount account = accounts.getFirst();
        assertThat(account.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(account.accountNumber()).isEqualTo("TA-2026-00077");
        assertThat(account.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(account.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(account.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(account.currency()).isEqualTo("USD");
        assertThat(account.cededPremium()).isEqualByComparingTo("400000.0000");
        assertThat(account.cededLosses()).isEqualByComparingTo("240000.0000");
        assertThat(account.commission()).isEqualByComparingTo("100000.0000");
        assertThat(account.otherCharges()).isEqualByComparingTo("5000.0000");
        assertThat(account.balance()).isEqualByComparingTo("355000.0000");
        assertThat(account.status()).isEqualTo("ISSUED");
        assertThat(account.settlementReference()).isNull();
        assertThat(account.createdAt()).isEqualTo(Instant.parse("2026-04-01T09:30:00Z"));
        server.verify();
    }

    @Test
    void createsAccountForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ACCOUNTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TechnicalAccountClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                         "currency": "USD", "cededPremium": 400000.0000,
                         "cededLosses": 240000.0000, "commission": 100000.0000,
                         "otherCharges": 5000.0000}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(accountJson()));

        TechnicalAccount account = client.create(TENANT, CONTRACT_ID,
                new CreateTechnicalAccountCommand(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31), "USD",
                        new BigDecimal("400000.0000"), new BigDecimal("240000.0000"),
                        new BigDecimal("100000.0000"), new BigDecimal("5000.0000")));

        assertThat(account.accountNumber()).isEqualTo("TA-2026-00077");
        assertThat(account.balance()).isEqualByComparingTo("355000.0000");
        server.verify();
    }

    @Test
    void updatesStatusForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(TechnicalAccountClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"status": "SETTLED"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(accountJson("SETTLED", null),
                        MediaType.APPLICATION_JSON));

        TechnicalAccount account = client.updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new UpdateTechnicalAccountStatusCommand("SETTLED"));

        assertThat(account.status()).isEqualTo("SETTLED");
        assertThat(account.settlementReference()).isNull();
        server.verify();
    }

    @Test
    void settlesTechnicalAccountForwardingTheSettlementReference() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(TechnicalAccountClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"status": "SETTLED", "reference": "STL-2026-0007"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(accountJson("SETTLED", "STL-2026-0007"),
                        MediaType.APPLICATION_JSON));

        TechnicalAccount account = client.updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new UpdateTechnicalAccountStatusCommand("SETTLED", "STL-2026-0007"));

        assertThat(account.status()).isEqualTo("SETTLED");
        assertThat(account.settlementReference()).isEqualTo("STL-2026-0007");
        server.verify();
    }

    @Test
    void omitsASettlementReferenceFromANonSettlementPayload() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("""
                        {"status": "ISSUED"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess(accountJson("ISSUED", null),
                        MediaType.APPLICATION_JSON));

        TechnicalAccount account = client.updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new UpdateTechnicalAccountStatusCommand("ISSUED", "STL-2026-0007"));

        assertThat(account.status()).isEqualTo("ISSUED");
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ACCOUNTS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "PERIOD_OVERLAP",
                                 "message": "Period 2026-01-01..2026-03-31 overlaps TA-2026-00076"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID,
                new CreateTechnicalAccountCommand(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31), "USD", null, null, null, null)))
                .isInstanceOf(ContractValidationException.class)
                .satisfies(ex -> {
                    ContractValidationException validation = (ContractValidationException) ex;
                    assertThat(validation.code()).isEqualTo("PERIOD_OVERLAP");
                    assertThat(validation.getMessage()).contains("overlaps TA-2026-00076");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "NOT_FOUND",
                                 "message": "Technical account aaaaaaaa was not found"}
                                """));

        assertThatThrownBy(() -> client.updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new UpdateTechnicalAccountStatusCommand("SETTLED")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("aaaaaaaa");
        server.verify();
    }

    @Test
    void preservesTheExpertConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "INVALID_ACCOUNT_TRANSITION",
                                 "message": "Technical account TA-2026-00077 cannot move from DRAFT to SETTLED"}
                                """));

        assertThatThrownBy(() -> client.updateStatus(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new UpdateTechnicalAccountStatusCommand("SETTLED")))
                .isInstanceOf(ContractConflictException.class)
                .satisfies(ex -> {
                    ContractConflictException conflict = (ContractConflictException) ex;
                    assertThat(conflict.code()).isEqualTo("INVALID_ACCOUNT_TRANSITION");
                    assertThat(conflict.getMessage()).contains("DRAFT to SETTLED");
                });
        server.verify();
    }

    @Test
    void mapsAnExpertOutageToAnIntegrationFailure() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountClient client = new TechnicalAccountClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(ACCOUNTS + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findAll(TENANT, CONTRACT_ID))
                .isInstanceOf(ExpertIntegrationException.class)
                .hasMessageContaining("status 503");
        server.verify();
    }

    private static String accountJson() {
        return accountJson("ISSUED", null);
    }

    private static String accountJson(String status, String settlementReference) {
        String reference = settlementReference == null
                ? "null"
                : "\"" + settlementReference + "\"";
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "contractId": "%s",
                  "accountNumber": "TA-2026-00077",
                  "periodStart": "2026-01-01",
                  "periodEnd": "2026-03-31",
                  "currency": "USD",
                  "cededPremium": "400000.0000",
                  "cededLosses": "240000.0000",
                  "commission": "100000.0000",
                  "otherCharges": "5000.0000",
                  "balance": "355000.0000",
                  "status": "%s",
                  "settlementReference": %s,
                  "createdAt": "2026-04-01T09:30:00Z"
                }
                """.formatted(ACCOUNT_ID, TENANT, CONTRACT_ID, status, reference);
    }

    private static RestClient.Builder builder() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return RestClient.builder()
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
                });
    }
}
