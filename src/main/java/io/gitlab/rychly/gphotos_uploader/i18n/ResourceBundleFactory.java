package io.gitlab.rychly.gphotos_uploader.i18n;

import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class ResourceBundleFactory {
    public static final String MESSAGES_RESOURCE_BUNDLE_NAME = "messages";
    public static final Locale DEFAULT_LOCALE = Locale.getDefault();
    public static final Locale FALLBACK_LOCALE = Locale.US;
    public static final String RESOURCE_ENCODING = "UTF-8";
    public static final String RESOURCE_SUFFIX = "properties";
    private static final Locale MESSAGES_LOCALE;
    private static final ResourceBundle MESSAGES_RESOURCE_BUNDLE;

    static {
        ResourceBundle resourceBundle;
        Locale locale;
        try {
            locale = DEFAULT_LOCALE;
            resourceBundle = ResourceBundle.getBundle(
                    MESSAGES_RESOURCE_BUNDLE_NAME, locale, new ResourceEncodingControl());
        } catch (MissingResourceException e) {
            locale = FALLBACK_LOCALE;
            resourceBundle = ResourceBundle.getBundle(
                    MESSAGES_RESOURCE_BUNDLE_NAME, locale, new ResourceEncodingControl());
        }
        MESSAGES_LOCALE = locale;
        MESSAGES_RESOURCE_BUNDLE = resourceBundle;
    }

    @Contract(pure = true)
    public static Locale getMessagesLocale() {
        return MESSAGES_LOCALE;
    }

    @Contract(pure = true)
    public static ResourceBundle getMessagesResourceBundle() {
        return MESSAGES_RESOURCE_BUNDLE;
    }

    public static String msg(String key, Object... args) {
        String message;
        try {
            message = MESSAGES_RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            message = key;
        }
        return String.format(message, args);
    }

    public static class ResourceEncodingControl extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(
                String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IOException {
            final String bundleName = toBundleName(baseName, locale);
            final String resourceName = toResourceName(bundleName, RESOURCE_SUFFIX);
            ResourceBundle bundle = null;
            InputStream stream = null;
            if (reload) {
                final URL url = loader.getResource(resourceName);
                if (url != null) {
                    final URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream != null) {
                try {
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, RESOURCE_ENCODING));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }
}
