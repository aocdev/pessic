package cat.pessic.core.history;

import cat.pessic.core.account.AccountEntry;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.common.Money;
import cat.pessic.core.income.Income;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Month by month figures for the "Històric" tab. Closed cycles use their {@link CycleClose snapshot};
 * the others are computed now with today's limits and flagged as estimates.
 */
public final class History {
    private History() {}

    /**
     * @param closed  from a snapshot; otherwise an estimate with the current settings
     * @param current the cycle containing today (still running)
     * @param bankDifference what the bank had minus Pessic's estimate when the cycle was closed
     *                       (negative: probably expenses not recorded), or {@code null} if not checked
     */
    public record Row(YearMonth cycle, boolean closed, boolean current, BigDecimal income, BigDecimal extra,
                      BigDecimal budget, BigDecimal spent, BigDecimal toPots, Map<Category, CycleClose.CategoryLine> categories,
                      BigDecimal bankDifference) {

        /** Neither spent from the month nor put into a pot. */
        public BigDecimal saved() {
            return income.add(extra).subtract(spent).subtract(toPots);
        }

        /** Share of what came in that was not spent from the monthly budget, in percent. */
        public BigDecimal savingsRate() {
            BigDecimal in = income.add(extra);
            if (in.signum() == 0) return BigDecimal.ZERO;
            return Money.divide(in.subtract(spent).multiply(BigDecimal.valueOf(100)), in)
                    .setScale(0, java.math.RoundingMode.HALF_UP);
        }
    }

    /** New money of the cycle plus the previous month's leftover carried over into it. */
    public static BigDecimal extra(CycleSummary s) {
        return s.newMoney().add(s.incomes().stream().filter(i -> i.kind() == Income.Kind.CARRY)
                .map(Income::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * Up to {@code count} cycles up to and including the current one, oldest first. Starts at the
     * first cycle with any data (an expense, income or close), so months before the profile was
     * used do not show up as empty estimates.
     */
    public static List<Row> last(Profile p, LocalDate today, int count) {
        BudgetCalculator calc = BudgetCalculator.of(p);
        YearMonth current = calc.cycleContaining(today).key();
        YearMonth start = current.minusMonths(count - 1);
        var firstData = java.util.stream.Stream.of(
                        p.movements().stream().map(m -> calc.cycleContaining(m.date()).key()),
                        p.incomes().stream().map(i -> calc.cycleContaining(i.date()).key()),
                        p.ledger().closes().stream().map(CycleClose::cycle))
                .flatMap(s -> s).min(YearMonth::compareTo).orElse(current);
        if (firstData.isAfter(start)) start = firstData.isAfter(current) ? current : firstData;
        List<Row> rows = new ArrayList<>();
        for (YearMonth k = start; !k.isAfter(current); k = k.plusMonths(1)) {
            YearMonth key = k;
            var close = p.ledger().close(key);
            if (close.isPresent()) {
                CycleClose c = close.get();
                var checks = p.ledger().accountEntries().stream().filter(e -> key.equals(e.cycle())).toList();
                BigDecimal diff = checks.isEmpty() ? null
                        : checks.stream().map(AccountEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                rows.add(new Row(key, true, key.equals(current), c.income(), c.extra(), c.budget(), c.spent(),
                        c.toPots(), c.categories(), diff));
            } else {
                CycleSummary s = calc.summarize(key, p, today);
                BigDecimal toPots = s.incomes().stream().filter(i -> i.potId() != null).map(Income::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                Map<Category, CycleClose.CategoryLine> cats = new EnumMap<>(Category.class);
                for (CycleSummary.Envelope e : s.envelopes()) {
                    cats.put(e.category(), new CycleClose.CategoryLine(e.limit(), e.fixedPaid().add(e.variableSpent())));
                }
                rows.add(new Row(key, false, key.equals(current), s.income(), extra(s), s.budget(), s.spent(),
                        toPots, cats, null));
            }
        }
        return rows;
    }
}
