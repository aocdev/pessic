package cat.pessic.db.budget;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.db.Jdbc;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The limit of each category (table {@code category_limit}). */
public final class BudgetStore {
    private final Jdbc db;

    public BudgetStore(Jdbc db) {
        this.db = db;
    }

    public Map<Category, BigDecimal> limits(String profileId) {
        Map<Category, BigDecimal> limits = new EnumMap<>(Category.class);
        db.byProfile("SELECT category, limit_cents FROM category_limit WHERE profile_id = ?", profileId,
                rs -> limits.put(Category.ofCode(rs.getString(1)), Money.fromCents(rs.getLong(2))));
        return limits;
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "category_limit");
    }

    public void insert(String profileId, Map<Category, BigDecimal> limits) {
        db.batch("INSERT INTO category_limit(profile_id, category, limit_cents) VALUES (?, ?, ?)",
                List.copyOf(limits.entrySet()), (ps, e) -> {
                    ps.setString(1, profileId);
                    ps.setString(2, e.getKey().code());
                    ps.setLong(3, Money.toCents(e.getValue()));
                });
    }
}
