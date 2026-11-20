package com.m000gg.plugins.nbuexchangerate;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Unit Billing plugin supplying official National Bank of Ukraine (NBU) exchange rates
 * via {@link com.m000gg.plugin.api.spi.fx.ExchangeRateProvider}.
 */
public final class NbuExchangeRatePlugin implements Plugin {

    public static final String PLUGIN_ID = "nbu-exchange-rate";
    private static final Logger log = LoggerFactory.getLogger(NbuExchangeRatePlugin.class);

    private final Function<NbuExchangeRateSettings, NbuRateHttpClient> clientFactory;
    private CachedNbuExchangeRateProvider provider;

    public NbuExchangeRatePlugin() {
        this(NbuRateHttpClient::new);
    }

    NbuExchangeRatePlugin(Function<NbuExchangeRateSettings, NbuRateHttpClient> clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
    }

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public void onEnable(PluginContext context) {
        Objects.requireNonNull(context, "context must not be null");

        NbuExchangeRateSettings settings = NbuExchangeRateSettings.from(context);
        NbuRateHttpClient httpClient = clientFactory.apply(settings);
        CachedNbuExchangeRateProvider rateProvider = new CachedNbuExchangeRateProvider(settings, httpClient);

        rateProvider.start();
        try {
            context.registerExchangeRateProvider(rateProvider);
        } catch (RuntimeException | Error e) {
            rateProvider.stop();
            throw e;
        }

        this.provider = rateProvider;
        log.info("NBU exchange rate plugin enabled (apiUrl={}, refreshInterval={}s, cacheTtl={}s)",
                settings.apiUri(), settings.refreshInterval().toSeconds(), settings.cacheTtl().toSeconds());
    }

    @Override
    public void onDisable() {
        CachedNbuExchangeRateProvider current = this.provider;
        this.provider = null;
        if (current != null) {
            current.stop();
        }
        log.info("NBU exchange rate plugin disabled");
    }
}
