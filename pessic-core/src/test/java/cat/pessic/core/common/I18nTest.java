package cat.pessic.core.common;

import static org.junit.jupiter.api.Assertions.*;

import cat.pessic.core.budget.Category;
import cat.pessic.core.expense.MovementFilter;
import cat.pessic.core.income.Income;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.savings.PotEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The strings file and the code agree: every key the code asks for exists, every key in the file is
 * used, and every call passes as many values as its text has placeholders.
 */
class I18nTest {

    private static final Pattern CALL = Pattern.compile("\\b(t|plural)\\(\"([\\w.]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    record Use(Path file, String method, String key, int args) {}

    @Test
    void placeholdersAreFilledAsPlainText() {
        assertEquals("L'any 2026 té 12 mesos", I18n.fill("L'any {0} té {1} mesos", 2026, 12));
        assertEquals("{x} i {1}", I18n.fill("{x} i {1}", "a"), "unknown or missing placeholders stay as they are");
        assertEquals("‼no.such.key", I18n.t("no.such.key"));
        assertEquals("1 dia", I18n.plural("dashboard.days", 1));
        assertEquals("3 dies", I18n.plural("dashboard.days", 3));
        assertEquals("9 d'octubre de 2026",
                I18n.t("format.date", 9, I18n.t("format.ofMonth.10"), 2026), "elision lives in the strings file");
    }

    @Test
    void textsComeFromTheFile() {
        assertEquals("Informació i comunicacions", Category.INFO_COMMUNICATION.displayName());
        assertEquals("Paga extra", Income.Kind.EXTRA_PAY.label());
        assertEquals(Category.INFO_COMMUNICATION, Category.parse("Informació i comunicacions").orElseThrow());
    }

    @Test
    void everyKeyTheCodeUsesExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Use u : uses()) {
            if (u.method().equals("plural")) {
                for (String form : List.of(".one", ".other")) {
                    if (!I18n.has(u.key() + form)) missing.add(u.file().getFileName() + ": " + u.key() + form);
                }
            } else if (!I18n.has(u.key())) {
                missing.add(u.file().getFileName() + ": " + u.key());
            }
        }
        for (String k : dynamicKeys()) if (!I18n.has(k)) missing.add("(built in code) " + k);
        assertEquals(List.of(), missing);
    }

    @Test
    void everyKeyInTheFileIsUsed() throws IOException {
        Set<String> used = new TreeSet<>(dynamicKeys());
        for (Use u : uses()) {
            if (u.method().equals("plural")) {
                used.add(u.key() + ".one");
                used.add(u.key() + ".other");
            } else {
                used.add(u.key());
            }
        }
        Set<String> unused = new TreeSet<>(I18n.keys());
        unused.removeAll(used);
        assertEquals(Set.of(), unused);
    }

    @Test
    void everyCallFillsAllItsPlaceholders() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Use u : uses()) {
            if (u.method().equals("plural")) {
                for (String form : List.of(".one", ".other")) {
                    int n = placeholders(u.key() + form);
                    if (n > 1) wrong.add(u.key() + form + " has " + n + " placeholders; plural fills only {0}");
                }
            } else if (I18n.has(u.key()) && placeholders(u.key()) != u.args()) {
                wrong.add(u.file().getFileName() + ": " + u.key() + " expects " + placeholders(u.key()) + " values, gets " + u.args());
            }
        }
        assertEquals(List.of(), wrong);
    }

    /** Keys built at run time ("category." + code + ".name"), so they never appear literally in the code. */
    static Set<String> dynamicKeys() {
        Set<String> keys = new TreeSet<>();
        for (Category c : Category.ALL) {
            for (String part : List.of("name", "description", "examples")) keys.add("category." + c.code() + "." + part);
        }
        for (var e : Income.Kind.values()) keys.add("income.kind." + e.name());
        for (var e : Income.Destination.values()) keys.add("income.destination." + e.name());
        for (var e : CycleClose.Leftover.values()) keys.add("monthclose.leftover." + e.name());
        for (var e : PotEntry.Kind.values()) keys.add("savings.entry." + e.name());
        for (var e : MovementFilter.Type.values()) keys.add("expense.filter.type." + e.name());
        for (int i = 0; i < 9; i++) keys.add("ine.household." + i);
        for (int m = 1; m <= 12; m++) keys.add("format.ofMonth." + m);
        return keys;
    }

    private static int placeholders(String key) {
        Matcher m = PLACEHOLDER.matcher(I18n.t(key));
        int max = -1;
        while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
        return max + 1;
    }

    /** Every literal t("…") / plural("…") call in the main sources of all modules. */
    static List<Use> uses() throws IOException {
        Path root = Path.of("").toAbsolutePath().getParent(); // tests run from pessic-core/
        List<Use> out = new ArrayList<>();
        for (String module : List.of("pessic-core", "pessic-db", "pessic-app")) {
            Path src = root.resolve(module).resolve("src/main/java");
            assertTrue(Files.isDirectory(src), "sources not found: " + src);
            try (Stream<Path> files = Files.walk(src)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = Files.readString(f);
                    Matcher m = CALL.matcher(code);
                    while (m.find()) {
                        if (m.group(2).endsWith(".")) continue; // a prefix: t("category." + code + …), see dynamicKeys()
                        out.add(new Use(f, m.group(1), m.group(2), extraArgs(code, m.end())));
                    }
                }
            }
        }
        assertFalse(out.isEmpty());
        return out;
    }

    /** Counts the arguments after the key: top-level commas up to the call's closing parenthesis. */
    static int extraArgs(String code, int afterKey) {
        int depth = 0, commas = 0;
        for (int i = afterKey; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                for (i++; i < code.length() && code.charAt(i) != c; i++) if (code.charAt(i) == '\\') i++;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) return commas;
                depth--;
            } else if (c == ',' && depth == 0) {
                commas++;
            }
        }
        throw new IllegalStateException("unclosed call");
    }
}
