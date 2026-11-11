package cat.pessic.core.savings;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.core.common.Ids;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A pot of money set aside: the annual reserve (one per profile, id {@link #RESERVE_ID}) or a
 * savings goal ("guardiola"), such as a trip or an emergency fund.
 *
 * <p>Its balance is {@code opening} plus its {@link PotEntry entries}, plus incomes sent to it, minus
 * expenses paid from it (see {@link Pots#balance}).
 *
 * @param target      goal amount, or {@code null} (the reserve, or a goal without a target)
 * @param targetMonth month the goal should be reached by, or {@code null}
 * @param monthly     planned monthly contribution (for the reserve it is derived from the annual
 *                    payments and this field is ignored)
 * @param opening     what the pot held on {@code since}
 * @param since       from this day on, entries, incomes and expenses change the balance; for the
 *                    reserve it is {@code null} until the user says how much it holds
 */
public record Pot(String id, Kind kind, String name, BigDecimal target, YearMonth targetMonth, BigDecimal monthly,
                  BigDecimal opening, LocalDate since, boolean archived) {

    public static final String RESERVE_ID = "reserve";

    public enum Kind {
        RESERVE, GOAL;

        public String code() { return name().toLowerCase(); }

        public static Kind ofCode(String code) { return valueOf(code.toUpperCase()); }
    }

    public Pot {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("pot id is required");
        if ((kind == Kind.RESERVE) != RESERVE_ID.equals(id)) throw new IllegalArgumentException("bad reserve pot");
        name = name == null ? "" : name.strip();
        if (kind == Kind.GOAL && name.isEmpty()) throw new IllegalArgumentException("a goal needs a name");
        if (target != null) {
            if (target.signum() <= 0) throw new IllegalArgumentException("target must be > 0");
            target = Money.round(target);
        }
        monthly = Money.round(monthly == null ? BigDecimal.ZERO : monthly);
        if (monthly.signum() < 0) throw new IllegalArgumentException("monthly must be >= 0");
        opening = Money.round(opening == null ? BigDecimal.ZERO : opening);
    }

    /** The reserve before the user has said how much it holds. */
    public static Pot newReserve() {
        return new Pot(RESERVE_ID, Kind.RESERVE, t("savings.reserve.name"), null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                null, false);
    }

    public static Pot newGoal(String name, BigDecimal target, YearMonth targetMonth, BigDecimal monthly,
                              BigDecimal opening, LocalDate since) {
        return new Pot(Ids.next("g"), Kind.GOAL, name, target, targetMonth, monthly, opening, since, false);
    }

    public boolean openingSet() {
        return since != null;
    }

    /** True if something dated {@code date} changes the balance. */
    public boolean counts(LocalDate date) {
        return since != null && !date.isBefore(since);
    }

    public boolean isReserve() {
        return kind == Kind.RESERVE;
    }

    /** The pot held {@code amount} on {@code date}; earlier movements no longer change the balance. */
    public Pot withOpening(BigDecimal amount, LocalDate date) {
        return new Pot(id, kind, name, target, targetMonth, monthly, amount, date, archived);
    }

    public Pot withArchived(boolean value) {
        return new Pot(id, kind, name, target, targetMonth, monthly, opening, since, value);
    }
}
