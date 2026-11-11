package cat.pessic.core.savings;

import static cat.pessic.core.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.expense.ExpenseOps;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.profile.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reserve for annual payments and the savings goals. */
class SavingsTest {

    @Test
    void reserveBalanceAndShortfall() {
        Profile p = SavingsOps.setReserveBalance(profile(List.of(CLAUDE)), eur("100"), LocalDate.of(2026, 11, 9));
        assertEquals(eur("100.00"), Pots.balance(p.ledger().reserve(), p));

        var shortfall = Pots.reserveShortfall(p, LocalDate.of(2026, 11, 9)).orElseThrow();
        assertEquals(DEC, shortfall.cycle());
        assertEquals(eur("99.65"), shortfall.missing()); // 100 + 18.15 - 217.80

        // an annual payment made from the reserve lowers it; one made before the balance was given does not
        Profile paid = p.withMovements(List.of(
                new Movement("old", LocalDate.of(2026, 11, 1), "Abans", Category.FOOD, eur("50"), true, null),
                new Movement("r", LocalDate.of(2026, 11, 30), "Claude", Category.INFO_COMMUNICATION, eur("217.80"), true,
                        "cl@2026")));
        assertEquals(eur("-117.80"), Pots.balance(paid.ledger().reserve(), paid));

        Profile adjusted = SavingsOps.adjustPotBalance(paid, Pot.RESERVE_ID, eur("20"), LocalDate.of(2026, 12, 2));
        assertEquals(eur("20.00"), Pots.balance(adjusted.ledger().reserve(), adjusted));
        assertTrue(Pots.reserveShortfall(profile(List.of(CLAUDE)), LocalDate.of(2026, 11, 9)).isEmpty(),
                "no forecast until the balance is known");
    }

    @Test
    void goalBalanceProgressAndSpendingFromIt() {
        Pot goal = Pot.newGoal("Viatge", eur("1200"), YearMonth.of(2027, 4), eur("100"), eur("200"), LocalDate.of(2026, 11, 1));
        Profile p = SavingsOps.saveGoal(profile(List.of()), goal);
        p = SavingsOps.addPotEntry(p, goal.id(), LocalDate.of(2026, 11, 10), eur("50"), PotEntry.Kind.CONTRIBUTION, "");
        p = income(p, "2026-11-11", "300", Income.Kind.BONUS, Income.Destination.GOAL, null, goal.id());
        p = ExpenseOps.addMovement(p, LocalDate.of(2026, 11, 12), "Bitllets", Category.TRANSPORT, eur("20"), false, goal.id());

        var progress = Pots.progress(goal, p, LocalDate.of(2026, 11, 12));
        assertEquals(eur("530.00"), progress.balance());
        assertEquals(44, progress.percent());
        assertEquals(6, progress.monthsLeft());
        assertEquals(eur("111.67"), progress.neededMonthly());
        assertFalse(progress.onTrack());

        var s = BudgetCalculator.of(p).summarize(NOV, p, LocalDate.of(2026, 11, 12));
        assertEquals(eur("0.00"), s.spent(), "paid from the goal, not from the month");
        assertEquals(eur("100.00"), s.goalsMonthly());
        assertEquals(eur("1300.00"), s.projectedSavings()); // 2000 - 600 - 100

        Profile deleted = SavingsOps.deleteGoal(p, goal.id());
        assertTrue(deleted.ledger().goals().isEmpty());
        assertEquals(Income.Destination.SAVINGS, deleted.incomes().get(0).destination());
        assertEquals(eur("20.00"), BudgetCalculator.of(deleted).summarize(NOV, deleted, LocalDate.of(2026, 11, 12)).spent());
    }
}
