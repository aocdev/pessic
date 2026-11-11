package cat.pessic.core.monthclose;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.core.account.AccountOps;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.history.History;
import cat.pessic.core.income.Income;
import cat.pessic.core.payment.ScheduledPayment;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Closing a cycle once it has ended: posting the monthly contributions to the reserve and the goals,
 * deciding where the leftover goes, and keeping a {@link CycleClose snapshot} for the history.
 */
public final class MonthClose {
    private MonthClose() {}

    /** A pot that can receive its monthly contribution when the cycle is closed. */
    public record Contribution(Pot pot, BigDecimal planned) {}

    /**
     * What closing the cycle would do, for the close dialog.
     *
     * @param leftover    budget − spent; negative when the month went over
     * @param unpaidFixed fixed payments of the cycle never marked as paid
     */
    public record Draft(YearMonth cycle, CycleSummary summary, BigDecimal leftover, List<Contribution> contributions,
                        List<ScheduledPayment> unpaidFixed) {}

    /** Ended cycles not closed yet, oldest first: from the first one with data, or after the last close. */
    public static List<YearMonth> pending(Profile p, LocalDate today) {
        BudgetCalculator calc = new BudgetCalculator(p.config());
        YearMonth current = calc.cycleContaining(today).key();
        Optional<YearMonth> lastClose = p.ledger().closes().stream().map(CycleClose::cycle).max(YearMonth::compareTo);
        Optional<LocalDate> first = java.util.stream.Stream.concat(
                p.movements().stream().map(Movement::date), p.incomes().stream().map(Income::date))
                .min(LocalDate::compareTo);
        if (first.isEmpty()) return List.of();
        YearMonth from = calc.cycleContaining(first.get()).key();
        if (lastClose.isPresent() && !lastClose.get().isBefore(from)) from = lastClose.get().plusMonths(1);
        List<YearMonth> result = new ArrayList<>();
        for (YearMonth k = from; k.isBefore(current); k = k.plusMonths(1)) {
            if (p.ledger().close(k).isEmpty()) result.add(k);
        }
        return result;
    }

    public static Draft draft(Profile p, YearMonth key, LocalDate today) {
        BudgetCalculator calc = BudgetCalculator.of(p);
        CycleSummary s = calc.summarize(key, p, today);
        LocalDate end = s.cycle().end();
        List<Contribution> contributions = new ArrayList<>();
        Pot reserve = p.ledger().reserve();
        BigDecimal reserveMonthly = Money.round(calc.monthlyReserve());
        // a balance given after the cycle ended already includes its contribution
        if (reserveMonthly.signum() > 0 && (!reserve.openingSet() || reserve.counts(end))) {
            contributions.add(new Contribution(reserve, reserveMonthly));
        }
        for (Pot g : p.ledger().activeGoals()) {
            if (g.counts(end)) contributions.add(new Contribution(g, g.monthly()));
        }
        List<ScheduledPayment> unpaid = s.plan().stream().filter(l -> !l.paid()).map(CycleSummary.PlanLine::payment).toList();
        return new Draft(key, s, Money.round(s.budget().subtract(s.spent())), contributions, unpaid);
    }

    /**
     * Closes the cycle.
     *
     * @param amounts    contribution per pot id (missing or zero: nothing is posted for that pot)
     * @param leftoverTo where a positive leftover goes
     * @param goalId     the goal, when {@code leftoverTo} is {@link CycleClose.Leftover#GOAL}
     */
    public static Profile close(Profile p, YearMonth key, Map<String, BigDecimal> amounts, CycleClose.Leftover leftoverTo,
                                String goalId, LocalDate today) {
        return close(p, key, amounts, leftoverTo, goalId, null, today);
    }

    /**
     * Closes the cycle and, when {@code actualBalance} is given, checks the account against it: the
     * difference from the estimate is recorded as a correction of this cycle (expenses not recorded).
     */
    public static Profile close(Profile p, YearMonth key, Map<String, BigDecimal> amounts, CycleClose.Leftover leftoverTo,
                                String goalId, BigDecimal actualBalance, LocalDate today) {
        Profile closed = closeWithoutCheck(p, key, amounts, leftoverTo, goalId, today);
        return actualBalance == null ? closed : AccountOps.adjustAccount(closed, actualBalance, today, key);
    }

