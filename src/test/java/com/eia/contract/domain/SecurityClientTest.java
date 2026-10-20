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

class SecurityClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID SECURITY_ID = UUID.fromString("cccccccc-cccc-7ccc-8ccc-cccccccccccc");
    private static final UUID PROVIDER = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final String BASE = "http://expert.test";
    private static final String SECURITY =
            BASE + "/internal/v1/contracts/" + CONTRACT_ID + "/security";

    @Test
    void listsSecurityForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecurityClient client = new SecurityClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(SECURITY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(SecurityClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(SecurityClient.BOOK_HEADER, "ASSUMED"))
                .andRespond(withSuccess("[" + securityJson() + "]", MediaType.APPLICATION_JSON));

        List<SecurityRecord> securities = client.findAll(TENANT, CONTRACT_ID);

        assertThat(securities).hasSize(1);
        SecurityRecord security = securities.getFirst();
        assertThat(security.id()).isEqualTo(SECURITY_ID);
        assertThat(security.securityType()).isEqualTo("LETTER_OF_CREDIT");
        assertThat(security.requiredAmount()).isEqualByComparingTo("5000000.0000");
        assertThat(security.heldAmount()).isEqualByComparingTo("4500000.0000");
        assertThat(security.currency()).isEqualTo("USD");
        assertThat(security.providerPartyId()).isEqualTo(PROVIDER);
        assertThat(security.expiryDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(security.approvalRef()).isEqualTo("APR-2026-0007");
        assertThat(security.status()).isEqualTo("OUTSTANDING");
        assertThat(security.createdAt()).isEqualTo(Instant.parse("2026-01-05T09:30:00Z"));
        server.verify();
    }

    @Test
    void createsSecurityForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecurityClient client = new SecurityClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(SECURITY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(SecurityClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(SecurityClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"securityType": "LETTER_OF_CREDIT", "requiredAmount": 5000000,
                         "heldAmount": 4500000, "currency": "USD",
                         "providerPartyId": "%s", "expiryDate": "2027-03-31",
                         "approvalRef": "APR-2026-0007", "approvedAt": "2026-04-01T09:30:00Z"}
                        """.formatted(PROVIDER)))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(securityJson()));

        SecurityRecord security = client.create(TENANT, CONTRACT_ID,
                new CreateSecurityCommand("LETTER_OF_CREDIT", new BigDecimal("5000000"),
                        new BigDecimal("4500000"), "USD", PROVIDER, null, null, null,
                        LocalDate.of(2027, 3, 31), null, null, null, null,
                        "APR-2026-0007", Instant.parse("2026-04-01T09:30:00Z")));

        assertThat(security.securityType()).isEqualTo("LETTER_OF_CREDIT");
        assertThat(security.status()).isEqualTo("OUTSTANDING");
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecurityClient client = new SecurityClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(SECURITY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "INVALID_SECURITY_TYPE",
                                 "message": "Unknown securityType: DEPOSIT"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID,
                new CreateSecurityCommand("DEPOSIT", new BigDecimal("5000000"), null, "USD",
                        null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(ContractValidationException.class)
                .satisfies(ex -> {
                    ContractValidationException validation = (ContractValidationException) ex;
                    assertThat(validation.code()).isEqualTo("INVALID_SECURITY_TYPE");
                    assertThat(validation.getMessage()).contains("DEPOSIT");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecurityClient client = new SecurityClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(SECURITY + "?tenantId=" + TENANT))
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
        SecurityClient client = new SecurityClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(SECURITY + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "COLLATERAL_EXCEEDS_OBLIGATION",
                                 "message": "Held collateral 7500000.0000 exceeds the secured obligation 5000000.0000"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID,
                new CreateSecurityCommand("LETTER_OF_CREDIT", new BigDecimal("5000000"),
                        new BigDecimal("7500000"), "USD", null, null, null, null, null,
                        null, null, null, null, null, null)))
                .isInstanceOf(ContractConflictException.class)
                .satisfies(ex -> {
                    ContractConflictException conflict = (ContractConflictException) ex;
                    assertThat(conflict.code()).isEqualTo("COLLATERAL_EXCEEDS_OBLIGATION");
                    assertThat(conflict.getMessage()).contains("exceeds the secured obligation");
                });
        server.verify();
    }

    @Test
    void mapsAnExpertOutageToAnIntegrationFailure() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecurityClient client = new SecurityClient(builder, new ObjectMapper(), BASE);

        server.expect(requestTo(SECURITY + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findAll(TENANT, CONTRACT_ID))
                .isInstanceOf(ExpertIntegrationException.class)
                .hasMessageContaining("status 503");
        server.verify();
    }

    private static String securityJson() {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "contractId": "%s",
                  "securityType": "LETTER_OF_CREDIT",
                  "requiredAmount": "5000000.0000",
                  "heldAmount": "4500000.0000",
                  "currency": "USD",
                  "providerPartyId": "%s",
                  "beneficiaryPartyId": null,
                  "effectiveFrom": "2026-01-01",
                  "effectiveTo": null,
                  "expiryDate": "2027-03-31",
                  "status": "OUTSTANDING",
                  "collateralQuality": "CASH",
                  "documentId": null,
                  "releaseConditions": null,
                  "approvalRef": "APR-2026-0007",
                  "approvedAt": "2026-01-10T00:00:00Z",
                  "createdAt": "2026-01-05T09:30:00Z",
                  "updatedAt": "2026-01-05T09:30:00Z"
                }
                """.formatted(SECURITY_ID, TENANT, CONTRACT_ID, PROVIDER);
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
