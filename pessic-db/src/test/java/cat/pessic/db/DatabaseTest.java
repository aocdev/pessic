package cat.pessic.db;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.Category;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    @Test
    void migratesToLatestVersionAndSeedsCategories(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub/pessic.db");
        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
            try (var rs = db.connection().createStatement().executeQuery("SELECT count(*) FROM category")) {
                assertEquals(Category.ALL.size(), rs.getInt(1));
            }
        }
        // Re-opening an up-to-date database is a no-op
        try (Database db = Database.open(file)) {
            assertEquals(Database.MIGRATIONS.size(), db.schemaVersion());
        }
    }

    @Test
    void splitsStatementsAndDropsComments() {
        var parts = Database.splitStatements("-- c\nCREATE TABLE a (x INT);\n\n-- d\nCREATE TABLE b (y INT);\n");
        assertEquals(2, parts.size());
        assertEquals("CREATE TABLE a (x INT)", parts.get(0));
    }
}
