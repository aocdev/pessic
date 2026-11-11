package cat.pessic.db;

import cat.pessic.core.budget.Category;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.sqlite.SQLiteConfig;

/**
 * Opens the embedded SQLite database and brings its schema up to date.
 *
 * <p>Migrations live in {@code /db/migration/V<n>__*.sql} and are listed in {@link #MIGRATIONS}.
 * The applied version is kept in SQLite's {@code PRAGMA user_version}, so no extra table or
 * library is needed. Never edit a released migration: add a new one.
 */
public final class Database implements AutoCloseable {

    static final List<String> MIGRATIONS = List.of("V1__init.sql", "V2__coicop2018.sql", "V3__income_pots_close.sql",
            "V4__account.sql");

    private final Connection connection;
    /** The database file, or {@code null} in memory. Used to back it up before migrating. */
    private final Path file;
    private Path lastBackup;

    private Database(Connection connection, Path file) {
        this.connection = connection;
        this.file = file;
    }

    public static Database open(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            return open("jdbc:sqlite:" + file.toAbsolutePath(), file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An in-memory database, for tests. */
    public static Database inMemory() {
        return open("jdbc:sqlite::memory:", null);
    }

    /** Backup made before the last schema upgrade, if one was needed when opening. */
    public java.util.Optional<Path> lastBackup() {
        return java.util.Optional.ofNullable(lastBackup);
    }

    private static Database open(String url, Path file) {
        try {
            SQLiteConfig cfg = new SQLiteConfig();
            cfg.enforceForeignKeys(true);
            cfg.setJournalMode(SQLiteConfig.JournalMode.WAL);
            Connection c = DriverManager.getConnection(url, cfg.toProperties());
            Database db = new Database(c, file);
            db.migrate();
            db.seedCategories();
            return db;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database " + url, e);
        }
    }

    /** The database file, or empty for an in-memory database. */
    public java.util.Optional<Path> file() {
        return java.util.Optional.ofNullable(file);
    }

    /**
     * Writes a consistent, compacted copy of the whole database to {@code target} with
     * {@code VACUUM INTO}. Works while the app is using the database (also in WAL mode).
     * The target must not exist yet.
     */
    public void copyTo(Path target) {
        try {
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            try (PreparedStatement ps = connection.prepareStatement("VACUUM INTO ?")) {
                ps.setString(1, target.toAbsolutePath().toString());
                ps.execute();
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot create " + target.getParent(), new SQLException(e));
        } catch (SQLException e) {
            throw new DataAccessException("Cannot copy the database to " + target, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    public int schemaVersion() throws SQLException {
        try (Statement st = connection.createStatement(); var rs = st.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    private void migrate() throws SQLException {
        int current = schemaVersion();
        if (current >= MIGRATIONS.size()) return;
        if (current > 0) backup(current);
        // Table rebuilds need foreign keys off; that pragma is ignored inside a transaction.
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
        }
        try {
            for (int v = current + 1; v <= MIGRATIONS.size(); v++) {
                String sql = readMigration(MIGRATIONS.get(v - 1));
                boolean auto = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (Statement st = connection.createStatement()) {
                    for (String statement : splitStatements(sql)) st.execute(statement);
                    try (var rs = st.executeQuery("PRAGMA foreign_key_check")) {
                        if (rs.next()) {
                            throw new SQLException("Foreign key violation in table " + rs.getString(1)
                                    + " (row " + rs.getLong(2) + ", references " + rs.getString(3) + ")");
                        }
                    }
                    st.execute("PRAGMA user_version = " + v);
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw new DataAccessException("Migration " + MIGRATIONS.get(v - 1) + " failed", e);
                } finally {
                    connection.setAutoCommit(auto);
                }
            }
        } finally {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    /** Copies the file to {@code <name>.bak-v<version>} (never overwriting) before it is upgraded. */
    private void backup(int version) throws SQLException {
        if (file == null) return;
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(TRUNCATE)"); // make the main file complete
        }
        try {
            Path target = file.resolveSibling(file.getFileName() + ".bak-v" + version);
            if (Files.exists(target)) {
                target = file.resolveSibling(file.getFileName() + ".bak-v" + version + "-" + System.currentTimeMillis());
            }
            Files.copy(file, target);
            lastBackup = target;
        } catch (IOException e) {
            throw new DataAccessException("Cannot back up the database before upgrading it", new SQLException(e));
        }
    }

    private void seedCategories() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO category(code, name, sort_order) VALUES (?, ?, ?) "
                        + "ON CONFLICT(code) DO UPDATE SET name = excluded.name, sort_order = excluded.sort_order")) {
            for (Category c : Category.ALL) {
                ps.setString(1, c.code());
                ps.setString(2, c.displayName());
                ps.setInt(3, c.ordinal() + 1);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static String readMigration(String name) {
        try (InputStream in = Database.class.getResourceAsStream("/db/migration/" + name)) {
            if (in == null) throw new IllegalStateException("Missing migration " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Splits on ';' at line ends, dropping '--' comment lines. Enough for plain DDL files. */
    static List<String> splitStatements(String sql) {
        String noComments = sql.lines().filter(l -> !l.strip().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");
        return java.util.Arrays.stream(noComments.split(";\\s*\\n"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot close database", e);
        }
    }
}
