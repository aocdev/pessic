package cat.pessic.core.savings;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.core.common.Ids;
import cat.pessic.core.common.Lists;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The reserve for annual payments and the savings goals ("guardioles"). */
public final class SavingsOps {
    private SavingsOps() {}

    /** Says how much the annual reserve holds on {@code date}; from then on its balance is tracked. */
    public static Profile setReserveBalance(Profile p, BigDecimal amount, LocalDate date) {
        if (amount.signum() < 0) throw new IllegalArgumentException("balance must be >= 0");
        return savePot(p, p.ledger().reserve().withOpening(amount, date));
    }

    /** Adds the goal, or replaces the one with the same id. */
    public static Profile saveGoal(Profile p, Pot goal) {
        if (goal.isReserve()) throw new IllegalArgumentException("not a goal");
        return savePot(p, goal);
    }

    public static Profile archiveGoal(Profile p, String id, boolean archived) {
        return savePot(p, requireGoal(p, id).withArchived(archived));
    }

    /**
     * Deletes a goal with its entries. Expenses paid from it are kept but count against the month
     * from then on, and incomes sent to it go to savings.
     */
    public static Profile deleteGoal(Profile p, String id) {
        requireGoal(p, id);
        Ledger l = p.ledger();
        List<Income> incomes = l.incomes().stream().map(i -> id.equals(i.potId())
                ? new Income(i.id(), i.date(), i.concept(), i.amount(), i.kind(), Income.Destination.SAVINGS, null, null,
                i.scheduledId()) : i).toList();
        List<Movement> moves = p.movements().stream().map(m -> id.equals(m.goalId())
                ? new Movement(m.id(), m.date(), m.concept(), m.category(), m.amount(), m.fromReserve(), m.scheduledId(), null)
                : m).toList();
        return p.withMovements(moves).withLedger(l.withIncomes(incomes)
                .withPots(l.pots().stream().filter(x -> !x.id().equals(id)).toList())
                .withEntries(l.entries().stream().filter(e -> !e.potId().equals(id)).toList()));
    }

    /** Puts money into (positive) or takes it out of (negative) a pot. */
    public static Profile addPotEntry(Profile p, String potId, LocalDate date, BigDecimal amount, PotEntry.Kind kind,
                                      String note) {
        if (amount.signum() == 0) throw new IllegalArgumentException("amount must not be 0");
        Profile q = Pot.RESERVE_ID.equals(potId) ? ensureReserve(p, date) : p;
        if (!Pot.RESERVE_ID.equals(potId)) requireGoal(q, potId);
        List<PotEntry> list = new ArrayList<>(q.ledger().entries());
        list.add(new PotEntry(Ids.next("e"), potId, date, amount, kind, note, null));
        return q.withLedger(q.ledger().withEntries(list));
    }

    public static Profile removePotEntry(Profile p, String id) {
        return p.withLedger(p.ledger().withEntries(p.ledger().entries().stream().filter(e -> !e.id().equals(id)).toList()));
    }

    /**
     * Corrects a pot so it holds {@code actual} today, with an adjustment entry for the difference.
     * The first time for the reserve, it sets its starting balance instead.
     */
    public static Profile adjustPotBalance(Profile p, String potId, BigDecimal actual, LocalDate today) {
        Pot pot = p.ledger().pot(potId).orElseThrow(() -> new IllegalArgumentException("unknown pot: " + potId));
        if (!pot.openingSet()) return setReserveBalance(p, actual, today);
        BigDecimal diff = actual.subtract(Pots.balance(pot, p));
        if (diff.signum() == 0) return p;
        return addPotEntry(p, potId, today, diff, PotEntry.Kind.ADJUST, t("savings.note.adjustment"));
    }

    /** The reserve starts being tracked the first time money is sent to it. */
    public static Profile ensureReserve(Profile p, LocalDate date) {
        Pot r = p.ledger().reserve();
        if (r.openingSet()) return p;
        return savePot(p, r.withOpening(r.opening(), date));
    }

    /** The active or archived goal with this id; throws if there is none. */
    public static Pot requireGoal(Profile p, String id) {
        return p.ledger().pots().stream().filter(x -> x.id().equals(id) && !x.isReserve()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown goal: " + id));
    }

    private static Profile savePot(Profile p, Pot pot) {
        return p.withLedger(p.ledger().withPots(Lists.upsert(p.ledger().pots(), pot, Pot::id)));
    }
}
