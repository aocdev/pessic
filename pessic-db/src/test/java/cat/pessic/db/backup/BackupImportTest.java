package cat.pessic.db.backup;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.common.Money;
import cat.pessic.core.profile.Profile;
import cat.pessic.db.Database;
import cat.pessic.db.profile.ProfileRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BackupImportTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    Database db;
    ProfileRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        db = Database.inMemory();
        repo = new ProfileRepository(db);
        try (InputStream in = resource("/backup-sample.json")) {
            new BackupImporter().importInto(in, repo);
        }
    }

    @Test
    void importsProfileAndRoundTripsThroughTheDatabase() throws Exception {
        assertEquals("marta", repo.activeProfileId().orElseThrow());
        Profile stored = repo.find("marta").orElseThrow();

        Profile parsed;
        try (InputStream in = resource("/backup-sample.json")) {
            parsed = new BackupImporter().read(in).profiles().get(0);
        }
        assertEquals(java.util.Set.copyOf(parsed.config().plan()), java.util.Set.copyOf(stored.config().plan()));
        assertEquals(java.util.Set.copyOf(parsed.config().annuals()), java.util.Set.copyOf(stored.config().annuals()));
        assertEquals(parsed.config().limits(), stored.config().limits());
        assertEquals(parsed.config().income(), stored.config().income());
        assertEquals(parsed.movements().size(), stored.movements().size());
        assertTrue(stored.movements().containsAll(parsed.movements()));
    }

    @Test
    void reImportReplacesInsteadOfDuplicating() throws Exception {
        try (InputStream in = resource("/backup-sample.json")) {
            new BackupImporter().importInto(in, repo);
        }
        assertEquals(1, repo.list().size());
        assertEquals(15, repo.find("marta").orElseThrow().movements().size());
    }

    /** One line of the golden file produced by tools/js-oracle.js from the original PWA code. */
    record Golden(String[] f) {
        @Override public String toString() { return f[0]; }
    }

    static List<Golden> golden() throws Exception {
        try (var r = new BufferedReader(new InputStreamReader(
                resource("/golden-2026-11-09.psv"), StandardCharsets.UTF_8))) {
            return r.lines().filter(l -> !l.startsWith("#") && !l.isBlank())
                    .map(l -> new Golden(l.split("\\|", -1))).toList();
        }
    }

    @ParameterizedTest(name = "cycle {0} matches the PWA")
    @MethodSource("golden")
    void summaryMatchesOriginalJavaScript(Golden g) {
        String[] f = g.f();
        Profile p = repo.find("marta").orElseThrow();
        CycleSummary s = new BudgetCalculator(p.config()).summarize(YearMonth.parse(f[0]), p.movements(), TODAY);

        assertEquals(LocalDate.parse(f[1]), s.cycle().start());
        assertEquals(LocalDate.parse(f[2]), s.cycle().end());
        assertMoney(f[3], s.budget(), "budget");
        assertMoney(f[4], s.extraThisCycle(), "extra");
        assertMoney(f[5], s.monthlyReserve(), "reserve");
        assertMoney(f[6], s.projectedSavings(), "savings");
        assertMoney(f[7], s.spent(), "spent");
        assertMoney(f[8], s.pendingFixed(), "pendingFixed");
        assertMoney(f[9], s.fixedTotal(), "fixedTotal");
        assertMoney(f[10], s.variableBudget(), "variableBudget");
        assertMoney(f[11], s.variableSpent(), "variableSpent");
        assertMoney(f[12], s.left(), "left");
        assertEquals(Integer.parseInt(f[13]), s.daysLeft(), "daysLeft");
        if (f[14].isEmpty()) assertNull(s.perDay());
        else assertMoney(f[14], s.perDay(), "perDay");
        assertEquals(CycleSummary.Status.valueOf(f[15]), s.status());
        assertEquals(Integer.parseInt(f[16]), s.plan().size(), "plan size");
        assertMoneyList(f[17], s.weeks().stream().map(CycleSummary.Week::limit).toList(), "week limits");
        assertMoneyList(f[18], s.plan().stream().map(CycleSummary.PlanLine::balanceAfter).toList(), "balances");
    }

    private static void assertMoney(String expected, BigDecimal actual, String what) {
        assertEquals(Money.of(expected), actual, what);
    }

    private static void assertMoneyList(String expected, List<BigDecimal> actual, String what) {
        List<BigDecimal> exp = expected.isEmpty() ? List.of()
                : Arrays.stream(expected.split(" ")).map(Money::of).toList();
        assertEquals(exp, actual, what);
    }

    private static InputStream resource(String name) {
        return BackupImportTest.class.getResourceAsStream(name);
    }
}
