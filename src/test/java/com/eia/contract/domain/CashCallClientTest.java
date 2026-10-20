package com.eia.contract.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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

class CashCallClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID CASH_CALL_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final String BASE = "http://expert.test";
    private static final String CASH_CALLS =
            BASE + "/internal/v1/contracts/" + CONTRACT_ID + "/cash-calls";
    private static final String STATUS = CASH_CALLS + "/" + CASH_CALL_ID + "/status";

    @Test
    void listsCashCallsForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(CASH_CALLS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CashCallClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(CashCallClient.BOOK_HEADER, "ASSUMED"))
                .andRespond(withSuccess("[" + cashCallJson() + "]", MediaType.APPLICATION_JSON));

        List<CashCall> cashCalls = client.findAll(TENANT, CONTRACT_ID);

        assertThat(cashCalls).hasSize(1);
        CashCall cashCall = cashCalls.getFirst();
        assertThat(cashCall.cashCallId()).isEqualTo(CASH_CALL_ID);
        assertThat(cashCall.callNumber()).isEqualTo("CC-2026-00042");
        assertThat(cashCall.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(cashCall.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(cashCall.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(cashCall.amount()).isEqualByComparingTo("250000.0000");
        assertThat(cashCall.currency()).isEqualTo("USD");
        assertThat(cashCall.dueDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(cashCall.status()).isEqualTo("REQUESTED");
        assertThat(cashCall.notes()).isEqualTo("Large loss funding");
        assertThat(cashCall.createdAt()).isEqualTo(Instant.parse("2026-04-01T09:30:00Z"));
        server.verify();
    }

    @Test
    void createsCashCallForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(CASH_CALLS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(CashCallClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(CashCallClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                         "amount": 250000.0000, "currency": "USD",
                         "dueDate": "2026-04-15", "notes": "Large loss funding"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(cashCallJson()));

        CashCall cashCall = client.create(TENANT, CONTRACT_ID,
                new CreateCashCallCommand(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31), new BigDecimal("250000.0000"),
                        "USD", LocalDate.of(2026, 4, 15), "Large loss funding"));

        assertThat(cashCall.callNumber()).isEqualTo("CC-2026-00042");
        assertThat(cashCall.status()).isEqualTo("REQUESTED");
        server.verify();
    }

    @Test
    void updatesStatusForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(CashCallClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(CashCallClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"status": "ISSUED"}
                        """))
                .andRespond(withSuccess(cashCallJson().replace("\"REQUESTED\"", "\"ISSUED\""),
                        MediaType.APPLICATION_JSON));

        CashCall cashCall = client.updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID,
                new UpdateCashCallStatusCommand("ISSUED"));

        assertThat(cashCall.status()).isEqualTo("ISSUED");
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(CASH_CALLS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "INVALID_AMOUNT",
                                 "message": "amount must be greater than zero"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID,
                new CreateCashCallCommand(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31), new BigDecimal("0"), "USD",
                        LocalDate.of(2026, 4, 15), null)))
                .isInstanceOf(ContractValidationException.class)
                .satisfies(ex -> {
                    ContractValidationException validation = (ContractValidationException) ex;
                    assertThat(validation.code()).isEqualTo("INVALID_AMOUNT");
                    assertThat(validation.getMessage()).contains("greater than zero");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "NOT_FOUND",
                                 "message": "Cash call cccccccc was not found"}
                                """));

        assertThatThrownBy(() -> client.updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID,
                new UpdateCashCallStatusCommand("ISSUED")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("cccccccc");
        server.verify();
    }

    @Test
    void preservesTheExpertConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "INVALID_STATUS_TRANSITION",
                                 "message": "Cash-call status cannot move from SETTLED to ISSUED"}
                                """));

        assertThatThrownBy(() -> client.updateStatus(TENANT, CONTRACT_ID, CASH_CALL_ID,
                new UpdateCashCallStatusCommand("ISSUED")))
                .isInstanceOf(ContractConflictException.class)
                .satisfies(ex -> {
                    ContractConflictException conflict = (ContractConflictException) ex;
                    assertThat(conflict.code()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(conflict.getMessage()).contains("SETTLED to ISSUED");
                });
        server.verify();
    }

    @Test
    void mapsAnExpertOutageToAnIntegrationFailure() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashCallClient client = new CashCallClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(CASH_CALLS + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findAll(TENANT, CONTRACT_ID))
                .isInstanceOf(ExpertIntegrationException.class)
                .hasMessageContaining("status 503");
        server.verify();
    }

    private static String cashCallJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "contractId": "%s",
                  "callNumber": "CC-2026-00042",
                  "periodStart": "2026-01-01",
                  "periodEnd": "2026-03-31",
                  "amount": "250000.0000",
                  "currency": "USD",
                  "dueDate": "2026-04-15",
                  "status": "REQUESTED",
                  "notes": "Large loss funding",
                  "createdAt": "2026-04-01T09:30:00Z"
                }
                """.formatted(CASH_CALL_ID, TENANT, CONTRACT_ID);
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
