package cat.pessic.core.expense;

import static cat.pessic.core.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.Category;
import cat.pessic.core.income.Income;
import cat.pessic.core.profile.Profile;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Searching and filtering movements. */
class MovementFilterTest {

    @Test
    void searchIgnoresAccentsAndCombinesFilters() {
        Profile p = profile(List.of());
        p = ExpenseOps.addMovement(p, LocalDate.of(2026, 11, 2), "Cafè amb llet", Category.RESTAURANTS, eur("1.80"), false);
        p = ExpenseOps.addMovement(p, LocalDate.of(2026, 11, 3), "Súper", Category.FOOD, eur("54.10"), false);
        p = ExpenseOps.addMovement(p, LocalDate.of(2026, 10, 3), "Cafeteria", Category.RESTAURANTS, eur("4"), false);
        p = income(p, "2026-11-06", "10", Income.Kind.REFUND, Income.Destination.CATEGORY, Category.FOOD, null);
        p = income(p, "2026-11-07", "100", Income.Kind.BONUS, Income.Destination.SAVINGS, null, null);

        var cafe = new MovementFilter("CAFE", null, null, null, null).apply(p.movements(), p.incomes());
        assertEquals(List.of("Cafè amb llet", "Cafeteria"), cafe.stream().map(MovementFilter.Row::concept).toList());

        var novFood = new MovementFilter("", Category.FOOD, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
                .apply(p.movements(), p.incomes());
        assertEquals(2, novFood.size());
        var totals = MovementFilter.totals(novFood);
        assertEquals(eur("54.10"), totals.expenses());
        assertEquals(eur("10.00"), totals.refunds());
        assertEquals(eur("44.10"), totals.netSpent());

        var refunds = new MovementFilter(null, null, MovementFilter.Type.REFUND, null, null).apply(p.movements(), p.incomes());
        assertEquals(1, refunds.size());
        assertEquals(5, MovementFilter.ALL.apply(p.movements(), p.incomes()).size());
        assertEquals("Bonus o incentiu", new MovementFilter("bonus", null, null, null, null)
                .apply(p.movements(), p.incomes()).get(0).concept());
    }
}
