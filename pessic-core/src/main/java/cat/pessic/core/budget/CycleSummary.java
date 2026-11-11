package cat.pessic.core.budget;

import cat.pessic.core.income.Income;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.ScheduledPayment;
import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the dashboard shows for one cycle. Amounts are rounded to cents.
 *
 * @param budget         sum of the effective category limits
 * @param spent          movements in the cycle, excluding those paid from the reserve
 * @param pendingFixed   fixed payments of the cycle not yet marked as paid
 * @param variableBudget budget left after all fixed payments; it is split into weeks
 * @param left           budget - spent - pendingFixed
 * @param perDay         left divided by the remaining days, or {@code null} if the cycle is over
 * @param projectedSavings income - limits - monthly reserve - goal contributions
 * @param goalsMonthly   planned monthly contributions to savings goals
 * @param extraBudget    extra incomes of the cycle sent to its budget (included in {@code budget})
 * @param newMoney       every extra income of the cycle, whatever its destination (refunds excluded)
 * @param refunds        refunds of the cycle (already subtracted from {@code spent})
 * @param incomes        extra incomes and refunds dated in the cycle
 * @param expectedPaycheck the extra paycheck due in this cycle, or {@code null}
 */
public record CycleSummary(
        PayCycle cycle,
        BigDecimal income,
        BigDecimal budget,
        BigDecimal extraThisCycle,
        BigDecimal monthlyReserve,
        BigDecimal projectedSavings,
        BigDecimal spent,
        BigDecimal pendingFixed,
        BigDecimal fixedTotal,
        BigDecimal variableBudget,
        BigDecimal variableSpent,
        BigDecimal left,
        int daysLeft,
        BigDecimal perDay,
        Status status,
        List<Envelope> envelopes,
        List<Week> weeks,
        List<PlanLine> plan,
        List<AnnualLine> annualDue,
        BigDecimal goalsMonthly,
        BigDecimal extraBudget,
        BigDecimal newMoney,
        BigDecimal refunds,
        List<Income> incomes,
        ExpectedPaycheck expectedPaycheck) {

    public enum Status { ON_TRACK, AHEAD_OF_PACE, OVER_BUDGET }

    public enum WeekState { PENDING, OK, WARNING, OVER }

    /**
     * One category for the cycle.
     *
     * @param annualYearly  total of the category's annual payments (funded by the reserve, not by the limit)
     * @param annualMonthly annualYearly / 12: what the reserve sets aside for this category every month
     * @param annualDue     annual payments of this category that fall in this cycle
     * @param annualPaid    the part of annualDue already recorded as paid from the reserve
     */
    public record Envelope(Category category, BigDecimal limit, BigDecimal baseLimit, BigDecimal fixedPaid,
                           BigDecimal fixedPending, BigDecimal variableSpent, BigDecimal remaining,
                           boolean over, BigDecimal annualYearly, BigDecimal annualMonthly,
                           BigDecimal annualDue, BigDecimal annualPaid) {
        /** No limit, no spending, no fixed or annual payments. */
        public boolean isEmpty() {
            return limit.signum() == 0 && fixedPaid.signum() == 0 && variableSpent.signum() == 0
                    && fixedPending.signum() == 0 && annualYearly.signum() == 0;
        }

        /** Monthly money committed to this category: its limit plus its share of the annual reserve. */
        public BigDecimal monthlyCommitment() {
            return limit.add(annualMonthly);
        }
    }

    /**
     * An annual payment that falls in this cycle (its month is the cycle's month). It is paid from the
     * reserve, so it never consumes the monthly budget.
     *
     * @param scheduledId {@code <annualId>@<year>}, stored on the movement that records the payment
     */
    public record AnnualLine(AnnualItem item, String scheduledId, boolean paid) {}

    /**
     * @param month    the month whose payroll includes it (June, December…)
     * @param recorded the income that records it, or {@code null} while it has not arrived
     */
    public record ExpectedPaycheck(java.time.YearMonth month, String scheduledId, BigDecimal amount, Income recorded) {
        public boolean received() {
            return recorded != null;
        }
    }

    public record Week(int number, java.time.LocalDate start, java.time.LocalDate end, BigDecimal limit,
                       BigDecimal spent, BigDecimal remaining, WeekState state, boolean current) {}

    /** A row of the payment plan with the running account balance after it. */
    public record PlanLine(ScheduledPayment payment, boolean paid, boolean overdue, BigDecimal balanceAfter) {}
}
