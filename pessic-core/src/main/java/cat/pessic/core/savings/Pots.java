package cat.pessic.core.savings;

import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/** Balances and forecasts of the annual reserve and savings goals. */
public final class Pots {
    private Pots() {}

    /**
     * What the pot holds: its opening amount plus, from {@code since} on, its entries and the incomes
     * sent to it, minus the expenses paid from it (annual payments for the reserve, or expenses
     * marked as paid from the goal).
     */
    public static BigDecimal balance(Pot pot, Profile p) {
        BigDecimal b = pot.opening();
        for (PotEntry e : p.ledger().entries()) {
            if (e.potId().equals(pot.id()) && pot.counts(e.date())) b = b.add(e.amount());
        }
        for (Income i : p.incomes()) {
            if (pot.id().equals(i.potId()) && pot.counts(i.date())) b = b.add(i.amount());
        }
        for (Movement m : p.movements()) {
            boolean fromIt = pot.isReserve() ? m.fromReserve() : pot.id().equals(m.goalId());
            if (fromIt && pot.counts(m.date())) b = b.subtract(m.amount());
        }
        return Money.round(b);
    }

    /** First month the reserve will not have enough for the annual payments due, if any. */
    public record Shortfall(YearMonth cycle, BigDecimal missing, BigDecimal due) {}

    /**
     * Walks the next 12 cycles from the one containing {@code today}: each one pays its annual
     * payments not yet paid, and then (when the month is closed) adds the monthly reserve. Assumes
     * every month will be closed with its contribution.
     */
    public static Optional<Shortfall> reserveShortfall(Profile p, LocalDate today) {
        Pot reserve = p.ledger().reserve();
        if (!reserve.openingSet()) return Optional.empty();
        BudgetCalculator calc = BudgetCalculator.of(p);
        BigDecimal balance = balance(reserve, p);
        BigDecimal monthly = calc.monthlyReserve();
        YearMonth key = calc.cycleContaining(today).key();
        for (int i = 0; i < 12; i++, key = key.plusMonths(1)) {
            BigDecimal due = BigDecimal.ZERO;
            for (var line : calc.annualDue(key, p.movements())) if (!line.paid()) due = due.add(line.item().amount());
            balance = balance.subtract(due);
            if (Money.isNegative(balance)) return Optional.of(new Shortfall(key, Money.round(balance.negate()), due));
            // the current cycle's contribution is posted when it is closed
            balance = balance.add(monthly);
        }
        return Optional.empty();
    }

    /**
     * The smallest amount the reserve should hold today so that, adding the monthly reserve every
     * month, every annual payment of the next 12 months can be paid. Ignores what it holds now.
     */
    public static BigDecimal reserveNeededNow(Profile p, LocalDate today) {
        BudgetCalculator calc = BudgetCalculator.of(p);
        BigDecimal monthly = calc.monthlyReserve();
        YearMonth key = calc.cycleContaining(today).key();
        BigDecimal running = BigDecimal.ZERO;
        BigDecimal lowest = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++, key = key.plusMonths(1)) {
            for (var line : calc.annualDue(key, p.movements())) if (!line.paid()) running = running.subtract(line.item().amount());
            if (running.compareTo(lowest) < 0) lowest = running;
            running = running.add(monthly);
        }
        return Money.round(lowest.negate());
    }

    /**
     * Progress of a goal.
     *
     * @param percent      balance over target, 0..100 (null without a target)
     * @param monthsLeft   cycles from the current one to the target month, inclusive (null without a date)
     * @param neededMonthly what has to go in every month to reach the target in time (null without both)
     * @param onTrack      the planned monthly contribution is enough (true when nothing is required)
     */
    public record Progress(BigDecimal balance, Integer percent, Integer monthsLeft, BigDecimal neededMonthly,
                           boolean reached, boolean onTrack) {}

    public static Progress progress(Pot goal, Profile p, LocalDate today) {
        BigDecimal balance = balance(goal, p);
        Integer percent = null;
        boolean reached = false;
        if (goal.target() != null) {
            reached = balance.compareTo(goal.target()) >= 0;
            percent = reached ? 100 : Math.max(0, Money.divide(balance.multiply(BigDecimal.valueOf(100)), goal.target())
                    .setScale(0, java.math.RoundingMode.DOWN).intValue());
        }
        Integer monthsLeft = null;
        BigDecimal needed = null;
        if (goal.targetMonth() != null) {
            YearMonth now = new BudgetCalculator(p.config()).cycleContaining(today).key();
            monthsLeft = (int) Math.max(0, now.until(goal.targetMonth(), java.time.temporal.ChronoUnit.MONTHS) + 1);
            if (goal.target() != null) {
                BigDecimal missing = goal.target().subtract(balance).max(BigDecimal.ZERO);
                needed = monthsLeft == 0 ? missing : Money.round(Money.divide(missing, monthsLeft));
            }
        }
        boolean onTrack = reached || needed == null || goal.monthly().compareTo(needed) >= 0
                || (monthsLeft != null && monthsLeft == 0 && needed.signum() == 0);
        return new Progress(balance, percent, monthsLeft, needed, reached, onTrack);
    }
}
