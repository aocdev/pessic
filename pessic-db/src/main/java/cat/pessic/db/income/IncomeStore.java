package cat.pessic.db.income;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.income.Income;
import cat.pessic.db.Jdbc;
import java.time.LocalDate;
import java.util.List;

/** Extra incomes and refunds (table {@code income}). */
public final class IncomeStore {
    private final Jdbc db;

    public IncomeStore(Jdbc db) {
        this.db = db;
    }

    public List<Income> load(String profileId) {
        return db.byProfile("SELECT id, date, concept, amount_cents, kind, destination, category, pot_id, scheduled_id "
                        + "FROM income WHERE profile_id = ? ORDER BY date, id", profileId,
                rs -> new Income(rs.getString(1), LocalDate.parse(rs.getString(2)), rs.getString(3),
                        Money.fromCents(rs.getLong(4)), Income.Kind.ofCode(rs.getString(5)),
                        Income.Destination.ofCode(rs.getString(6)),
                        rs.getString(7) == null ? null : Category.ofCode(rs.getString(7)), rs.getString(8), rs.getString(9)));
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "income");
    }

    public void insert(String profileId, List<Income> incomes) {
        db.batch("INSERT INTO income(profile_id, id, date, concept, amount_cents, kind, destination, category, pot_id, "
                + "scheduled_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", incomes, (ps, i) -> {
            ps.setString(1, profileId);
            ps.setString(2, i.id());
            ps.setString(3, i.date().toString());
            ps.setString(4, i.concept());
            ps.setLong(5, Money.toCents(i.amount()));
            ps.setString(6, i.kind().code());
            ps.setString(7, i.destination().code());
            ps.setString(8, i.category() == null ? null : i.category().code());
            ps.setString(9, i.potId());
            ps.setString(10, i.scheduledId());
        });
    }
}
