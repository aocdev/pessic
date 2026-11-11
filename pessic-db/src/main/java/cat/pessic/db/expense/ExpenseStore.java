package cat.pessic.db.expense;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.db.Jdbc;
import java.time.LocalDate;
import java.util.List;

/** Expenses, including fixed and annual payments marked as paid (table {@code movement}). */
public final class ExpenseStore {
    private final Jdbc db;

    public ExpenseStore(Jdbc db) {
        this.db = db;
    }

    public List<Movement> load(String profileId) {
        return db.byProfile("SELECT id, date, concept, category, amount_cents, from_reserve, scheduled_id, goal_id "
                        + "FROM movement WHERE profile_id = ? ORDER BY date, id", profileId,
                rs -> new Movement(rs.getString(1), LocalDate.parse(rs.getString(2)), rs.getString(3),
                        Category.ofCode(rs.getString(4)), Money.fromCents(rs.getLong(5)), rs.getBoolean(6), rs.getString(7),
                        rs.getString(8)));
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "movement");
    }

    public void insert(String profileId, List<Movement> movements) {
        db.batch("INSERT INTO movement(profile_id, id, date, concept, category, amount_cents, from_reserve, "
                + "scheduled_id, goal_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", movements, (ps, m) -> {
            ps.setString(1, profileId);
            ps.setString(2, m.id());
            ps.setString(3, m.date().toString());
            ps.setString(4, m.concept());
            ps.setString(5, m.category().code());
            ps.setLong(6, Money.toCents(m.amount()));
            ps.setBoolean(7, m.fromReserve());
            ps.setString(8, m.scheduledId());
            ps.setString(9, m.goalId());
        });
    }
}
