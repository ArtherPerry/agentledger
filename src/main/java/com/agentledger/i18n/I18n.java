package com.agentledger.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/** Single source of UI strings (UTF-8 properties so Burmese stays readable in the file). */
public final class I18n {

    /** Forces .properties to be read as UTF-8 instead of the legacy ISO-8859-1. */
    private static final ResourceBundle.Control UTF8 = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload)
                throws IOException {
            String resource = toResourceName(toBundleName(baseName, locale), "properties");
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in == null) return null;
                return new PropertyResourceBundle(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        }
    };

    private static ResourceBundle bundle =
            ResourceBundle.getBundle("i18n.strings", new Locale("my"), UTF8);

    private I18n() {}

    public static void setLocale(String lang) {
        bundle = ResourceBundle.getBundle("i18n.strings", new Locale(lang), UTF8);
    }

    public static String t(String key) {
        try { return bundle.getString(key); }
        catch (Exception e) { return key; }
    }

    public static String t(String key, Object... args) {
        return MessageFormat.format(t(key), args);
    }

    /** The current bundle — used by FXMLLoader for %key lookups in FXML. */
    public static java.util.ResourceBundle bundle() {
        return bundle;
    }
}