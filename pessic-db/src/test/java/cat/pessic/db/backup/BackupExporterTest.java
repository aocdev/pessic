package cat.pessic.db.backup;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.account.AccountOps;
import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.ExpenseOps;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.monthclose.MonthClose;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.profile.NewProfile;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.core.savings.SavingsOps;
import cat.pessic.db.Database;
import cat.pessic.db.profile.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackupExporterTest {

    private BackupImporter.Backup sample() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/backup-sample.json")) {
            return new BackupImporter().read(in);
        }
    }

    @Test
    void exportThenImportGivesTheSameProfiles() throws Exception {
        BackupImporter.Backup original = sample();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new BackupExporter().write(out, original.profiles(), original.activeProfileId());

        BackupImporter.Backup again = new BackupImporter().read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(original.activeProfileId(), again.activeProfileId());
        Profile a = original.profiles().get(0), b = again.profiles().get(0);
        assertEquals(a.id(), b.id());
        assertEquals(a.name(), b.name());
        assertEquals(a.config().income(), b.config().income());
        assertEquals(a.config().payday(), b.config().payday());
        assertEquals(a.config().household(), b.config().household());
        assertEquals(a.config().limits(), b.config().limits());
        assertEquals(a.config().plan(), b.config().plan());
        assertEquals(a.config().annuals(), b.config().annuals());
        assertEquals(Set.copyOf(a.movements()), Set.copyOf(b.movements()));
    }

    /** Format v4: COICOP codes for categories, numbers stay JSON numbers. */
    @Test
    void writesFormatV3WithCategoryCodes() throws Exception {
        BackupImporter.Backup original = sample();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new BackupExporter().write(out, original.profiles(), original.activeProfileId());
        JsonNode root = new ObjectMapper().readTree(out.toByteArray());

        assertEquals(4, root.get("v").asInt());
        assertEquals("pessic", root.get("format").asText());
        JsonNode p = root.get("profiles").get("marta");
        JsonNode cfg = p.get("cfg");
        assertTrue(cfg.get("ingressos").isNumber());
        assertTrue(cfg.get("dia").isInt());
        assertTrue(cfg.get("limits").get("01").isNumber());
        assertEquals(13, cfg.get("limits").size());
        assertFalse(cfg.has("membres"), "unknown household size is omitted");
        JsonNode prestec = cfg.get("plan").get(0);
        assertTrue(prestec.get("imp").isNumber());
        assertEquals("07", prestec.get("cat").asText());
        assertFalse(prestec.has("des"), "optional keys are omitted, as in the web app");
        assertFalse(prestec.has("extra"));
        assertTrue(p.get("moves").isArray());
        JsonNode move = p.get("moves").get(0);
        assertTrue(move.get("reserva").isBoolean());
        assertTrue(move.get("imp").isNumber());
    }

    @Test
    void emptyListProducesAValidEmptyBackup() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new BackupExporter().write(out, List.of(), null);
        JsonNode root = new ObjectMapper().readTree(out.toByteArray());
        assertTrue(root.get("profiles").isEmpty());
    }

    static java.util.Map<Category, java.math.BigDecimal> allLimits() {
        var m = new java.util.EnumMap<Category, java.math.BigDecimal>(Category.class);
        Category.ALL.forEach(c -> m.put(c, java.math.BigDecimal.ZERO));
        m.put(Category.FOOD, Money.of("300"));
        return m;
    }

    /** Same content; the order of rows does not matter. */
    static void assertSameProfile(Profile a, Profile b) {
        assertEquals(a.withMovements(List.of()).withLedger(Ledger.EMPTY), b.withMovements(List.of()).withLedger(Ledger.EMPTY));
        assertEquals(Set.copyOf(a.movements()), Set.copyOf(b.movements()));
        assertEquals(Set.copyOf(a.incomes()), Set.copyOf(b.incomes()));
        assertEquals(Set.copyOf(a.ledger().pots()), Set.copyOf(b.ledger().pots()));
        assertEquals(Set.copyOf(a.ledger().entries()), Set.copyOf(b.ledger().entries()));
        assertEquals(a.ledger().closes(), b.ledger().closes());
        assertEquals(a.ledger().account(), b.ledger().account());
        assertEquals(Set.copyOf(a.ledger().accountEntries()), Set.copyOf(b.ledger().accountEntries()));
    }

    /** A profile that uses everything format v3 adds. */
    static Profile everything() {
        LocalDate today = LocalDate.of(2026, 12, 1);
        var cfg = new BudgetConfig(Money.of("2000"), Money.of("300"), 28, 2, 2,
                allLimits(), List.of(),
                List.of(new AnnualItem("cl", 12, "Claude", Category.INFO_COMMUNICATION, Money.of("217.80"))),
                new Paychecks(14, Money.of("1800"), List.of(6, 12)));
        Profile p = new Profile("p1", "Marta", cfg, List.of());
        Pot goal = new Pot("g1", Pot.Kind.GOAL, "Viatge", Money.of("1200"), YearMonth.of(2027, 4), Money.of("100"),
                Money.of("50"), LocalDate.of(2026, 10, 1), false);
        p = SavingsOps.saveGoal(p, goal);
        p = SavingsOps.setReserveBalance(p, Money.of("40"), LocalDate.of(2026, 10, 1));
        p = ExpenseOps.addMovement(p, LocalDate.of(2026, 11, 2), "Súper", Category.FOOD, Money.of("80"), false);
        p = ExpenseOps.addMovement(p, LocalDate.of(2026, 11, 3), "Bitllets", Category.TRANSPORT, Money.of("90"), false, "g1");
        p = IncomeOps.addIncome(p, LocalDate.of(2026, 11, 5), "Bonus", Money.of("150"), Income.Kind.BONUS,
                Income.Destination.GOAL, null, "g1", null);
        p = IncomeOps.addIncome(p, LocalDate.of(2026, 11, 6), "Jersei", Money.of("30"), Income.Kind.REFUND,
                Income.Destination.CATEGORY, Category.CLOTHING, null, null);
        p = SavingsOps.addPotEntry(p, "g1", LocalDate.of(2026, 11, 7), Money.of("-20"), PotEntry.Kind.WITHDRAWAL, "Sopar");
        p = AccountOps.setAccountBalance(p, Money.of("900"), LocalDate.of(2026, 10, 30));
        p = AccountOps.adjustAccount(p, Money.of("850"), LocalDate.of(2026, 11, 8), null);
        return MonthClose.close(p, YearMonth.of(2026, 11), java.util.Map.of(Pot.RESERVE_ID, Money.of("18.15"), "g1",
                Money.of("100")), CycleClose.Leftover.CARRY, null, Money.of("2500"), today);
    }

    @Test
    void v4RoundTripKeepsIncomesPotsClosedMonthsAndTheAccount() throws Exception {
        Profile p = everything();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new BackupExporter().write(out, List.of(p), p.id());
        Profile back = new BackupImporter().read(new ByteArrayInputStream(out.toByteArray())).profiles().get(0);
        assertSameProfile(p, back);
    }

    @Test
    void v4IsStoredAndReadBackFromTheDatabase() {
        Profile p = everything();
        assertNotNull(p.ledger().account());
        assertEquals(2, p.ledger().accountEntries().size());
        try (Database db = Database.inMemory()) {
            var repo = new ProfileRepository(db);
            repo.save(p);
            assertSameProfile(p, repo.find(p.id()).orElseThrow());
            repo.save(p); // saving again replaces every row
            assertSameProfile(p, repo.find(p.id()).orElseThrow());
            assertFalse(repo.list().get(0).empty());
            repo.delete(p.id());
            assertTrue(repo.list().isEmpty());
        }
    }

    @Test
    void membersSurviveTheRoundTrip() throws Exception {
        Profile p = NewProfile.create("Marta", Money.of("3000"), 1, 3, 3, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new BackupExporter().write(out, List.of(p), p.id());
        Profile back = new BackupImporter().read(new ByteArrayInputStream(out.toByteArray())).profiles().get(0);
        assertEquals(3, back.config().members());
        assertEquals(p.config().limits(), back.config().limits());
    }
}
