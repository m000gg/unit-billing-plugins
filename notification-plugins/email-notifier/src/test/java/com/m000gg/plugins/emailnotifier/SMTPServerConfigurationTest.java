package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.settings.PluginSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SMTPServerConfigurationTest {

    private PluginContext context;
    private PluginSettings settings;

    @BeforeEach
    void setUp() {
        context = mock(PluginContext.class);
        settings = mock(PluginSettings.class);

        when(context.settings()).thenReturn(settings);
    }

    @Test
    void shouldReadValidSmtpSettings() {
        stubSettings(Map.of(
                "host", " smtp.example.com ",
                "port", "587",
                "from", " billing@example.com ",
                "username", "mailer",
                "password", "secret",
                "starttls", "true"
        ));

        SMTPServerConfiguration config = SMTPServerConfiguration.from(context);

        assertEquals("smtp.example.com", config.host());
        assertEquals(587, config.port());
        assertEquals("billing@example.com", config.from());
        assertEquals("mailer", config.username());
        assertEquals("secret", config.password());
        assertTrue(config.startTlsEnabled());
    }

    @Test
    void shouldDefaultStartTlsToFalse() {
        stubSettings(Map.of(
                "host", "localhost",
                "port", "1025",
                "from", "billing@example.com"
        ));

        SMTPServerConfiguration config = SMTPServerConfiguration.from(context);

        assertFalse(config.startTlsEnabled());
        assertNull(config.username());
        assertNull(config.password());
    }

    @Test
    void shouldRejectMissingRequiredSetting() {
        stubSettings(Map.of(
                "host", "localhost",
                "port", "1025"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> SMTPServerConfiguration.from(context)
        );
    }

    @Test
    void shouldRejectInvalidPort() {
        stubSettings(Map.of(
                "host", "localhost",
                "port", "not-a-number",
                "from", "billing@example.com"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> SMTPServerConfiguration.from(context)
        );
    }

    @Test
    void shouldRejectOnlyOneCredential() {
        stubSettings(Map.of(
                "host", "localhost",
                "port", "1025",
                "from", "billing@example.com",
                "username", "mailer"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> SMTPServerConfiguration.from(context)
        );
    }

    private void stubSettings(Map<String, String> values) {
        when(settings.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Optional.ofNullable(values.get(key));
        });
    }
}