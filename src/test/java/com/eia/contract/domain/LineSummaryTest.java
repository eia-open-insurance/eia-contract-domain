package com.eia.contract.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineSummaryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportsMissingBucketsAsZeroAndAnEmptyDetailMap() {
        LineSummary.Totals totals = new LineSummary(List.of(), null).totals();

        assertThat(totals.premium()).isEqualByComparingTo("0");
        assertThat(totals.loss()).isEqualByComparingTo("0");
        assertThat(totals.commission()).isEqualByComparingTo("0");
        assertThat(totals.other()).isEqualByComparingTo("0");
        assertThat(totals.details()).isEmpty();
    }

    @Test
    void keepsTheFourBucketConstructorWithoutADetailMap() {
        LineSummary.Totals totals = new LineSummary.Totals(
                new BigDecimal("100.0000"), new BigDecimal("60.0000"),
                new BigDecimal("3.0000"), new BigDecimal("13.5000"));

        assertThat(totals.details()).isEmpty();
        assertThat(totals.other()).isEqualByComparingTo("13.5000");
    }

    @Test
    void serializesTheDetailMapAsDecimalStrings() throws Exception {
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        details.put("PREMIUM", new BigDecimal("100.0000"));
        details.put("PAID_LOSS", new BigDecimal("40.0000"));
        details.put("IBNR", BigDecimal.ZERO);
        LineSummary summary = new LineSummary(List.of(), new LineSummary.Totals(
                new BigDecimal("100.0000"), new BigDecimal("47.0000"),
                BigDecimal.ZERO, BigDecimal.ZERO, details));

        JsonNode body = mapper.readTree(mapper.writeValueAsString(summary));
        JsonNode serialized = body.path("totals").path("details");

        assertThat(body.path("totals").path("premium").asText()).isEqualTo("100.0000");
        assertThat(serialized.path("PREMIUM").isTextual()).isTrue();
        assertThat(serialized.path("PREMIUM").asText()).isEqualTo("100.0000");
        assertThat(serialized.path("PAID_LOSS").asText()).isEqualTo("40.0000");
        assertThat(serialized.path("IBNR").asText()).isEqualTo("0");
    }

    @Test
    void omitsAnEmptyDetailMap() throws Exception {
        LineSummary summary = new LineSummary(List.of(), new LineSummary.Totals(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        String json = mapper.writeValueAsString(summary);

        assertThat(json).doesNotContain("details");
    }

    @Test
    void readsTheSerializedBodyBack() throws Exception {
        LineSummary summary = new LineSummary(List.of(), new LineSummary.Totals(
                new BigDecimal("100.0000"), new BigDecimal("60.0000"),
                new BigDecimal("5.0000"), new BigDecimal("3.5000"),
                Map.of("PAID_LOSS", new BigDecimal("40.0000"))));

        LineSummary read = mapper.readValue(mapper.writeValueAsString(summary), LineSummary.class);

        assertThat(read.totals().premium()).isEqualByComparingTo("100.0000");
        assertThat(read.totals().details().get("PAID_LOSS")).isEqualByComparingTo("40.0000");
    }

    @Test
    void freezesTheDetailMap() {
        LineSummary.Totals totals = new LineSummary.Totals(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new LinkedHashMap<>(Map.of("PAID_LOSS", new BigDecimal("40.0000"))));

        assertThat(totals.details()).containsEntry("PAID_LOSS", new BigDecimal("40.0000"));
        assertThatThrownBy(() -> totals.details().put("TAX", BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
