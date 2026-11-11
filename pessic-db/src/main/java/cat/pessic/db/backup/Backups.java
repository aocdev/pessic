package cat.pessic.db.backup;

import cat.pessic.db.DataAccessException;
import cat.pessic.db.Database;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Automatic copies of the database in a {@code backups} folder next to it.
 *
 * <p>One copy a day is made on startup ({@code pessic-YYYY-MM-DD.db}); copies made by hand get the
 * time too ({@code pessic-YYYY-MM-DD-HHmmss.db}). Only the newest {@link #KEEP} of those are kept.
 * Copies made just before a restore ({@code pessic-before-restore-*.db}) are never deleted
 * automatically.
 */
public final class Backups {

    public static final int KEEP = 10;
    private static final Pattern NAME = Pattern.compile("pessic-(\\d{4}-\\d{2}-\\d{2})(-\\d{6})?\\.db");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    /** One copy on disk. {@code date} is the day it was made. */
    public record Copy(Path file, LocalDate date, long size, boolean beforeRestore) {}

    private final Path dir;

    public Backups(Path databaseFile) {
        this.dir = databaseFile.toAbsolutePath().resolveSibling("backups");
    }

    public Path folder() {
        return dir;
    }

    /** Makes today's copy unless one already exists. Returns it when a copy was made. */
    public Optional<Path> daily(Database db, LocalDate today) {
        boolean done = list().stream().anyMatch(c -> !c.beforeRestore() && c.date().equals(today));
        if (done) return Optional.empty();
        Path target = dir.resolve("pessic-" + today + ".db");
        if (Files.exists(target)) return Optional.empty();
        db.copyTo(target);
        prune();
        return Optional.of(target);
    }

    /** A copy made on request, named with the date and time. */
    public Path now(Database db, LocalDateTime now) {
        Path target = dir.resolve("pessic-" + now.toLocalDate() + "-" + now.format(TIME) + ".db");
        for (int i = 1; Files.exists(target); i++) {
            target = dir.resolve("pessic-" + now.toLocalDate() + "-" + now.plusSeconds(i).format(TIME) + ".db");
        }
        db.copyTo(target);
        prune();
        return target;
    }

    /** Copies, newest first. */
    public List<Copy> list() {
        if (!Files.isDirectory(dir)) return List.of();
        List<Copy> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString();
                Matcher m = NAME.matcher(n);
                try {
                    if (m.matches()) {
                        result.add(new Copy(f, LocalDate.parse(m.group(1)), Files.size(f), false));
                    } else if (n.startsWith("pessic-before-restore-") && n.endsWith(".db")) {
                        LocalDate d = LocalDate.ofInstant(Files.getLastModifiedTime(f).toInstant(),
                                java.time.ZoneId.systemDefault());
                        result.add(new Copy(f, d, Files.size(f), true));
                    }
                } catch (IOException ignored) {
                    // a file that vanished while listing
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        result.sort(Comparator.comparing((Copy c) -> c.file().getFileName().toString()).reversed());
        return result;
    }

    /** Deletes regular copies beyond the newest {@link #KEEP}. */
    void prune() {
        List<Copy> regular = list().stream().filter(c -> !c.beforeRestore()).toList();
        for (int i = KEEP; i < regular.size(); i++) {
            try {
                Files.deleteIfExists(regular.get(i).file());
            } catch (IOException ignored) {
                // try again next time
            }
        }
    }

    /**
     * Replaces the database with {@code copy}. Saves the current data first as
     * {@code pessic-before-restore-<millis>.db}, closes {@code db} and returns the reopened (and, if
     * the copy is older, migrated) database. If the copy is not a readable Pessic database nothing
     * is changed and an exception is thrown.
     */
    public Database restore(Database db, Path copy) {
        Path file = db.file().orElseThrow(() -> new IllegalStateException("In-memory database"));
        check(copy);
        Path safety = dir.resolve("pessic-before-restore-" + System.currentTimeMillis() + ".db");
        db.copyTo(safety);
        db.close();
        try {
            Files.copy(copy, file, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-wal"));
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-shm"));
        } catch (IOException e) {
            // put the current data back before giving up
            try {
                Files.copy(safety, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // the safety copy is still in the backups folder
            }
            throw new DataAccessException("Cannot restore " + copy, new SQLException(e));
        }
        return Database.open(file);
    }

    /** Throws if the file is not a sound SQLite database with Pessic's profile table. */
    static void check(Path copy) {
        if (!Files.isRegularFile(copy)) {
            throw new DataAccessException("No such file: " + copy, new SQLException("missing"));
        }
        org.sqlite.SQLiteConfig cfg = new org.sqlite.SQLiteConfig();
        cfg.setReadOnly(true);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copy.toAbsolutePath(), cfg.toProperties());
             Statement st = c.createStatement()) {
            try (var rs = st.executeQuery("PRAGMA quick_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new DataAccessException("The copy is damaged: " + copy, new SQLException("quick_check"));
                }
            }
            try (var rs = st.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'profile'")) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    throw new DataAccessException("Not a Pessic database: " + copy, new SQLException("no profile table"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read " + copy, e);
        }
    }
}
