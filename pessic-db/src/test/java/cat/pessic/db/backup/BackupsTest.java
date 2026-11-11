package cat.pessic.db.backup;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.common.Money;
import cat.pessic.core.ine.Catalog;
import cat.pessic.core.profile.NewProfile;
import cat.pessic.db.DataAccessException;
import cat.pessic.db.Database;
import cat.pessic.db.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupsTest {

    static void saveProfile(Database db, String name) {
        var p = NewProfile.create(name, Money.of("2000"), 1, Catalog.ALL_HOUSEHOLDS, null, false);
        new ProfileRepository(db).save(p);
    }

    @Test
    void dailyCopyOncePerDayAndKeepsTheNewestTen(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        try (Database db = Database.open(file)) {
            saveProfile(db, "Marta");
            Backups b = new Backups(file);
            LocalDate d = LocalDate.of(2026, 1, 1);
            Path first = b.daily(db, d).orElseThrow();
            assertEquals("pessic-2026-01-01.db", first.getFileName().toString());
            assertTrue(b.daily(db, d).isEmpty(), "only one a day");
            for (int i = 1; i <= 12; i++) b.daily(db, d.plusDays(i));
            var copies = b.list();
            assertEquals(Backups.KEEP, copies.size());
            assertEquals(d.plusDays(12), copies.get(0).date());
            assertEquals(d.plusDays(3), copies.get(copies.size() - 1).date());
            assertFalse(Files.exists(first));

            // a copy is a complete database
            try (Database copy = Database.open(copies.get(0).file())) {
                assertEquals(1, new ProfileRepository(copy).list().size());
            }
        }
    }

    @Test
    void restoreBringsBackTheCopyAndKeepsTheCurrentData(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        Database db = Database.open(file);
        saveProfile(db, "Marta");
        Backups b = new Backups(file);
        Path copy = b.now(db, LocalDateTime.of(2026, 3, 4, 10, 11, 12));
        assertEquals("pessic-2026-03-04-101112.db", copy.getFileName().toString());
        saveProfile(db, "Joan");
        assertEquals(2, new ProfileRepository(db).list().size());

        db = b.restore(db, copy);
        try (Database restored = db) {
            assertEquals(1, new ProfileRepository(restored).list().size());
            var safety = b.list().stream().filter(Backups.Copy::beforeRestore).findFirst().orElseThrow();
            try (Database before = Database.open(safety.file())) {
                assertEquals(2, new ProfileRepository(before).list().size());
            }
        }
    }

    @Test
    void refusesAFileThatIsNotAPessicDatabase(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("pessic.db");
        Path junk = dir.resolve("junk.db");
        Files.writeString(junk, "not a database");
        try (Database db = Database.open(file)) {
            saveProfile(db, "Marta");
            Backups b = new Backups(file);
            assertThrows(DataAccessException.class, () -> b.restore(db, junk));
            assertEquals(1, new ProfileRepository(db).list().size(), "nothing changed");
        }
    }
}
