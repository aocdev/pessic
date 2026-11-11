package cat.pessic.db.payment;

import static cat.pessic.db.Jdbc.text;
import static cat.pessic.db.Jdbc.ym;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.FixedPayment;
import cat.pessic.db.Jdbc;
import java.util.List;

/** Fixed monthly payments and annual payments (tables {@code fixed_payment} and {@code annual_item}). */
public final class PaymentStore {
    private final Jdbc db;

    public PaymentStore(Jdbc db) {
        this.db = db;
    }

    public List<FixedPayment> fixed(String profileId) {
        return db.byProfile("SELECT id, day, concept, category, amount_cents, from_ym, to_ym, is_extra "
                        + "FROM fixed_payment WHERE profile_id = ? ORDER BY day, concept", profileId,
                rs -> new FixedPayment(rs.getString(1), rs.getInt(2), rs.getString(3), Category.ofCode(rs.getString(4)),
                        Money.fromCents(rs.getLong(5)), ym(rs.getString(6)), ym(rs.getString(7)), rs.getBoolean(8)));
    }

    public List<AnnualItem> annuals(String profileId) {
        return db.byProfile("SELECT id, month, concept, category, amount_cents FROM annual_item "
                        + "WHERE profile_id = ? ORDER BY month, concept", profileId,
                rs -> new AnnualItem(rs.getString(1), rs.getInt(2), rs.getString(3), Category.ofCode(rs.getString(4)),
                        Money.fromCents(rs.getLong(5))));
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "fixed_payment", "annual_item");
    }

    public void insert(String profileId, List<FixedPayment> plan, List<AnnualItem> annuals) {
        db.batch("INSERT INTO fixed_payment(profile_id, id, concept, category, amount_cents, day, from_ym, "
                + "to_ym, is_extra) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", plan, (ps, f) -> {
            ps.setString(1, profileId);
            ps.setString(2, f.id());
            ps.setString(3, f.concept());
            ps.setString(4, f.category().code());
            ps.setLong(5, Money.toCents(f.amount()));
            ps.setInt(6, f.day());
            ps.setString(7, text(f.from()));
            ps.setString(8, text(f.to()));
            ps.setBoolean(9, f.extra());
        });
        db.batch("INSERT INTO annual_item(profile_id, id, month, concept, category, amount_cents) "
                + "VALUES (?, ?, ?, ?, ?, ?)", annuals, (ps, a) -> {
            ps.setString(1, profileId);
            ps.setString(2, a.id());
            ps.setInt(3, a.month());
            ps.setString(4, a.concept());
            ps.setString(5, a.category().code());
            ps.setLong(6, Money.toCents(a.amount()));
        });
    }
}
