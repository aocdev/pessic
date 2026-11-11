package cat.pessic.db.monthclose;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.db.Jdbc;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Snapshots of closed months (tables {@code cycle_close} and {@code cycle_close_category}). */
public final class MonthCloseStore {
    private final Jdbc db;

    public MonthCloseStore(Jdbc db) {
        this.db = db;
    }

    public List<CycleClose> load(String profileId) {
        Map<YearMonth, Map<Category, CycleClose.CategoryLine>> cats = new LinkedHashMap<>();
        db.byProfile("SELECT cycle_ym, category, limit_cents, spent_cents FROM cycle_close_category WHERE profile_id = ?",
                profileId,
                rs -> cats.computeIfAbsent(YearMonth.parse(rs.getString(1)), k -> new EnumMap<>(Category.class))
                        .put(Category.ofCode(rs.getString(2)), new CycleClose.CategoryLine(Money.fromCents(rs.getLong(3)),
                                Money.fromCents(rs.getLong(4)))));
        return db.byProfile("SELECT cycle_ym, closed_on, income_cents, extra_cents, budget_cents, spent_cents, to_pots_cents, "
                        + "leftover_cents, leftover_to FROM cycle_close WHERE profile_id = ? ORDER BY cycle_ym", profileId,
                rs -> {
                    YearMonth k = YearMonth.parse(rs.getString(1));
                    return new CycleClose(k, LocalDate.parse(rs.getString(2)), Money.fromCents(rs.getLong(3)),
                            Money.fromCents(rs.getLong(4)), Money.fromCents(rs.getLong(5)), Money.fromCents(rs.getLong(6)),
                            Money.fromCents(rs.getLong(7)), Money.fromCents(rs.getLong(8)),
                            CycleClose.Leftover.ofCode(rs.getString(9)), cats.getOrDefault(k, Map.of()));
                });
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "cycle_close_category", "cycle_close"); // categories reference their close
    }

    public void insert(String profileId, List<CycleClose> closes) {
        db.batch("INSERT INTO cycle_close(profile_id, cycle_ym, closed_on, income_cents, extra_cents, budget_cents, "
                        + "spent_cents, to_pots_cents, leftover_cents, leftover_to) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                closes, (ps, c) -> {
                    ps.setString(1, profileId);
                    ps.setString(2, c.cycle().toString());
                    ps.setString(3, c.closedOn().toString());
                    ps.setLong(4, Money.toCents(c.income()));
                    ps.setLong(5, Money.toCents(c.extra()));
                    ps.setLong(6, Money.toCents(c.budget()));
                    ps.setLong(7, Money.toCents(c.spent()));
                    ps.setLong(8, Money.toCents(c.toPots()));
                    ps.setLong(9, Money.toCents(c.leftover()));
                    ps.setString(10, c.leftoverTo().code());
                });
        record Line(YearMonth cycle, Category category, CycleClose.CategoryLine line) {}
        List<Line> lines = new ArrayList<>();
        for (CycleClose c : closes) c.categories().forEach((cat, line) -> lines.add(new Line(c.cycle(), cat, line)));
        db.batch("INSERT INTO cycle_close_category(profile_id, cycle_ym, category, limit_cents, spent_cents) "
                + "VALUES (?, ?, ?, ?, ?)", lines, (ps, o) -> {
            ps.setString(1, profileId);
            ps.setString(2, o.cycle().toString());
            ps.setString(3, o.category().code());
            ps.setLong(4, Money.toCents(o.line().limit()));
            ps.setLong(5, Money.toCents(o.line().spent()));
        });
    }
}
