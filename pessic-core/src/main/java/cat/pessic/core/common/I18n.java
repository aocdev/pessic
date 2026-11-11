package cat.pessic.core.common;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Every text the user sees, read from {@code i18n/strings_<language>.properties} (UTF-8).
 * Only Catalan exists for now ({@code strings_ca.properties}); a translation is a copy of that file
 * with the same keys, for example {@code strings_es.properties}.
 *
 * <p>Placeholders are {@code {0}}, {@code {1}}… and are replaced as plain text: unlike
 * {@link java.text.MessageFormat}, apostrophes need no escaping ({@code l'any}) and numbers are passed
 * already formatted.
 */
public final class I18n {

    static final String BUNDLE = "i18n.strings";
    public static final Locale DEFAULT = Locale.forLanguageTag("ca");

    private static volatile ResourceBundle bundle = load(DEFAULT);

    private I18n() {}

    /** The text for {@code key}, with {@code {0}}, {@code {1}}… replaced by {@code args}. */
    public static String t(String key, Object... args) {
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "‼" + key; // visible on screen, and caught by the bundle test
        }
        return args.length == 0 ? text : fill(text, args);
    }

    /**
     * {@code key.one} when {@code n} is 1, otherwise {@code key.other}, with {@code n} as {@code {0}}
     * ("1 dia", "3 dies"). Catalan, Spanish and English all have just these two forms.
     */
    public static String plural(String key, long n) {
        return t(key + (n == 1 ? ".one" : ".other"), n);
    }

    /** Switches the language. Texts already on screen keep the old one until they are rebuilt. */
    public static void use(Locale locale) {
        bundle = load(locale);
    }

    public static Locale locale() {
        return bundle.getLocale();
    }

    public static boolean has(String key) {
        return bundle.containsKey(key);
    }

    public static Set<String> keys() {
        return bundle.keySet();
    }

    static String fill(String text, Object... args) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int close = c == '{' ? text.indexOf('}', i) : -1;
            if (close > i + 1 && text.substring(i + 1, close).chars().allMatch(Character::isDigit)) {
                int n = Integer.parseInt(text.substring(i + 1, close));
                out.append(n < args.length ? String.valueOf(args[n]) : text.substring(i, close + 1));
                i = close;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static ResourceBundle load(Locale locale) {
        // no fallback to the JVM's default language: a missing translation falls back to Catalan
        ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        try {
            return ResourceBundle.getBundle(BUNDLE, locale, I18n.class.getClassLoader(), control);
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle(BUNDLE, DEFAULT, I18n.class.getClassLoader(), control);
        }
    }
}
