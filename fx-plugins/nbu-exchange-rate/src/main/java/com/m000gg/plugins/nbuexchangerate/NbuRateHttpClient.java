package com.m000gg.plugins.nbuexchangerate;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * Fetches raw JSON exchange rate feeds from NBU using JDK {@link HttpClient}.
 */
final class NbuRateHttpClient {

    private final HttpClient httpClient;
    private final NbuExchangeRateSettings settings;

    NbuRateHttpClient(NbuExchangeRateSettings settings) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Objects.requireNonNull(settings, "settings").connectTimeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                settings
        );
    }

    NbuRateHttpClient(HttpClient httpClient, NbuExchangeRateSettings settings) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    String fetchJson() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(settings.apiUri())
                .timeout(settings.requestTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("NBU API returned unexpected HTTP status " + response.statusCode());
        }
        return response.body();
    }
}
