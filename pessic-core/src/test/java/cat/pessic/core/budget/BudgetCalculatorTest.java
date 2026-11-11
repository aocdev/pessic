package cat.pessic.core.budget;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.FixedPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetCalculatorTest {

    static final YearMonth NOV = YearMonth.of(2026, 11);

    static BigDecimal eur(String v) {
        return Money.of(v);
    }

    static BudgetConfig config(List<FixedPayment> plan, List<AnnualItem> annuals) {
        return new BudgetConfig(eur("2000"), eur("300"), 28, 2,
                Map.of(Category.FOOD, eur("300"), Category.TRANSPORT, eur("200"), Category.RECREATION, eur("100")),
                plan, annuals);
    }

    @Test
    void fixedPaymentBeforePaydayFallsInSecondMonthAndClampsToMonthEnd() {
        var calc = new BudgetCalculator(config(List.of(
                new FixedPayment("gym", 1, "Gym", Category.RECREATION, eur("40"), null, null, false),
                new FixedPayment("loan", 30, "Loan", Category.TRANSPORT, eur("150"), null, null, false)), List.of()));

        // Cycle "March 2027" runs 28 Feb - 27 Mar
        var plan = calc.planFor(YearMonth.of(2027, 3));
        assertEquals(LocalDate.of(2027, 2, 28), plan.get(0).date()); // day 30 clamped to 28 Feb
        assertEquals(LocalDate.of(2027, 3, 1), plan.get(1).date());
        assertEquals("loan@2027-03", plan.get(0).scheduledId());
    }

    @Test
    void fromAndToBoundsAreInclusive() {
        var p = new FixedPayment("x", 5, "X", Category.TRANSPORT, eur("10"), NOV, YearMonth.of(2026, 12), false);
        var calc = new BudgetCalculator(config(List.of(p), List.of()));
        assertTrue(calc.planFor(YearMonth.of(2026, 10)).isEmpty());
        assertEquals(1, calc.planFor(NOV).size());
        assertEquals(1, calc.planFor(YearMonth.of(2026, 12)).size());
        assertTrue(calc.planFor(YearMonth.of(2027, 1)).isEmpty());
    }

    @Test
    void extraPaymentsRaiseTheCategoryLimit() {
        var calc = new BudgetCalculator(config(List.of(
                new FixedPayment("x", 5, "One-off", Category.TRANSPORT, eur("50"), NOV, NOV, true)), List.of()));
        assertEquals(0, eur("250").compareTo(calc.effectiveLimit(Category.TRANSPORT, NOV)));
        assertEquals(0, eur("200").compareTo(calc.effectiveLimit(Category.TRANSPORT, NOV.plusMonths(1))));
        assertEquals(0, eur("650").compareTo(calc.totalLimits(NOV)));
    }

    @Test
    void reserveIsAnnualTotalOverTwelve() {
        var calc = new BudgetCalculator(config(List.of(), List.of(
                new AnnualItem("a", 5, "Insurance", Category.TRANSPORT, eur("600")),
                new AnnualItem("b", 7, "Prime", Category.RECREATION, eur("60")))));
        assertEquals(0, eur("55").compareTo(Money.round(calc.monthlyReserve())));
    }

    @Test
    void summaryCountsFixedVariableAndReserveSeparately() {
        var loan = new FixedPayment("loan", 30, "Loan", Category.TRANSPORT, eur("150"), null, null, false);
        var gym = new FixedPayment("gym", 1, "Gym", Category.RECREATION, eur("40"), null, null, false);
        var calc = new BudgetCalculator(config(List.of(loan, gym), List.of(
                new AnnualItem("a", 5, "Insurance", Category.TRANSPORT, eur("600")))));
        List<Movement> moves = List.of(
                new Movement("m1", LocalDate.of(2026, 10, 30), "Loan", Category.TRANSPORT, eur("150"), false, "loan@2026-11"),
                new Movement("m2", LocalDate.of(2026, 11, 2), "Groceries", Category.FOOD, eur("60"), false, null),
                new Movement("m3", LocalDate.of(2026, 11, 3), "Insurance", Category.TRANSPORT, eur("600"), true, null),
                new Movement("m4", LocalDate.of(2026, 11, 28), "Next cycle", Category.FOOD, eur("99"), false, null));

        var s = calc.summarize(NOV, moves, LocalDate.of(2026, 11, 9));

        assertEquals(eur("600.00"), s.budget());
        assertEquals(eur("210.00"), s.spent());          // reserve and next-cycle movements excluded
        assertEquals(eur("40.00"), s.pendingFixed());    // gym not paid yet
        assertEquals(eur("410.00"), s.variableBudget()); // 600 - 190 fixed
        assertEquals(eur("60.00"), s.variableSpent());
        assertEquals(eur("350.00"), s.left());
        assertEquals(19, s.daysLeft());                  // 9 Nov .. 27 Nov
        assertEquals(eur("18.42"), s.perDay());
        assertEquals(eur("1350.00"), s.projectedSavings()); // 2000 - 600 - 50
        assertEquals(CycleSummary.Status.ON_TRACK, s.status());

        var transport = s.envelopes().stream().filter(e -> e.category().equals(Category.TRANSPORT)).findFirst().orElseThrow();
        assertEquals(eur("150.00"), transport.fixedPaid());
        assertEquals(eur("50.00"), transport.remaining());

        // balance: 2000 - 300 savings - 50 reserve - 150 loan = 1500, then - 40 gym
        assertEquals(eur("1500.00"), s.plan().get(0).balanceAfter());
        assertTrue(s.plan().get(0).paid());
        assertEquals(eur("1460.00"), s.plan().get(1).balanceAfter());
    }

    @Test
    void weeksCarryOverUnspentAndOverspentMoney() {
        // 700 variable over a 28-day cycle (Feb 2026, payday 1) = 25/day = 175/week
        var cfg = new BudgetConfig(eur("1000"), BigDecimal.ZERO, 1, 2,
                Map.of(Category.FOOD, eur("700")), List.of(), List.of());
        var calc = new BudgetCalculator(cfg);
        List<Movement> moves = List.of(
                new Movement("a", LocalDate.of(2026, 2, 2), "x", Category.FOOD, eur("100"), false, null),
                new Movement("b", LocalDate.of(2026, 2, 10), "y", Category.FOOD, eur("300"), false, null));

        var weeks = calc.summarize(YearMonth.of(2026, 2), moves, LocalDate.of(2026, 2, 16)).weeks();

        assertEquals(4, weeks.size());
        assertEquals(eur("175.00"), weeks.get(0).limit());
        assertEquals(eur("75.00"), weeks.get(0).remaining());
        assertEquals(eur("250.00"), weeks.get(1).limit());           // 175 + 75 carried
        assertEquals(CycleSummary.WeekState.OVER, weeks.get(1).state());
        assertEquals(eur("125.00"), weeks.get(2).limit());           // 175 - 50 overspent
        assertTrue(weeks.get(2).current());
        assertEquals(CycleSummary.WeekState.PENDING, weeks.get(3).state());
    }

    @Test
    void overspendingVariableAheadOfTimeFlagsPace() {
        var cfg = new BudgetConfig(eur("1000"), BigDecimal.ZERO, 1, 2,
                Map.of(Category.FOOD, eur("600")), List.of(), List.of());
        var s = new BudgetCalculator(cfg).summarize(YearMonth.of(2026, 2),
                List.of(new Movement("a", LocalDate.of(2026, 2, 2), "x", Category.FOOD, eur("200"), false, null)),
                LocalDate.of(2026, 2, 7));
        assertEquals(CycleSummary.Status.AHEAD_OF_PACE, s.status());
    }

    @Test
    void envelopesCoverAllCategoriesAndShowAnnualPayments() {
        var claude = new AnnualItem("cl", 12, "Claude", Category.INFO_COMMUNICATION, eur("217.80"));
        var calc = new BudgetCalculator(config(List.of(), List.of(claude)));

        var nov = calc.summarize(NOV, List.of(), LocalDate.of(2026, 11, 9));
        assertEquals(13, nov.envelopes().size());
        var info = nov.envelopes().stream().filter(e -> e.category() == Category.INFO_COMMUNICATION).findFirst().orElseThrow();
        assertEquals(eur("217.80"), info.annualYearly());
        assertEquals(eur("18.15"), info.annualMonthly());
        assertEquals(eur("0.00"), info.annualDue(), "not due in November");
        assertFalse(info.isEmpty(), "a category with an annual payment is never empty");
        assertTrue(nov.annualDue().isEmpty());

        var dec = calc.summarize(YearMonth.of(2026, 12), List.of(), LocalDate.of(2026, 12, 3));
        var infoDec = dec.envelopes().stream().filter(e -> e.category() == Category.INFO_COMMUNICATION).findFirst().orElseThrow();
        assertEquals(eur("217.80"), infoDec.annualDue());
        assertEquals(1, dec.annualDue().size());
        assertEquals(nov.budget(), dec.budget(), "annual payments never change the monthly budget");
    }
}
