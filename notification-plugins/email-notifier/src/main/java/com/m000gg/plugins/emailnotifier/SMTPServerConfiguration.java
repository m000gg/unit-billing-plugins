package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.settings.PluginSettings;

public record SMTPServerConfiguration(
        String host,
        int port,
        String from,
        String username,
        String password, boolean startTlsEnabled
) {
    public static SMTPServerConfiguration from(PluginContext context) {
        PluginSettings settings = context.settings();

        String host = settings.get("host").orElse("").trim();
        String portValue = settings.get("port").orElse("").trim();
        String from = settings.get("from").orElse("").trim();
        String username = settings.get("username").orElse(null);
        String password = settings.get("password").orElse(null);
        String startTlsValue = settings.get("starttls").orElse("false").trim();

        if (host.isEmpty() || portValue.isEmpty() || from.isEmpty()) {
            throw new IllegalArgumentException(
                    "SMTP host, port and from must be configured"
            );
        }

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SMTP port must be a number", e);
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "SMTP port must be between 1 and 65535"
            );
        }

        username = username == null || username.isBlank() ? null : username.trim();
        password = password == null || password.isBlank() ? null : password;

        if ((username == null) != (password == null)) {
            throw new IllegalArgumentException(
                    "SMTP username and password must both be configured, or both be empty"
            );
        }

        if (!startTlsValue.equalsIgnoreCase("true")
                && !startTlsValue.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("SMTP starttls must be true or false");
        }

        return new SMTPServerConfiguration(
                host,
                port,
                from,
                username,
                password,
                Boolean.parseBoolean(startTlsValue)
        );
    }
}