    private static Profile closeWithoutCheck(Profile p, YearMonth key, Map<String, BigDecimal> amounts,
                                             CycleClose.Leftover leftoverTo, String goalId, LocalDate today) {
        if (p.ledger().close(key).isPresent()) throw new IllegalArgumentException("already closed: " + key);
        Draft d = draft(p, key, today);
        if (!d.summary().cycle().end().isBefore(today)) throw new IllegalArgumentException("the cycle has not ended: " + key);
        LocalDate end = d.summary().cycle().end();
        Profile q = p;
        BigDecimal toPots = BigDecimal.ZERO;
        for (Contribution c : d.contributions()) {
            BigDecimal a = amounts.getOrDefault(c.pot().id(), BigDecimal.ZERO);
            if (a.signum() < 0) throw new IllegalArgumentException("contribution must be >= 0");
            if (a.signum() == 0) continue;
            q = post(q, c.pot().id(), "c-" + c.pot().id() + "@" + key, end, a, PotEntry.Kind.CONTRIBUTION, key);
            toPots = toPots.add(a);
        }
        // money that came in this cycle and went straight into a pot
        for (Income i : d.summary().incomes()) if (i.potId() != null) toPots = toPots.add(i.amount());

        BigDecimal leftover = d.leftover();
        CycleClose.Leftover dest = leftover.signum() > 0 ? leftoverTo : CycleClose.Leftover.SAVINGS;
        if (dest == null) dest = CycleClose.Leftover.SAVINGS;
        switch (dest) {
            case GOAL -> {
                if (goalId == null) throw new IllegalArgumentException("choose a goal for the leftover");
                q = post(q, goalId, "l-" + goalId + "@" + key, end, leftover, PotEntry.Kind.LEFTOVER, key);
                toPots = toPots.add(leftover);
            }
            case RESERVE -> {
                q = post(q, Pot.RESERVE_ID, "l-" + Pot.RESERVE_ID + "@" + key, end, leftover, PotEntry.Kind.LEFTOVER, key);
                toPots = toPots.add(leftover);
            }
            case CARRY -> {
                LocalDate next = new BudgetCalculator(p.config()).cycle(key.plusMonths(1)).start();
                List<Income> incomes = new ArrayList<>(q.incomes());
                incomes.add(new Income("carry@" + key, next, t("monthclose.note.carry", key), leftover, Income.Kind.CARRY,
                        Income.Destination.BUDGET, null, null, carryId(key)));
                q = q.withLedger(q.ledger().withIncomes(incomes));
                // set aside for next month: it shows up there as extra money
                toPots = toPots.add(leftover);
            }
            case SAVINGS -> { }
        }

        Map<Category, CycleClose.CategoryLine> cats = new EnumMap<>(Category.class);
        for (CycleSummary.Envelope e : d.summary().envelopes()) {
            cats.put(e.category(), new CycleClose.CategoryLine(e.limit(), e.fixedPaid().add(e.variableSpent())));
        }
        BigDecimal extra = History.extra(d.summary());
        CycleClose snapshot = new CycleClose(key, today, d.summary().income(), extra, d.summary().budget(),
                d.summary().spent(), toPots, leftover, dest, cats);
        List<CycleClose> closes = new ArrayList<>(q.ledger().closes());
        closes.add(snapshot);
        closes.sort(java.util.Comparator.comparing(CycleClose::cycle));
        return q.withLedger(q.ledger().withCloses(closes));
    }

    /**
     * Undoes a close: removes its snapshot, the contributions and leftover it posted, any carry-over
     * and the account check made with it.
     */
    public static Profile reopen(Profile p, YearMonth key) {
        Ledger l = p.ledger();
        return p.withLedger(l
                .withIncomes(l.incomes().stream().filter(i -> !carryId(key).equals(i.scheduledId())).toList())
                .withEntries(l.entries().stream().filter(e -> !key.equals(e.cycle())).toList())
                .withCloses(l.closes().stream().filter(c -> !c.cycle().equals(key)).toList())
                .withAccountEntries(l.accountEntries().stream().filter(e -> !key.equals(e.cycle())).toList()));
    }

    static String carryId(YearMonth key) {
        return "carry@" + key;
    }

    private static Profile post(Profile p, String potId, String id, LocalDate date, BigDecimal amount,
                                PotEntry.Kind kind, YearMonth key) {
        Profile q = SavingsOps.addPotEntry(p, potId, date, amount, kind, t("monthclose.note.entry", kind.label(), key));
        // replace the random id with a stable one linked to the cycle
        List<PotEntry> entries = new ArrayList<>(q.ledger().entries());
        PotEntry last = entries.remove(entries.size() - 1);
        entries.add(new PotEntry(id, last.potId(), last.date(), last.amount(), last.kind(), last.note(), key));
        return q.withLedger(q.ledger().withEntries(entries));
    }
}
