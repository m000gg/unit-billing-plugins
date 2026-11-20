package com.m000gg.plugins.nbuexchangerate;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.fx.ExchangeRate;
import com.m000gg.plugin.api.query.fx.ExchangeRateDirectory;
import com.m000gg.plugin.api.query.settings.PluginSettings;
import com.m000gg.plugin.api.query.user.UserDirectory;
import com.m000gg.plugin.api.spi.fx.ExchangeRateProvider;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NbuExchangeRatePluginIntegrationTest {

    @Test
    void pluginRegistersProviderFetchesRatesViaHttpClientAndShutsDownCleanly() throws Exception {
        String body = """
                [
                  {"cc":"USD","rate":41.5000,"units":1,"rate_per_unit":41.5000},
                  {"cc":"EUR","rate":44.2000,"units":1,"rate_per_unit":44.2000},
                  {"cc":"GBP","rate":52.1000,"units":1,"rate_per_unit":52.1000}
                ]
                """;
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        HttpClient fakeHttp = new StubHttpClient(request -> {
            capturedRequest.set(request);
            return new StubResponse(200, body);
        });

        NbuExchangeRatePlugin plugin = new NbuExchangeRatePlugin(settings -> new NbuRateHttpClient(fakeHttp, settings));
        AtomicReference<ExchangeRateProvider> registered = new AtomicReference<>();
        Map<String, String> settingsMap = Map.of(
                "api-url", "https://rates.example.test/nbu?json",
                "refresh-interval-seconds", "60",
                "connect-timeout-ms", "1000",
                "request-timeout-ms", "1500",
                "cache-ttl-seconds", "300"
        );

        try {
            plugin.onEnable(stubContext(settingsMap, registered));
            assertThat(plugin.getId()).isEqualTo("nbu-exchange-rate");
            assertThat(registered.get()).isNotNull();

            ExchangeRate quote = awaitRate(registered.get(), "GBP", "EUR", Duration.ofSeconds(3));
            assertThat(quote.rate()).isEqualByComparingTo("1.178733");
            assertThat(quote.source()).isEqualTo("nbu");
            assertThat(capturedRequest.get().uri().toString()).isEqualTo("https://rates.example.test/nbu?json");
            assertThat(capturedRequest.get().method()).isEqualTo("GET");
            assertThat(capturedRequest.get().headers().firstValue("Accept")).contains("application/json");
            assertThat(capturedRequest.get().timeout()).contains(Duration.ofMillis(1500));
        } finally {
            plugin.onDisable();
        }
    }

    @Test
    void httpClientThrowsOnNon200Status() {
        NbuExchangeRateSettings settings = NbuExchangeRateSettings.from(stubContext(Map.of(), new AtomicReference<>()));
        NbuRateHttpClient client = new NbuRateHttpClient(
                new StubHttpClient(req -> new StubResponse(503, "Service Unavailable")),
                settings
        );

        assertThatIOException().isThrownBy(client::fetchJson)
                .withMessageContaining("503");
    }

    @Test
    void validatesSettingsAndServiceLoaderDescriptor() {
        assertThat(ServiceLoader.load(Plugin.class))
                .anySatisfy(p -> assertThat(p).isInstanceOf(NbuExchangeRatePlugin.class));

        NbuExchangeRateSettings defaults = NbuExchangeRateSettings.from(stubContext(Map.of(), new AtomicReference<>()));
        assertThat(defaults.apiUri().toString()).isEqualTo(NbuExchangeRateSettings.DEFAULT_API_URL);
        assertThat(defaults.refreshInterval()).isEqualTo(Duration.ofSeconds(3600));
        assertThat(defaults.cacheTtl()).isEqualTo(Duration.ofSeconds(86400));

        assertThatIllegalArgumentException().isThrownBy(() ->
                NbuExchangeRateSettings.from(stubContext(Map.of("api-url", "ftp://invalid"), new AtomicReference<>())));
        assertThatIllegalArgumentException().isThrownBy(() ->
                NbuExchangeRateSettings.from(stubContext(Map.of("cache-ttl-seconds", "0"), new AtomicReference<>())));
        assertThatIllegalArgumentException().isThrownBy(() ->
                NbuExchangeRateSettings.from(stubContext(Map.of("request-timeout-ms", "abc"), new AtomicReference<>())));
    }

    private static ExchangeRate awaitRate(
            ExchangeRateProvider provider,
            String from,
            String to,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<ExchangeRate> found = provider.findRate(from, to);
            if (found.isPresent()) {
                return found.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for cached rate " + from + " -> " + to);
    }

    private static PluginContext stubContext(
            Map<String, String> settingsMap,
            AtomicReference<ExchangeRateProvider> registeredProvider
    ) {
        PluginSettings settings = key -> Optional.ofNullable(settingsMap.get(key));
        UserDirectory users = id -> Optional.empty();
        ExchangeRateDirectory directory = new ExchangeRateDirectory() {
            @Override
            public String baseCurrency() {
                return "USD";
            }

            @Override
            public Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency) {
                return Optional.empty();
            }
        };

        return (PluginContext) Proxy.newProxyInstance(
                PluginContext.class.getClassLoader(),
                new Class<?>[]{PluginContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "settings" -> settings;
                    case "users" -> users;
                    case "exchangeRates" -> directory;
                    case "registerExchangeRateProvider" -> {
                        registeredProvider.set((ExchangeRateProvider) args[0]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    @FunctionalInterface
    private interface Responder {
        HttpResponse<String> respond(HttpRequest request) throws IOException;
    }

    private static final class StubHttpClient extends HttpClient {
        private final Responder responder;

        private StubHttpClient(Responder responder) {
            this.responder = responder;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            return (HttpResponse<T>) responder.respond(request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private record StubResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a, b) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://rates.example.test/nbu?json");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
