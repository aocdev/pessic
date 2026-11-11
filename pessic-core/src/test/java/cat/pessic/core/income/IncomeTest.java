package cat.pessic.core.income;

import static cat.pessic.core.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.profile.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Extra incomes, refunds and extra paychecks. */
class IncomeTest {

    @Test
    void extraIncomeRaisesTheBudgetAndRefundsLowerTheirCategory() {
        Profile p = spend(profile(List.of()), "2026-11-02", Category.FOOD, "80");
        p = income(p, "2026-11-05", "150", Income.Kind.BONUS, Income.Destination.BUDGET, null, null);
        p = income(p, "2026-11-06", "30", Income.Kind.REFUND, Income.Destination.CATEGORY, Category.FOOD, null);
        p = income(p, "2026-11-07", "2000", Income.Kind.EXTRA_PAY, Income.Destination.SAVINGS, null, null);
        var s = BudgetCalculator.of(p).summarize(NOV, p, LocalDate.of(2026, 11, 9));

        assertEquals(eur("750.00"), s.budget());
        assertEquals(eur("150.00"), s.extraBudget());
        assertEquals(eur("50.00"), s.spent());
        assertEquals(eur("30.00"), s.refunds());
        assertEquals(eur("2150.00"), s.newMoney());
        assertEquals(eur("700.00"), s.left());
        assertEquals(eur("1400.00"), s.projectedSavings(), "extra money does not change the plan");
        var food = s.envelopes().stream().filter(e -> e.category() == Category.FOOD).findFirst().orElseThrow();
        assertEquals(eur("50.00"), food.variableSpent());
        assertEquals(eur("250.00"), food.remaining());
        assertEquals(3, s.incomes().size());
    }

    @Test
    void refundRulesAreChecked() {
        Profile p = profile(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> income(p, "2026-11-06", "30", Income.Kind.REFUND, Income.Destination.BUDGET, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> income(p, "2026-11-06", "30", Income.Kind.BONUS, Income.Destination.GOAL, null, "nope"));
        assertThrows(IllegalArgumentException.class,
                () -> income(p, "2026-11-06", "0", Income.Kind.BONUS, Income.Destination.BUDGET, null, null));
    }

    @Test
    void extraPaychecksArriveInTheCycleOfTheirPayday() {
        Paychecks fourteen = new Paychecks(14, null, List.of(12, 6));
        assertEquals(List.of(6, 12), fourteen.months());
        // payday 28: the June payroll arrives on 28 June, which starts the "July" cycle
        assertEquals(YearMonth.of(2026, 6), fourteen.extraIn(YearMonth.of(2026, 7), 28).orElseThrow());
        assertTrue(fourteen.extraIn(YearMonth.of(2026, 6), 28).isEmpty());
        assertEquals(YearMonth.of(2026, 12), fourteen.extraIn(YearMonth.of(2027, 1), 28).orElseThrow());
        assertEquals(YearMonth.of(2026, 6), fourteen.extraIn(YearMonth.of(2026, 6), 1).orElseThrow());
        assertTrue(Paychecks.TWELVE.extraIn(YearMonth.of(2026, 7), 28).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Paychecks(13, null, null));

        Profile p = IncomeOps.updatePaychecks(profile(List.of()), new Paychecks(14, eur("1800"), List.of(6, 12)));
        var calc = BudgetCalculator.of(p);
        var expected = calc.summarize(YearMonth.of(2027, 1), p, LocalDate.of(2026, 12, 29)).expectedPaycheck();
        assertEquals(eur("1800.00"), expected.amount());
        assertFalse(expected.received());

        p = IncomeOps.addIncome(p, LocalDate.of(2026, 12, 28), "Paga de Nadal", expected.amount(), Income.Kind.EXTRA_PAY,
                Income.Destination.SAVINGS, null, null, expected.scheduledId());
        Profile again = IncomeOps.addIncome(p, LocalDate.of(2026, 12, 28), "Paga de Nadal", expected.amount(),
                Income.Kind.EXTRA_PAY, Income.Destination.SAVINGS, null, null, expected.scheduledId());
        assertEquals(1, again.incomes().size(), "recording it twice does nothing");
        assertTrue(BudgetCalculator.of(p).summarize(YearMonth.of(2027, 1), p, LocalDate.of(2026, 12, 29))
                .expectedPaycheck().received());
    }
}
