package cat.pessic.db;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.account.AccountOps;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.db.profile.ProfileRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opens a database created with the V1 schema (old Catalan category names) and checks the upgrade. */
class MigrationTest {

    static final String[] LEGACY = {"Habitatge, aigua, llum i gas", "Alimentació", "Transport", "Restaurants i hotels",
            "Oci, esport i cultura", "Vestit i calçat", "Sanitat", "Cura personal i altres",
            "Assegurances i serveis financers", "Mobles i llar", "Comunicacions", "Educació", "Alcohol i tabac"};

    /** Creates a V1 database like the one Pessic 0.1 wrote, with one row in every table. */
    static void createV1(Path file, String annualCategory) throws Exception {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/db/migration/V1__init.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement()) {
            for (String s : Database.splitStatements(sql)) st.execute(s);
            for (int i = 0; i < LEGACY.length; i++) {
                st.execute("INSERT INTO category(name, sort_order) VALUES ('" + LEGACY[i].replace("'", "''") + "', " + i + ")");
            }
            st.execute("INSERT INTO profile(id, name, income_cents, savings_cents, payday, household) "
                    + "VALUES ('marta', 'Marta', 215000, 40000, 28, 7)");
            st.execute("INSERT INTO category_limit VALUES ('marta', 'Comunicacions', 3000)");
            st.execute("INSERT INTO category_limit VALUES ('marta', 'Transport', 48000)");
            st.execute("INSERT INTO fixed_payment VALUES ('marta', 'prestec', 'Préstec', 'Transport', 24530, 30, NULL, NULL, 0)");
            st.execute("INSERT INTO annual_item VALUES ('marta', 'cl', 12, 'Domini i correu', '" + annualCategory + "', 3600)");
            st.execute("INSERT INTO movement VALUES ('marta', 'm1', '2026-11-03', 'Cafè', 'Alimentació', 120, 0, NULL)");
            st.execute("PRAGMA user_version = 1");
        }
    }

    @Test
    void upgradesLegacyNamesToCodesAndKeepsEveryRow(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        createV1(file, "Habitatge, aigua, llum i gas");

        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
            Path backup = db.lastBackup().orElseThrow();
            assertEquals("pessic.db.bak-v1", backup.getFileName().toString());
            assertTrue(Files.size(backup) > 0);

            var p = new ProfileRepository(db).find("marta").orElseThrow();
            assertEquals(Money.of("30.00"), p.config().limit(Category.INFO_COMMUNICATION));
            assertEquals(Money.of("480.00"), p.config().limit(Category.TRANSPORT));
            assertEquals(Category.TRANSPORT, p.config().plan().get(0).category());
            assertEquals(Category.HOUSING, p.config().annuals().get(0).category());
            assertEquals("Domini i correu", p.config().annuals().get(0).concept());
            assertEquals(Category.FOOD, p.movements().get(0).category());
            assertNull(p.config().members());

            try (var rs = db.connection().createStatement().executeQuery(
                    "SELECT name FROM category WHERE code = '08'")) {
                assertEquals("Informació i comunicacions", rs.getString(1));
            }
            try (var rs = db.connection().createStatement().executeQuery("PRAGMA foreign_keys")) {
                assertEquals(1, rs.getInt(1), "foreign keys are back on after migrating");
            }
        }
        // An up-to-date database is opened without another backup
        try (Database db = Database.open(file)) {
            assertTrue(db.lastBackup().isEmpty());
        }
    }

    @Test
    void unknownCategoryAbortsTheUpgradeWithoutLosingData(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        createV1(file, "Habitatge, aigua, llum i gas");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement()) {
            st.execute("INSERT INTO category(name, sort_order) VALUES ('Inventada', 99)");
            st.execute("INSERT INTO movement VALUES ('marta', 'm2', '2026-11-04', 'X', 'Inventada', 100, 0, NULL)");
        }
        assertThrows(DataAccessException.class, () -> Database.open(file));
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement()) {
            try (var rs = st.executeQuery("PRAGMA user_version")) { assertEquals(1, rs.getInt(1)); }
            try (var rs = st.executeQuery("SELECT count(*) FROM movement")) { assertEquals(2, rs.getInt(1)); }
        }
    }

    @Test
    void upgradesV2ToTheLatestKeepingRowsAndBackingUp(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        createV1(file, "Habitatge, aigua, llum i gas");
        // bring it to v2 only, as Pessic 0.2 would have left it
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement()) {
            String sql;
            try (InputStream in = Database.class.getResourceAsStream("/db/migration/V2__coicop2018.sql")) {
                sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            st.execute("PRAGMA foreign_keys = OFF");
            for (String s : Database.splitStatements(sql)) st.execute(s);
            st.execute("PRAGMA user_version = 2");
        }
        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
            assertEquals("pessic.db.bak-v2", db.lastBackup().orElseThrow().getFileName().toString());
            var p = new ProfileRepository(db).find("marta").orElseThrow();
            assertEquals(1, p.movements().size());
            assertNull(p.movements().get(0).goalId());
            assertEquals(12, p.config().paychecks().count());
            assertTrue(p.incomes().isEmpty());
            assertFalse(p.ledger().reserve().openingSet());
            assertNull(p.ledger().account(), "no account balance until the user gives it");
            assertEquals(Money.of("36.00"), p.config().annuals().get(0).amount());
        }
    }

    @Test
    void upgradesV3ToV4WithABackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        createV1(file, "Habitatge, aigua, llum i gas");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            for (String name : new String[]{"V2__coicop2018.sql", "V3__income_pots_close.sql"}) {
                try (InputStream in = Database.class.getResourceAsStream("/db/migration/" + name)) {
                    for (String s : Database.splitStatements(new String(in.readAllBytes(), StandardCharsets.UTF_8))) st.execute(s);
                }
            }
            st.execute("PRAGMA user_version = 3");
        }
        try (Database db = Database.open(file)) {
            assertEquals(4, db.schemaVersion());
            assertEquals("pessic.db.bak-v3", db.lastBackup().orElseThrow().getFileName().toString());
            var repo = new ProfileRepository(db);
            var p = repo.find("marta").orElseThrow();
            assertNull(p.ledger().account());
            var withAccount = AccountOps.setAccountBalance(p, Money.of("512.40"), java.time.LocalDate.of(2026, 11, 11));
            repo.save(withAccount);
            assertEquals(Money.of("512.40"), repo.find("marta").orElseThrow().ledger().account().opening());
        }
    }
}
