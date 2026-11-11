package cat.pessic.core.monthclose;

import static cat.pessic.core.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.history.History;
import cat.pessic.core.income.Income;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.Pots;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Closing a month: contributions, the leftover, the snapshot and the history. */
class MonthCloseTest {

    Profile closable() {
        Pot goal = new Pot("g1", Pot.Kind.GOAL, "Viatge", null, null, eur("100"), eur("0"), LocalDate.of(2026, 10, 1), false);
        Profile p = SavingsOps.saveGoal(profile(List.of(CLAUDE)), goal);
        p = SavingsOps.setReserveBalance(p, BigDecimal.ZERO, LocalDate.of(2026, 10, 1));
        p = spend(p, "2026-11-02", Category.FOOD, "80");
        return income(p, "2026-11-05", "150", Income.Kind.BONUS, Income.Destination.BUDGET, null, null);
    }

    @Test
    void closingAMonthPostsContributionsAndCarriesTheLeftover() {
        Profile p = closable();
        LocalDate today = LocalDate.of(2026, 12, 1);
        assertEquals(List.of(NOV), MonthClose.pending(p, today));

        var draft = MonthClose.draft(p, NOV, today);
        assertEquals(eur("670.00"), draft.leftover());
        assertEquals(List.of(eur("18.15"), eur("100.00")), draft.contributions().stream().map(MonthClose.Contribution::planned).toList());

        Profile closed = MonthClose.close(p, NOV, Map.of(Pot.RESERVE_ID, eur("18.15"), "g1", eur("100")),
                CycleClose.Leftover.CARRY, null, today);
        assertTrue(MonthClose.pending(closed, today).isEmpty());
        assertEquals(eur("18.15"), Pots.balance(closed.ledger().reserve(), closed));
        assertEquals(eur("100.00"), Pots.balance(closed.ledger().pot("g1").orElseThrow(), closed));

        CycleClose snap = closed.ledger().close(NOV).orElseThrow();
        assertEquals(eur("2000.00"), snap.income());
        assertEquals(eur("150.00"), snap.extra());
        assertEquals(eur("750.00"), snap.budget());
        assertEquals(eur("80.00"), snap.spent());
        assertEquals(eur("788.15"), snap.toPots()); // 18.15 + 100 + 670 carried over
        assertEquals(eur("1281.85"), snap.saved());
        assertEquals(eur("80.00"), snap.categories().get(Category.FOOD).spent());

        var dec = BudgetCalculator.of(closed).summarize(DEC, closed, today);
        assertEquals(eur("670.00"), dec.extraBudget());
        assertEquals(eur("1270.00"), dec.budget());
        assertEquals(eur("0.00"), dec.newMoney(), "a carry-over is not new money");

        var history = History.last(closed, today, 12);
        assertEquals(List.of(NOV, DEC), history.stream().map(History.Row::cycle).toList(), "starts with the first data");
        assertTrue(history.get(0).closed());
        assertFalse(history.get(1).closed());
        assertTrue(history.get(1).current());
        assertEquals(eur("670.00"), history.get(1).extra());
        assertEquals(List.of(DEC), History.last(closed, today, 1).stream().map(History.Row::cycle).toList());

        assertThrows(IllegalArgumentException.class, () -> MonthClose.close(closed, NOV, Map.of(),
                CycleClose.Leftover.SAVINGS, null, today));
        assertThrows(IllegalArgumentException.class, () -> MonthClose.close(closed, DEC, Map.of(),
                CycleClose.Leftover.SAVINGS, null, today), "the cycle has not ended");

        Profile reopened = MonthClose.reopen(closed, NOV);
        assertEquals(List.of(NOV), MonthClose.pending(reopened, today));
        assertEquals(eur("0.00"), Pots.balance(reopened.ledger().reserve(), reopened));
        assertEquals(p.incomes().size(), reopened.incomes().size());
    }

    @Test
    void leftoverToAGoalAndOverspentMonths() {
        LocalDate today = LocalDate.of(2026, 12, 1);
        Profile toGoal = MonthClose.close(closable(), NOV, Map.of(), CycleClose.Leftover.GOAL, "g1", today);
        assertEquals(eur("670.00"), Pots.balance(toGoal.ledger().pot("g1").orElseThrow(), toGoal));

        Profile over = spend(closable(), "2026-11-03", Category.TRANSPORT, "1000");
        Profile closed = MonthClose.close(over, NOV, Map.of(), CycleClose.Leftover.GOAL, "g1", today);
        CycleClose snap = closed.ledger().close(NOV).orElseThrow();
        assertEquals(eur("-330.00"), snap.leftover());
        assertEquals(CycleClose.Leftover.SAVINGS, snap.leftoverTo(), "nothing to move when the month went over");
        assertEquals(eur("0.00"), Pots.balance(closed.ledger().pot("g1").orElseThrow(), closed));
    }

    @Test
    void contributionsAreNotOfferedForMonthsBeforeTheBalanceWasGiven() {
        Profile p = SavingsOps.setReserveBalance(closable(), eur("500"), LocalDate.of(2026, 11, 30));
        var draft = MonthClose.draft(p, NOV, LocalDate.of(2026, 12, 1));
        assertTrue(draft.contributions().stream().noneMatch(c -> c.pot().isReserve()));
    }
}
