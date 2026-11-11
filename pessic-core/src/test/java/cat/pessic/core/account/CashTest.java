package cat.pessic.core.account;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.ExpenseOps;
import cat.pessic.core.history.History;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.monthclose.MonthClose;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.FixedPayment;
import cat.pessic.core.payment.PaymentOps;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.core.savings.Pots;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Estimated balance of the day-to-day account and the forecast until payday. */
class CashTest {

    static final YearMonth NOV = YearMonth.of(2026, 11); // payday 28: 28 Oct - 27 Nov
    static final YearMonth DEC = YearMonth.of(2026, 12);
    static final AnnualItem CLAUDE = new AnnualItem("cl", 12, "Claude", Category.INFO_COMMUNICATION, eur("217.80"));
    static final FixedPayment GYM = new FixedPayment("gym", 15, "Gym", Category.RECREATION, eur("40"), null, null, false);
    static final FixedPayment LOAN = new FixedPayment("loan", 25, "Préstec", Category.TRANSPORT, eur("150"), null, null, false);

    static BigDecimal eur(String v) {
        return Money.of(v);
    }

    static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    /** Income 2000, automatic savings 300, payday 28, limits 600, reserve 18,15 a month. */
    static Profile profile(List<FixedPayment> plan) {
        var cfg = new BudgetConfig(eur("2000"), eur("300"), 28, 2, null,
                Map.of(Category.FOOD, eur("300"), Category.TRANSPORT, eur("200"), Category.RECREATION, eur("100")),
                plan, List.of(CLAUDE));
        return new Profile("p", "Marta", cfg, List.of());
    }

    static Profile withAccount(Profile p, String amount, String date) {
        return AccountOps.setAccountBalance(p, eur(amount), d(date));
    }

    static Profile spend(Profile p, String date, String amount, boolean fromReserve) {
        return ExpenseOps.addMovement(p, d(date), "Despesa", Category.FOOD, eur(amount), fromReserve);
    }

    @Test
    void noAccountNoEstimate() {
        assertNull(Cash.estimate(profile(List.of()), d("2026-11-12")));
        assertNull(Cash.forecast(profile(List.of()), d("2026-11-12")));
    }

    @Test
    void expensesAndPaydays() {
        Profile p = withAccount(profile(List.of()), "500", "2026-11-10");
        p = spend(p, "2026-11-10", "999", false); // same day as the balance: already in it
        p = spend(p, "2026-11-12", "50", false);
        p = spend(p, "2026-11-13", "20", true);    // paid from the reserve
        assertEquals(eur("500.00"), Cash.estimate(p, d("2026-11-10")));
        assertEquals(eur("500.00"), Cash.estimate(p, d("2026-11-01")), "before the opening day: the opening");
        assertEquals(eur("450.00"), Cash.estimate(p, d("2026-11-27")));
        // payday: + 2000 salary - 300 automatic savings - 18.15 reserve
        assertEquals(eur("2131.85"), Cash.estimate(p, d("2026-11-28")));
        assertEquals(eur("3813.70"), Cash.estimate(p, d("2026-12-28")));
    }

    @Test
    void incomesThatStayInTheAccount() {
        Pot goal = Pot.newGoal("Viatge", null, null, eur("0"), eur("0"), d("2026-11-01"));
        Profile p = SavingsOps.saveGoal(withAccount(profile(List.of()), "500", "2026-11-10"), goal);
        p = IncomeOps.addIncome(p, d("2026-11-11"), "", eur("100"), Income.Kind.BONUS, Income.Destination.BUDGET, null, null, null);
        p = IncomeOps.addIncome(p, d("2026-11-11"), "", eur("50"), Income.Kind.OTHER, Income.Destination.SAVINGS, null, null, null);
        p = IncomeOps.addIncome(p, d("2026-11-11"), "", eur("10"), Income.Kind.REFUND, Income.Destination.CATEGORY,
                Category.FOOD, null, null);
        p = IncomeOps.addIncome(p, d("2026-11-11"), "", eur("70"), Income.Kind.BONUS, Income.Destination.RESERVE, null, null, null);
        p = IncomeOps.addIncome(p, d("2026-11-11"), "", eur("80"), Income.Kind.BONUS, Income.Destination.GOAL, null, goal.id(), null);
        assertEquals(eur("660.00"), Cash.estimate(p, d("2026-11-11")));

        // money moved by hand into a pot leaves the account; taking it out brings it back
        p = SavingsOps.addPotEntry(p, goal.id(), d("2026-11-12"), eur("200"), PotEntry.Kind.CONTRIBUTION, "");
        p = SavingsOps.addPotEntry(p, goal.id(), d("2026-11-13"), eur("-30"), PotEntry.Kind.WITHDRAWAL, "");
        assertEquals(eur("490.00"), Cash.estimate(p, d("2026-11-13")));
    }

    @Test
    void closedCyclesUseTheRealContributions() {
        Profile p = withAccount(profile(List.of()), "500", "2026-11-10");
        p = spend(p, "2026-11-12", "1", false);
        assertEquals(eur("2180.85"), Cash.estimate(p, d("2026-12-27")));
        Profile closed = MonthClose.close(p, DEC, Map.of(Pot.RESERVE_ID, eur("10")), CycleClose.Leftover.SAVINGS, null,
                d("2026-12-28"));
        // the December payday (28 Nov) now takes 10 to the reserve instead of the planned 18.15
        assertEquals(eur("2189.00"), Cash.estimate(closed, d("2026-12-27")));
    }

