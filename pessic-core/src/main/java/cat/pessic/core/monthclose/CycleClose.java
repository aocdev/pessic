package cat.pessic.core.monthclose;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Snapshot of a cycle taken when the user closes it, so history keeps the limits and amounts of
 * that month even if the settings change later.
 *
 * @param income    salary of the cycle
 * @param extra     other money that came in during the cycle, including the previous month's
 *                  leftover carried over (refunds excluded)
 * @param budget    limits plus extra money added to the month's budget
 * @param spent     what was spent from the monthly budget, net of refunds
 * @param toPots    set aside: contributions to the reserve and goals posted at close, incomes sent
 *                  to them, and a leftover moved to a pot or carried over to the next month
 * @param leftover  budget − spent; negative when the month went over
 * @param leftoverTo where a positive leftover went
 * @param categories limit and net spending per category
 */
public record CycleClose(YearMonth cycle, LocalDate closedOn, BigDecimal income, BigDecimal extra,
                         BigDecimal budget, BigDecimal spent, BigDecimal toPots, BigDecimal leftover,
                         Leftover leftoverTo, Map<Category, CategoryLine> categories) {

    public enum Leftover {
        SAVINGS("Estalvi"), GOAL("Una guardiola"), RESERVE("Reserva d'anuals"), CARRY("El mes següent");

        public final String label;

        Leftover(String label) { this.label = label; }

        public String code() { return name().toLowerCase(); }

        public static Leftover ofCode(String code) { return valueOf(code.toUpperCase()); }
    }

    public record CategoryLine(BigDecimal limit, BigDecimal spent) {
        public CategoryLine {
            limit = Money.round(limit);
            spent = Money.round(spent);
        }
    }

    public CycleClose {
        income = Money.round(income);
        extra = Money.round(extra);
        budget = Money.round(budget);
        spent = Money.round(spent);
        toPots = Money.round(toPots);
        leftover = Money.round(leftover);
        var copy = new java.util.EnumMap<Category, CategoryLine>(Category.class);
        copy.putAll(categories);
        categories = java.util.Collections.unmodifiableMap(copy);
    }

    /** Money that came in and was neither spent from the month nor put into a pot. */
    public BigDecimal saved() {
        return income.add(extra).subtract(spent).subtract(toPots);
    }

    /** saved + toPots over everything that came in, in percent (0 when nothing came in). */
    public BigDecimal savingsRate() {
        BigDecimal in = income.add(extra);
        if (in.signum() == 0) return BigDecimal.ZERO;
        return Money.divide(in.subtract(spent).multiply(BigDecimal.valueOf(100)), in)
                .setScale(0, java.math.RoundingMode.HALF_UP);
    }
}
