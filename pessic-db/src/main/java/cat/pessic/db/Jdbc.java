package cat.pessic.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** The bit of JDBC every feature's store needs: queries, updates, batches and transactions. */
public final class Jdbc {

    public interface Binder { void bind(PreparedStatement ps) throws SQLException; }
    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }
    public interface ItemBinder<T> { void bind(PreparedStatement ps, T item) throws SQLException; }
    public interface Work { void run() throws SQLException; }

    private final Connection c;

    public Jdbc(Database db) {
        this.c = db.connection();
    }

    public <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    /** Rows of a table that belong to one profile. */
    public <T> List<T> byProfile(String sql, String profileId, RowMapper<T> mapper) {
        return query(sql, ps -> ps.setString(1, profileId), mapper);
    }

    public void update(String sql, Binder binder) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    /** Deletes a profile's rows from each table, in the given order (children first). */
    public void deleteProfileRows(String profileId, String... tables) {
        for (String table : tables) update("DELETE FROM " + table + " WHERE profile_id = ?", ps -> ps.setString(1, profileId));
    }

    public <T> void batch(String sql, List<T> items, ItemBinder<T> binder) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (T item : items) {
                binder.bind(ps, item);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    public void inTransaction(Work work) {
        try {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                work.run();
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Transaction failed", e);
        }
    }

    // ------------------------------------------------------------ column helpers

    public static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    public static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    public static LocalDate date(String s) {
        return s == null ? null : LocalDate.parse(s);
    }

    public static YearMonth ym(String s) {
        return s == null ? null : YearMonth.parse(s);
    }

    public static String text(Object o) {
        return o == null ? null : o.toString();
    }
}
