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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TechnicalAccountLineClientTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID CONTRACT_ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID LINE_ID = UUID.fromString("bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb");
    private static final String BASE = "http://expert.test";
    private static final String LINES = BASE + "/internal/v1/contracts/" + CONTRACT_ID
            + "/technical-accounts/" + ACCOUNT_ID + "/lines";
    private static final String LINE = LINES + "/" + LINE_ID;

    @Test
    void listsLinesForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(TechnicalAccountLineClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountLineClient.BOOK_HEADER, "ASSUMED"))
                .andRespond(withSuccess(summaryJson(), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.items()).hasSize(1);
        TechnicalAccountLine line = summary.items().getFirst();
        assertThat(line.lineId()).isEqualTo(LINE_ID);
        assertThat(line.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(line.lineType()).isEqualTo("PREMIUM");
        assertThat(line.reference()).isEqualTo("BOR-2026-0001");
        assertThat(line.description()).isEqualTo("Ceded premium Q1");
        assertThat(line.amount()).isEqualByComparingTo("1000000.0000");
        assertThat(line.createdAt()).isEqualTo(Instant.parse("2026-04-01T09:35:00Z"));
        assertThat(summary.totals().premium()).isEqualByComparingTo("1000000.0000");
        assertThat(summary.totals().loss()).isEqualByComparingTo("0");
        assertThat(summary.totals().commission()).isEqualByComparingTo("0");
        assertThat(summary.totals().other()).isEqualByComparingTo("0");
        server.verify();
    }

    @Test
    void readsThePublicFieldNamesFromTheExpertProjection() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s", "lines": [{
                          "lineId": "%s",
                          "technicalAccountId": "%s",
                          "lineType": "COMMISSION",
                          "sourceRef": "BOR-2026-0002",
                          "description": "Ceding commission",
                          "amount": "30000.0000",
                          "createdAt": "2026-04-01T09:36:00Z"
                        }], "totals": {"PREMIUM": "0.0000", "LOSS": "0.0000",
                                       "COMMISSION": "30000.0000", "OTHER": "0.0000"}}
                        """.formatted(ACCOUNT_ID, LINE_ID, ACCOUNT_ID), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        TechnicalAccountLine line = summary.items().getFirst();
        assertThat(line.lineId()).isEqualTo(LINE_ID);
        assertThat(line.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(line.reference()).isEqualTo("BOR-2026-0002");
        assertThat(summary.totals().commission()).isEqualByComparingTo("30000.0000");
        server.verify();
    }

    @Test
    void createsLineForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TechnicalAccountLineClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountLineClient.BOOK_HEADER, "ASSUMED"))
                .andExpect(content().json("""
                        {"lineType": "PREMIUM", "reference": "BOR-2026-0001",
                         "description": "Ceded premium Q1", "amount": 1000000.0000}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(lineJson("PREMIUM", "1000000.0000")));

        TechnicalAccountLine line = client.create(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new CreateTechnicalAccountLineCommand("PREMIUM", "BOR-2026-0001",
                        "Ceded premium Q1", new BigDecimal("1000000.0000")));

        assertThat(line.lineId()).isEqualTo(LINE_ID);
        assertThat(line.lineType()).isEqualTo("PREMIUM");
        server.verify();
    }

    @Test
    void deletesLineForwardingTenantAndAssumedBook() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header(TechnicalAccountLineClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(header(TechnicalAccountLineClient.BOOK_HEADER, "ASSUMED"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.delete(TENANT, CONTRACT_ID, ACCOUNT_ID, LINE_ID);

        server.verify();
    }

    @Test
    void derivesMissingTotalsFromTheLines() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s", "lines": [
                          %s,
                          {"lineType": "REINSTATEMENT", "amount": "5.0000"},
                          {"lineType": "PAID_LOSS", "amount": "40.0000"},
                          {"lineType": "OUTSTANDING_LOSS", "amount": "10.0000"},
                          {"lineType": "IBNR", "amount": "7.0000"},
                          {"lineType": "ALAE", "amount": "3.0000"},
                          {"lineType": "COMMISSION", "amount": "3.0000"},
                          {"lineType": "BROKERAGE", "amount": "2.0000"},
                          {"lineType": "TAX", "amount": "1.0000"},
                          {"lineType": "ADJUSTMENT", "amount": "2.5000"}
                        ]}
                        """.formatted(ACCOUNT_ID, lineJson("PREMIUM", "100.0000")),
                        MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.items()).hasSize(10);
        assertThat(summary.totals().premium()).isEqualByComparingTo("100.0000");
        assertThat(summary.totals().loss()).isEqualByComparingTo("65.0000");
        assertThat(summary.totals().commission()).isEqualByComparingTo("5.0000");
        assertThat(summary.totals().other()).isEqualByComparingTo("3.5000");
        assertThat(summary.totals().details())
                .containsOnlyKeys("PREMIUM", "REINSTATEMENT", "PAID_LOSS",
                        "OUTSTANDING_LOSS", "IBNR", "ALAE", "COMMISSION", "BROKERAGE",
                        "TAX", "ADJUSTMENT");
        assertThat(summary.totals().details().get("PAID_LOSS")).isEqualByComparingTo("40.0000");
        assertThat(summary.totals().details().get("IBNR")).isEqualByComparingTo("7.0000");
        assertThat(summary.totals().details().get("ALAE")).isEqualByComparingTo("3.0000");
        server.verify();
    }

    @Test
    void mapsFineGrainedExpertTotalsIntoTheFourBuckets() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s", "lines": [],
                         "totals": {"PREMIUM": "100.0000", "PAID_LOSS": "40.0000",
                                    "IBNR": "7.0000", "COMMISSION": "3.0000",
                                    "BROKERAGE": "2.0000", "REINSTATEMENT": "5.0000",
                                    "TAX": "1.0000"}}
                        """.formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.items()).isEmpty();
        assertThat(summary.totals().premium()).isEqualByComparingTo("100.0000");
        assertThat(summary.totals().loss()).isEqualByComparingTo("52.0000");
        assertThat(summary.totals().commission()).isEqualByComparingTo("5.0000");
        assertThat(summary.totals().other()).isEqualByComparingTo("1.0000");
        server.verify();
    }

    @Test
    void mapsEveryDetailLineTypeIntoItsBucketAndKeepsTheDetailMap() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s", "lines": [],
                         "totals": {"PREMIUM": "100.0000", "COMMISSION": "3.0000",
                                    "BROKERAGE": "2.0000", "PAID_LOSS": "40.0000",
                                    "OUTSTANDING_LOSS": "10.0000", "IBNR": "7.0000",
                                    "ALAE": "3.0000", "REINSTATEMENT": "5.0000",
                                    "CASH_CALL": "1.0000", "FUNDS_WITHHELD": "1.5000",
                                    "TAX": "1.0000", "ADJUSTMENT": "2.5000",
                                    "OTHER": "0.5000"},
                         "bucketTotals": {"premium": "100.0000", "loss": "66.0000",
                                         "commission": "5.0000", "other": "5.5000"}}
                        """.formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);
        LineSummary.Totals totals = summary.totals();

        assertThat(totals.premium()).isEqualByComparingTo("100.0000");
        assertThat(totals.loss()).isEqualByComparingTo("66.0000");
        assertThat(totals.commission()).isEqualByComparingTo("5.0000");
        assertThat(totals.other()).isEqualByComparingTo("5.5000");
        assertThat(totals.details()).containsOnlyKeys(
                "PREMIUM", "COMMISSION", "BROKERAGE", "PAID_LOSS", "OUTSTANDING_LOSS",
                "IBNR", "ALAE", "REINSTATEMENT", "CASH_CALL", "FUNDS_WITHHELD", "TAX",
                "ADJUSTMENT", "OTHER");
        assertThat(totals.details().get("PREMIUM")).isEqualByComparingTo("100.0000");
        assertThat(totals.details().get("PAID_LOSS")).isEqualByComparingTo("40.0000");
        assertThat(totals.details().get("OUTSTANDING_LOSS")).isEqualByComparingTo("10.0000");
        assertThat(totals.details().get("IBNR")).isEqualByComparingTo("7.0000");
        assertThat(totals.details().get("ALAE")).isEqualByComparingTo("3.0000");
        assertThat(totals.details().get("COMMISSION")).isEqualByComparingTo("3.0000");
        assertThat(totals.details().get("BROKERAGE")).isEqualByComparingTo("2.0000");
        assertThat(totals.details().get("REINSTATEMENT")).isEqualByComparingTo("5.0000");
        assertThat(totals.details().get("CASH_CALL")).isEqualByComparingTo("1.0000");
        assertThat(totals.details().get("FUNDS_WITHHELD")).isEqualByComparingTo("1.5000");
        assertThat(totals.details().get("TAX")).isEqualByComparingTo("1.0000");
        assertThat(totals.details().get("ADJUSTMENT")).isEqualByComparingTo("2.5000");
        assertThat(totals.details().get("OTHER")).isEqualByComparingTo("0.5000");
        server.verify();
    }

    @Test
    void prefersTheExpertBucketTotalsOverTheLocalRollup() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s", "lines": [],
                         "totals": {"PREMIUM": "100.0000", "PAID_LOSS": "40.0000",
                                    "COMMISSION": "3.0000", "TAX": "1.0000"},
                         "bucketTotals": {"premium": "100.0000", "loss": "50.0000",
                                         "commission": "5.0000", "other": "2.0000"}}
                        """.formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.totals().premium()).isEqualByComparingTo("100.0000");
        assertThat(summary.totals().loss()).isEqualByComparingTo("50.0000");
        assertThat(summary.totals().commission()).isEqualByComparingTo("5.0000");
        assertThat(summary.totals().other()).isEqualByComparingTo("2.0000");
        assertThat(summary.totals().details())
                .containsEntry("PAID_LOSS", new BigDecimal("40.0000"))
                .containsEntry("TAX", new BigDecimal("1.0000"));
        server.verify();
    }

    @Test
    void mapsTheLegacyLossTypeIntoTheLossBucket() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s", "lines": [],
                         "totals": {"LOSS": "12.0000", "PAID_LOSS": "4.0000"}}
                        """.formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.totals().loss()).isEqualByComparingTo("16.0000");
        assertThat(summary.totals().details())
                .containsEntry("LOSS", new BigDecimal("12.0000"))
                .containsEntry("PAID_LOSS", new BigDecimal("4.0000"));
        server.verify();
    }

    @Test
    void derivesBucketsTheExpertOmittedFromTheLines() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("""
                        {"accountId": "%s",
                         "lines": [{"lineType": "PAID_LOSS", "amount": "40.0000"}],
                         "totals": {"PREMIUM": "100.0000"}}
                        """.formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.totals().premium()).isEqualByComparingTo("100.0000");
        assertThat(summary.totals().loss()).isEqualByComparingTo("40.0000");
        assertThat(summary.totals().commission()).isEqualByComparingTo("0");
        assertThat(summary.totals().other()).isEqualByComparingTo("0");
        assertThat(summary.totals().details())
                .containsEntry("PREMIUM", new BigDecimal("100.0000"))
                .containsEntry("PAID_LOSS", new BigDecimal("40.0000"));
        server.verify();
    }

    @Test
    void returnsAnEmptySummaryForAnEmptyExpertBody() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        LineSummary summary = client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID);

        assertThat(summary.items()).isEmpty();
        assertThat(summary.totals().premium()).isEqualByComparingTo("0");
        assertThat(summary.totals().loss()).isEqualByComparingTo("0");
        assertThat(summary.totals().commission()).isEqualByComparingTo("0");
        assertThat(summary.totals().other()).isEqualByComparingTo("0");
        assertThat(summary.totals().details()).isEmpty();
        server.verify();
    }

    @Test
    void preservesTheExpertValidationProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "INVALID_LINE_TYPE",
                                 "message": "Unknown technical-account line type: FEE"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new CreateTechnicalAccountLineCommand("FEE", null, null,
                        new BigDecimal("100"))))
                .isInstanceOf(ContractValidationException.class)
                .satisfies(ex -> {
                    ContractValidationException validation = (ContractValidationException) ex;
                    assertThat(validation.code()).isEqualTo("INVALID_LINE_TYPE");
                    assertThat(validation.getMessage()).contains("FEE");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertNotFoundProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINE + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "NOT_FOUND",
                                 "message": "Technical account line bbbbbbbb was not found"}
                                """));

        assertThatThrownBy(() -> client.delete(TENANT, CONTRACT_ID, ACCOUNT_ID, LINE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("bbbbbbbb");
        server.verify();
    }

    @Test
    void preservesTheExpertConflictProblem() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "ACCOUNT_NOT_DRAFT",
                                 "message": "Lines can only be recorded on a DRAFT technical account"}
                                """));

        assertThatThrownBy(() -> client.create(TENANT, CONTRACT_ID, ACCOUNT_ID,
                new CreateTechnicalAccountLineCommand("PREMIUM", null, null,
                        new BigDecimal("100"))))
                .isInstanceOf(ContractConflictException.class)
                .satisfies(ex -> {
                    ContractConflictException conflict = (ContractConflictException) ex;
                    assertThat(conflict.code()).isEqualTo("ACCOUNT_NOT_DRAFT");
                    assertThat(conflict.getMessage()).contains("DRAFT");
                });
        server.verify();
    }

    @Test
    void mapsAnExpertOutageToAnIntegrationFailure() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TechnicalAccountLineClient client = new TechnicalAccountLineClient(
                builder, new ObjectMapper(), BASE);

        server.expect(requestTo(LINES + "?tenantId=" + TENANT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findAll(TENANT, CONTRACT_ID, ACCOUNT_ID))
                .isInstanceOf(ExpertIntegrationException.class)
                .hasMessageContaining("status 503");
        server.verify();
    }

    private static String summaryJson() {
        return """
                {"accountId": "%s", "lines": [%s],
                 "totals": {"PREMIUM": "1000000.0000", "LOSS": "0.0000",
                            "COMMISSION": "0.0000", "OTHER": "0.0000"}}
                """.formatted(ACCOUNT_ID, lineJson("PREMIUM", "1000000.0000"));
    }

    private static String lineJson(String lineType, String amount) {
        return """
                {
                  "id": "%s",
                  "tenantId": "%s",
                  "accountId": "%s",
                  "lineType": "%s",
                  "reference": "BOR-2026-0001",
                  "description": "Ceded premium Q1",
                  "amount": "%s",
                  "createdAt": "2026-04-01T09:35:00Z"
                }
                """.formatted(LINE_ID, TENANT, ACCOUNT_ID, lineType, amount);
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