    @Test
    void adjustingRecordsTheDifference() {
        Profile p = spend(withAccount(profile(List.of()), "500", "2026-11-10"), "2026-11-12", "50", false);
        Profile adjusted = AccountOps.adjustAccount(p, eur("430"), d("2026-11-20"), null);
        assertEquals(eur("430.00"), Cash.estimate(adjusted, d("2026-11-20")));
        assertEquals(eur("-20.00"), adjusted.ledger().accountEntries().get(0).amount());
        assertSame(adjusted, AccountOps.adjustAccount(adjusted, eur("430"), d("2026-11-20"), null), "no difference, no entry");
        // on the opening day itself it simply replaces the opening balance
        Profile same = AccountOps.adjustAccount(p, eur("480"), d("2026-11-10"), null);
        assertEquals(eur("480.00"), same.ledger().account().opening());
        assertTrue(same.ledger().accountEntries().isEmpty());
        // restarting the balance forgets earlier corrections
        Profile restarted = AccountOps.setAccountBalance(adjusted, eur("100"), d("2026-11-21"));
        assertTrue(restarted.ledger().accountEntries().isEmpty());
    }

    @Test
    void forecastUntilPayday() {
        Profile p = withAccount(profile(List.of(GYM, LOAN)), "100", "2026-11-10");
        p = spend(p, "2026-11-12", "50", false);
        LocalDate today = d("2026-11-12");
        Cash.Forecast f = Cash.forecast(p, today);
        assertEquals(eur("50.00"), f.today());
        assertEquals(List.of("Gym", "Préstec", "Resta del pressupost, si el gastes tot"),
                f.lines().stream().map(Cash.Line::concept).toList());
        assertEquals(eur("10.00"), f.balanceAfter("gym@2026-11"));
        assertEquals(eur("-140.00"), f.balanceAfter("loan@2026-11"));
        // left to spend: 600 - 50 - 190 pending = 360
        assertEquals(eur("-500.00"), f.beforePayday());
        assertTrue(f.goesNegative());
        assertEquals(d("2026-11-27"), f.lowestDate());

        Profile paid = PaymentOps.markPaid(p, BudgetCalculator.of(p).planFor(NOV).get(0), BudgetCalculator.of(p).cycle(NOV), today);
        Cash.Forecast after = Cash.forecast(paid, today);
        assertEquals(eur("10.00"), after.today());
        assertNull(after.balanceAfter("gym@2026-11"), "paid: already in today's balance");
        assertEquals(eur("-500.00"), after.beforePayday(), "paying early does not change where you end");

        // an overdue payment is expected today
        Cash.Forecast late = Cash.forecast(p, d("2026-11-20"));
        assertEquals(d("2026-11-20"), late.lines().get(0).date());
    }

    @Test
    void reserveNeededNowAvoidsAnyShortfall() {
        Profile p = profile(List.of());
        LocalDate today = d("2026-11-12");
        BigDecimal needed = Pots.reserveNeededNow(p, today);
        assertEquals(eur("199.65"), needed); // 217.80 in December minus one month of 18.15
        assertTrue(Pots.reserveShortfall(SavingsOps.setReserveBalance(p, needed, today), today).isEmpty());
        assertTrue(Pots.reserveShortfall(SavingsOps.setReserveBalance(p, eur("199.64"), today), today).isPresent());
    }

    @Test
    void fundingPotsFromTheAccount() {
        Pot goal = Pot.newGoal("Viatge", null, null, eur("0"), eur("0"), d("2026-11-01"));
        Profile p = SavingsOps.saveGoal(withAccount(profile(List.of()), "1000", "2026-11-10"), goal);
        Profile funded = AccountOps.fundPots(p, Map.of(Pot.RESERVE_ID, eur("199.65"), goal.id(), eur("100"),
                "unused", BigDecimal.ZERO), d("2026-11-10"));
        assertEquals(eur("700.35"), Cash.estimate(funded, d("2026-11-10")));
        assertEquals(eur("700.35"), Cash.estimate(funded, d("2026-11-20")), "not taken away twice");
        assertEquals(eur("199.65"), Pots.balance(funded.ledger().reserve(), funded));
        assertEquals(eur("100.00"), Pots.balance(funded.ledger().pot(goal.id()).orElseThrow(), funded));

        Profile later = AccountOps.fundPots(p, Map.of(goal.id(), eur("100")), d("2026-11-15"));
        assertEquals(eur("1000.00"), later.ledger().account().opening());
        assertEquals(eur("900.00"), Cash.estimate(later, d("2026-11-15")));
        assertThrows(IllegalArgumentException.class,
                () -> AccountOps.fundPots(profile(List.of()), Map.of(Pot.RESERVE_ID, eur("1")), d("2026-11-10")));
    }

    @Test
    void monthCloseChecksTheBalanceAndReopenUndoesIt() {
        Profile p = spend(withAccount(profile(List.of()), "500", "2026-11-10"), "2026-11-12", "50", false);
        LocalDate today = d("2026-12-01");
        Profile closed = MonthClose.close(p, NOV, Map.of(), CycleClose.Leftover.SAVINGS, null, eur("2000"), today);
        // estimate: 500 - 50 + 1681.85 on the 28 Nov payday = 2131.85
        assertEquals(eur("2000.00"), Cash.estimate(closed, today));
        History.Row nov = History.last(closed, today, 12).get(0);
        assertEquals(NOV, nov.cycle());
        assertEquals(eur("-131.85"), nov.bankDifference());

        Profile reopened = MonthClose.reopen(closed, NOV);
        assertTrue(reopened.ledger().accountEntries().isEmpty());
        assertEquals(eur("2131.85"), Cash.estimate(reopened, today));
        assertNull(History.last(p, today, 12).get(0).bankDifference());
    }
}
