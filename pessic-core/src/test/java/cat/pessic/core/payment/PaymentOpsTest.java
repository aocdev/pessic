package cat.pessic.core.payment;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.profile.NewProfile;
import cat.pessic.core.profile.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Fixed and annual payments: planning them and marking them as paid. */
class PaymentOpsTest {

    static final YearMonth NOV = YearMonth.of(2026, 11);

    final Profile base = NewProfile.create("Test", Money.of("2000"), 28, 2, null, false);

    @Test
    void markPaidDatesEarlyPaymentsTodayAndIsIdempotent() {
        Profile p = PaymentOps.saveFixed(base, new FixedPayment("rent", 1, "Lloguer", Category.HOUSING,
                Money.of("800"), null, null, false));
        BudgetCalculator calc = new BudgetCalculator(p.config());
        ScheduledPayment rent = calc.planFor(NOV).get(0);           // due 1 Nov
        LocalDate today = LocalDate.of(2026, 10, 30);               // cycle started 28 Oct

        Profile paid = PaymentOps.markPaid(p, rent, calc.cycle(NOV), today);
        assertEquals(today, paid.movements().get(0).date());
        assertEquals("rent@2026-11", paid.movements().get(0).scheduledId());
        assertSame(paid, PaymentOps.markPaid(paid, rent, calc.cycle(NOV), today));

        var s = calc.summarize(NOV, paid.movements(), today);
        assertTrue(s.plan().get(0).paid());
        assertEquals(Money.of("0"), s.pendingFixed());

        assertTrue(PaymentOps.unmarkPaid(paid, "rent@2026-11").movements().isEmpty());
    }

    @Test
    void lateMarkUsesTheDueDate() {
        Profile p = PaymentOps.saveFixed(base, new FixedPayment("gym", 1, "Gym", Category.RECREATION,
                Money.of("40"), null, null, false));
        BudgetCalculator calc = new BudgetCalculator(p.config());
        Profile paid = PaymentOps.markPaid(p, calc.planFor(NOV).get(0), calc.cycle(NOV), LocalDate.of(2026, 11, 20));
        assertEquals(LocalDate.of(2026, 11, 1), paid.movements().get(0).date());
    }

    @Test
    void saveFixedReplacesById() {
        var f = new FixedPayment("x", 5, "Llum", Category.HOUSING, Money.of("60"), null, null, false);
        Profile p = PaymentOps.saveFixed(PaymentOps.saveFixed(base, f),
                new FixedPayment("x", 6, "Llum", Category.HOUSING, Money.of("65"), null, null, false));
        assertEquals(1, p.config().plan().size());
        assertEquals(Money.of("65"), p.config().plan().get(0).amount());
        assertTrue(PaymentOps.deleteFixed(p, "x").config().plan().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PaymentOps.saveFixed(base,
                new FixedPayment("y", 5, "Bad", Category.TRANSPORT, Money.of("1"), NOV, NOV.minusMonths(1), false)));
    }

    @Test
    void annualItemsFeedTheReserve() {
        Profile p = PaymentOps.saveAnnual(base, new AnnualItem("a", 5, "Assegurança", Category.TRANSPORT, Money.of("360")));
        assertEquals(Money.of("30.00"), Money.round(new BudgetCalculator(p.config()).monthlyReserve()));
        assertTrue(PaymentOps.deleteAnnual(p, "a").config().annuals().isEmpty());
    }

    @Test
    void endingAFixedPaymentKeepsEarlierCycles() {
        var netflix = new FixedPayment("nf", 12, "Netflix", Category.RECREATION, Money.of("13.99"), null, null, false);
        Profile p = PaymentOps.endFixed(PaymentOps.saveFixed(base, netflix), "nf", NOV.minusMonths(1));
        var calc = new BudgetCalculator(p.config());
        assertEquals(1, calc.planFor(NOV.minusMonths(1)).size(), "October still has it");
        assertTrue(calc.planFor(NOV).isEmpty(), "gone from November on");
        assertTrue(calc.planFor(NOV.plusMonths(6)).isEmpty());
        assertEquals(NOV.minusMonths(1), p.config().plan().get(0).to());
    }

    @Test
    void endingKeepsAnEarlierEndAndDeletesPaymentsThatNeverStarted() {
        var shortOne = new FixedPayment("s", 1, "X", Category.TRANSPORT, Money.of("10"), null, NOV.minusMonths(3), false);
        Profile p = PaymentOps.saveFixed(base, shortOne);
        assertSame(p, PaymentOps.endFixed(p, "s", NOV));

        var future = new FixedPayment("f", 1, "Y", Category.TRANSPORT, Money.of("10"), NOV.plusMonths(2), null, false);
        Profile q = PaymentOps.saveFixed(base, future);
        assertTrue(PaymentOps.endFixed(q, "f", NOV).config().plan().isEmpty());
    }

    @Test
    void annualPaymentIsPaidFromTheReserveOncePerYear() {
        var claude = new AnnualItem("cl", 12, "Claude", Category.INFO_COMMUNICATION, Money.of("217.80"));
        Profile p = PaymentOps.saveAnnual(base, claude);
        var calc = new BudgetCalculator(p.config());
        YearMonth dec = YearMonth.of(2026, 12);
        var line = calc.annualDue(dec, p.movements()).get(0);
        assertFalse(line.paid());

        LocalDate today = LocalDate.of(2026, 12, 3);  // cycle "Desembre" runs 28 Nov - 27 Dec
        Profile paid = PaymentOps.markAnnualPaid(p, line, calc.cycle(dec), today);
        Movement m = paid.movements().get(0);
        assertTrue(m.fromReserve());
        assertTrue(m.isAnnual());
        assertFalse(m.isFixed());
        assertEquals("cl@2026", m.scheduledId());
        assertEquals(today, m.date());
        assertSame(paid, PaymentOps.markAnnualPaid(paid, line, calc.cycle(dec), today));

        var s = calc.summarize(dec, paid.movements(), today);
        assertTrue(s.annualDue().get(0).paid());
        assertEquals(Money.of("0.00"), s.spent(), "paid from the reserve, not from the month");
        assertTrue(calc.annualDue(dec.plusYears(1), paid.movements()).stream().noneMatch(CycleSummary.AnnualLine::paid),
                "next year it is due again");
        assertTrue(PaymentOps.unmarkPaid(paid, "cl@2026").movements().isEmpty());
    }
}
