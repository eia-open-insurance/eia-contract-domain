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

class BordereauClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID BORDEREAU_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID TECHNICAL_ACCOUNT_ID =
            UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final String BASE = "http://expert.test";
    private static final String BORDEREAUX =
            BASE + "/api/v1/contracts/" + CONTRACT_ID + "/bordereaux";
    private static final String STATUS = BORDEREAUX + "/" + BORDEREAU_ID + "/status";

    @Test
    void listsBordereauxForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BORDEREAUX + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(BordereauClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(BordereauClient.BOOK_HEADER, "ASSUMED"))
                .andRespond(withSuccess("[" + bordereauJson() + "]", MediaType.APPLICATION_JSON));

        List<Bordereau> bordereaux = client.findAll(TENANT, CONTRACT_ID);

        assertThat(bordereaux).hasSize(1);
        Bordereau bordereau = bordereaux.getFirst();
        assertThat(bordereau.bordereauId()).isEqualTo(BORDEREAU_ID);
        assertThat(bordereau.bordereauNumber()).isEqualTo("BRD-2026-000412");
        assertThat(bordereau.contractId()).isEqualTo(CONTRACT_ID);
        assertThat(bordereau.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(bordereau.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(bordereau.receivedDate()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(bordereau.currency()).isEqualTo("USD");
        assertThat(bordereau.premiumAmount()).isEqualByComparingTo("400000.0000");
        assertThat(bordereau.lossAmount()).isEqualByComparingTo("240000.0000");
        assertThat(bordereau.commissionAmount()).isEqualByComparingTo("100000.0000");
        assertThat(bordereau.otherChargesAmount()).isEqualByComparingTo("5000.0000");
        assertThat(bordereau.lineCount()).isEqualTo(10);
        assertThat(bordereau.notes()).isEqualTo("Q1 claims bordereau");
        assertThat(bordereau.status()).isEqualTo("RECEIVED");
        assertThat(bordereau.technicalAccountId()).isEqualTo(TECHNICAL_ACCOUNT_ID);
        assertThat(bordereau.createdAt()).isEqualTo(Instant.parse("2026-04-02T09:30:00Z"));
        server.verify();
    }

    @Test
    void createsBordereauForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BORDEREAUX + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(BordereauClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(BordereauClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"periodStart": "2026-01-01", "periodEnd": "2026-03-31",
                         "receivedDate": "2026-04-02", "currency": "USD",
                         "premiumAmount": 400000.0000, "lossAmount": 240000.0000,
                         "commissionAmount": 100000.0000,
                         "otherChargesAmount": 5000.0000,
                         "lineCount": 10, "notes": "Q1 claims bordereau"}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(bordereauJson()));

        Bordereau bordereau = client.create(TENANT, CONTRACT_ID,
                new CreateBordereauCommand(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 2), "USD",
                        new BigDecimal("400000.0000"), new BigDecimal("240000.0000"),
                        new BigDecimal("100000.0000"), new BigDecimal("5000.0000"),
                        10, "Q1 claims bordereau"));

        assertThat(bordereau.bordereauNumber()).isEqualTo("BRD-2026-000412");
        assertThat(bordereau.premiumAmount()).isEqualByComparingTo("400000.0000");
        server.verify();
    }

    @Test
    void updatesStatusForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(BordereauClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(BordereauClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"status": "PROCESSED", "notes": "Posted to TA-2026-00077"}
                        """))
                .andRespond(withSuccess(bordereauJson().replace("\"RECEIVED\"", "\"PROCESSED\""),
                        MediaType.APPLICATION_JSON));

        Bordereau bordereau = client.updateStatus(TENANT, CONTRACT_ID, BORDEREAU_ID,
                new UpdateBordereauStatusCommand("PROCESSED", "Posted to TA-2026-00077"));

        assertThat(bordereau.status()).isEqualTo("PROCESSED");
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BORDEREAUX + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "BORDEREAU_CURRENCY_MISMATCH",
                                 "message": "Bordereau currency EUR does not match contract currency USD"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID,
                new CreateBordereauCommand(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 2), "EUR",
                        null, null, null, null, 1, null)))
                .isInstanceOf(ContractValidationException.class)
                .satisfies(ex -> {
                    ContractValidationException validation = (ContractValidationException) ex;
                    assertThat(validation.code()).isEqualTo("BORDEREAU_CURRENCY_MISMATCH");
                    assertThat(validation.getMessage()).contains("does not match contract currency USD");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BORDEREAUX + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "NOT_FOUND",
                                 "message": "Contract 22222222 was not found"}
                                """));

        assertThatThrownBy(() -> client.findAll(TENANT, CONTRACT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("22222222");
        server.verify();
    }

    @Test
    void preservesTheExpertConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(STATUS + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "INVALID_STATUS_TRANSITION",
                                 "message": "Bordereau status cannot move from RECEIVED to PROCESSED"}
                                """));

        assertThatThrownBy(() -> client.updateStatus(TENANT, CONTRACT_ID, BORDEREAU_ID,
                new UpdateBordereauStatusCommand("PROCESSED", null)))
                .isInstanceOf(ContractConflictException.class)
                .satisfies(ex -> {
                    ContractConflictException conflict = (ContractConflictException) ex;
                    assertThat(conflict.code()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(conflict.getMessage()).contains("RECEIVED to PROCESSED");
                });
        server.verify();
    }

    @Test
    void mapsAnExpertOutageToAnIntegrationFailure() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BordereauClient client = new BordereauClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(BORDEREAUX + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findAll(TENANT, CONTRACT_ID))
                .isInstanceOf(ExpertIntegrationException.class)
                .hasMessageContaining("status 503");
        server.verify();
    }

    private static String bordereauJson() {
        return """
                {
                  "id": "%s",
                  "bordereauNumber": "BRD-2026-000412",
                  "contractId": "%s",
                  "periodStart": "2026-01-01",
                  "periodEnd": "2026-03-31",
                  "receivedDate": "2026-04-02",
                  "currency": "USD",
                  "premiumAmount": "400000.0000",
                  "lossAmount": "240000.0000",
                  "commissionAmount": "100000.0000",
                  "otherChargesAmount": "5000.0000",
                  "lineCount": 10,
                  "notes": "Q1 claims bordereau",
                  "status": "RECEIVED",
                  "technicalAccountId": "%s",
                  "createdAt": "2026-04-02T09:30:00Z"
                }
                """.formatted(BORDEREAU_ID, CONTRACT_ID, TECHNICAL_ACCOUNT_ID);
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
