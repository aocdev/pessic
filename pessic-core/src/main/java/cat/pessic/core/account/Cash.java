package cat.pessic.core.account;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.budget.PayCycle;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.payment.ScheduledPayment;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.PotEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Estimated balance of the day-to-day account (where the salary arrives) and what is left of it
 * before the next payday.
 *
 * <p>Starting from what the user said the account held at the end of {@link Account#since()}, every
 * later day adds or takes away:
 * <ul>
 *   <li>on each payday: + salary − automatic savings − what goes to the reserve and the goals (the
 *       planned amounts, or the real contributions once that cycle is closed);</li>
 *   <li>− expenses, except those paid from the reserve or a goal (that money is in the pot);</li>
 *   <li>+ extra incomes and refunds, except money sent straight to the reserve or a goal and the
 *       previous month's leftover (it never left the account);</li>
 *   <li>− money put into a pot by hand, + money taken out of it;</li>
 *   <li>± corrections to what the bank says ({@link AccountEntry}).</li>
 * </ul>
 */
public final class Cash {
    private Cash() {}

    /** The account balance at the end of {@code date}, or {@code null} if the account is not set up. */
    public static BigDecimal estimate(Profile p, LocalDate date) {
        Account a = p.ledger().account();
        if (a == null) return null;
        BigDecimal b = a.opening();
        if (!date.isAfter(a.since())) return b;
        BudgetCalculator calc = BudgetCalculator.of(p);

        // paydays after the opening day
        for (YearMonth k = calc.cycleContaining(a.since()).key().plusMonths(1); ; k = k.plusMonths(1)) {
            LocalDate payday = calc.cycle(k).start();
            if (payday.isAfter(date)) break;
            if (!a.counts(payday)) continue;
            b = b.add(paydayFlow(p, calc, k));
        }
        for (Movement m : p.movements()) {
            if (!m.outsideBudget() && inRange(a, m.date(), date)) b = b.subtract(m.amount());
        }
        for (Income i : p.incomes()) {
            if (staysInAccount(i) && inRange(a, i.date(), date)) b = b.add(i.amount());
        }
        for (PotEntry e : p.ledger().entries()) {
            // contributions posted by a month close belong to that cycle's payday (counted above)
            if (e.cycle() == null && e.kind() != PotEntry.Kind.ADJUST && inRange(a, e.date(), date)) {
                b = b.subtract(e.amount());
            }
        }
        for (AccountEntry e : p.ledger().accountEntries()) {
            if (inRange(a, e.date(), date)) b = b.add(e.amount());
        }
        return Money.round(b);
    }

    /**
     * What happens on the payday that starts cycle {@code key}: the salary comes in, the automatic
     * savings leave, and so does the money for the reserve and the goals.
     */
    static BigDecimal paydayFlow(Profile p, BudgetCalculator calc, YearMonth key) {
        var close = p.ledger().close(key);
        BigDecimal salary = close.map(CycleClose::income).orElse(p.config().income());
        BigDecimal toPots;
        if (close.isPresent()) {
            toPots = p.ledger().entries().stream()
                    .filter(e -> key.equals(e.cycle()) && e.kind() != PotEntry.Kind.ADJUST)
                    .map(PotEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            toPots = calc.monthlyReserve().add(calc.goalsMonthly());
        }
        return salary.subtract(p.config().savings()).subtract(toPots);
    }

    /** Money that comes in and stays in the day-to-day account. */
    static boolean staysInAccount(Income i) {
        return i.kind() != Income.Kind.CARRY && i.potId() == null;
    }

    private static boolean inRange(Account a, LocalDate d, LocalDate until) {
        return a.counts(d) && !d.isAfter(until);
    }

    // ------------------------------------------------------------------ forecast

    /**
     * One step of the forecast.
     *
     * @param fixed the fixed payment, or {@code null} for the rest of the variable budget
     */
    public record Line(LocalDate date, String concept, BigDecimal amount, BigDecimal balanceAfter,
                       ScheduledPayment fixed) {
        public boolean negative() {
            return Money.isNegative(balanceAfter);
        }
    }

    /**
     * From today to the end of the current cycle.
     *
     * @param today          estimated balance today
     * @param lines          fixed payments not yet paid (overdue ones dated today), then what is
     *                       left of the variable budget, each with the balance after it
     * @param beforePayday   balance on the last day of the cycle if every pending payment is made
     *                       and the whole budget is spent
     * @param lowest         lowest balance on the way
     * @param lowestDate     when it happens
     */
    public record Forecast(PayCycle cycle, BigDecimal today, List<Line> lines, BigDecimal beforePayday,
                           BigDecimal lowest, LocalDate lowestDate) {
        public boolean goesNegative() {
            return Money.isNegative(lowest);
        }

        /** The balance after a fixed payment, by its scheduled id, or null if it is already paid. */
        public BigDecimal balanceAfter(String scheduledId) {
            return lines.stream().filter(l -> l.fixed() != null && l.fixed().scheduledId().equals(scheduledId))
                    .map(Line::balanceAfter).findFirst().orElse(null);
        }
    }

    /** Forecast for the cycle containing {@code today}, or {@code null} if the account is not set up. */
    public static Forecast forecast(Profile p, LocalDate today) {
        BigDecimal now = estimate(p, today);
        if (now == null) return null;
        BudgetCalculator calc = BudgetCalculator.of(p);
        PayCycle cycle = calc.cycleContaining(today);
        CycleSummary s = calc.summarize(cycle.key(), p, today);
        Set<String> paid = p.movements().stream().map(Movement::scheduledId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<Line> lines = new ArrayList<>();
        BigDecimal balance = now;
        BigDecimal lowest = now;
        LocalDate lowestDate = today;
        List<ScheduledPayment> pending = calc.planFor(cycle.key()).stream()
                .filter(sp -> !paid.contains(sp.scheduledId()))
                .sorted(Comparator.comparing(ScheduledPayment::date)).toList();
        for (ScheduledPayment sp : pending) {
            LocalDate d = sp.date().isBefore(today) ? today : sp.date();
            balance = balance.subtract(sp.payment().amount());
            lines.add(new Line(d, sp.payment().concept(), sp.payment().amount().negate(), Money.round(balance), sp));
            if (balance.compareTo(lowest) < 0) {
                lowest = balance;
                lowestDate = d;
            }
        }
        // whatever is still left to spend this month
        BigDecimal variableLeft = s.left().max(BigDecimal.ZERO);
        if (variableLeft.signum() > 0) {
            balance = balance.subtract(variableLeft);
            lines.add(new Line(cycle.end(), t("account.forecast.restOfBudget"), variableLeft.negate(),
                    Money.round(balance), null));
            if (balance.compareTo(lowest) < 0) {
                lowest = balance;
                lowestDate = cycle.end();
            }
        }
        return new Forecast(cycle, now, lines, Money.round(balance), Money.round(lowest), lowestDate);
    }
}
