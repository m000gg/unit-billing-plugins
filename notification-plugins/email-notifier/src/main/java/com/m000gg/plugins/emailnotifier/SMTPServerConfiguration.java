package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.settings.PluginSettings;

public record SMTPServerConfiguration(String host, int portValue, String from, String username, String password) {
    public static SMTPServerConfiguration from(PluginContext context) {
        PluginSettings settings = context.settings();
        String host = settings.get("host").orElse(null);
        String portValue = settings.get("port").orElse(null);
        String from = settings.get("from").orElse(null);
        String username = settings.get("username").orElse(null);
        String password = settings.get("password").orElse(null);

        if (host == null || portValue == null || from == null) {
            throw new IllegalArgumentException("SMTP host, port and from must be configured");
        }

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SMTP port must be a number");
        }

        return new SMTPServerConfiguration(host, port, from, username, password);
    }

}
