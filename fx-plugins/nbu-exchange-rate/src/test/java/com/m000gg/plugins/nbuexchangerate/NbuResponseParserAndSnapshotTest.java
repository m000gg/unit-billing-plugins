package com.m000gg.plugins.nbuexchangerate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NbuResponseParserAndSnapshotTest {

    @Test
    void parsesNbuExchangeSiteJsonIncludingMultiUnitCurrenciesAndComputesCrossRates() {
        String json = """
                [
                  {
                    "exchangedate": "19.11.2026",
                    "r030": 840,
                    "cc": "USD",
                    "txt": "Долар США",
                    "enname": "US Dollar",
                    "rate": 41.5000,
                    "units": 1,
                    "rate_per_unit": 41.5000,
                    "group": "1",
                    "special": "N"
                  },
                  {
                    "exchangedate": "19.11.2026",
                    "r030": 978,
                    "cc": "EUR",
                    "txt": "Євро",
                    "enname": "Euro",
                    "rate": 44.2000,
                    "units": 1,
                    "rate_per_unit": 44.2000
                  },
                  {
                    "exchangedate": "19.11.2026",
                    "r030": 826,
                    "cc": "GBP",
                    "txt": "Фунт \\"стерлінгів\\"",
                    "rate": 52.1000,
                    "units": 1,
                    "rate_per_unit": 52.1000
                  },
                  {
                    "r030": 398,
                    "cc": "KZT",
                    "rate": 10.1657,
                    "units": 100
                  },
                  {
                    "cc": null,
                    "rate_per_unit": 1.0
                  }
                ]
                """;

        Map<String, BigDecimal> parsed = NbuResponseParser.parseUahPerUnit(json);
        assertThat(parsed)
                .containsEntry("USD", new BigDecimal("41.5000"))
                .containsEntry("EUR", new BigDecimal("44.2000"))
                .containsEntry("GBP", new BigDecimal("52.1000"));
        assertThat(parsed.get("KZT")).isEqualByComparingTo("0.101657");

        NbuRatesSnapshot snapshot = new NbuRatesSnapshot(parsed, Instant.parse("2026-11-19T20:00:00Z"));
        assertThat(snapshot.convert("USD", "UAH").orElseThrow()).isEqualByComparingTo("41.5000");
        assertThat(snapshot.convert("UAH", "USD").orElseThrow()).isEqualByComparingTo("0.024096");
        assertThat(snapshot.convert("GBP", "EUR").orElseThrow()).isEqualByComparingTo("1.178733");
        assertThat(snapshot.convert("EUR", "KZT").orElseThrow()).isEqualByComparingTo("434.795440");
        assertThat(snapshot.convert("GBP", "GBP")).contains(BigDecimal.ONE);
        assertThat(snapshot.convert("GBP", "XYZ")).isEmpty();
    }

    @Test
    void rejectsMalformedOrEmptyNbuResponses() {
        assertThatIllegalArgumentException().isThrownBy(() -> NbuResponseParser.parseUahPerUnit(""));
        assertThatIllegalArgumentException().isThrownBy(() -> NbuResponseParser.parseUahPerUnit("[]"));
        assertThatIllegalArgumentException().isThrownBy(() -> NbuResponseParser.parseUahPerUnit("[{\"cc\":\"USD\",\"rate\":0}]"));
        assertThatIllegalArgumentException().isThrownBy(() -> NbuResponseParser.parseUahPerUnit("<html>error</html>"));
    }
}
