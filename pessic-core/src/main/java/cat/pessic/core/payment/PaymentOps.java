package cat.pessic.core.payment;

import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.budget.PayCycle;
import cat.pessic.core.common.Checks;
import cat.pessic.core.common.Lists;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.profile.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed monthly payments and annual payments: planning them and marking them as paid. Each
 * operation returns the new profile, which the caller saves.
 */
public final class PaymentOps {
    private PaymentOps() {}

    // ------------------------------------------------------------ paying

    /**
     * Records a fixed payment as paid. The movement is dated on the payment's date, or today if that
     * date is still ahead and we are already inside the cycle (paid early). Does nothing if already paid.
     */
    public static Profile markPaid(Profile p, ScheduledPayment sp, PayCycle cycle, LocalDate today) {
        if (p.movements().stream().anyMatch(m -> sp.scheduledId().equals(m.scheduledId()))) return p;
        LocalDate date = sp.date().isAfter(today) && !today.isBefore(cycle.start()) ? today : sp.date();
        FixedPayment f = sp.payment();
        List<Movement> moves = new ArrayList<>(p.movements());
        moves.add(new Movement("p-" + sp.scheduledId(), date, f.concept(), f.category(), f.amount(), false,
                sp.scheduledId()));
        return p.withMovements(moves);
    }

    /**
     * Records an annual payment of the given cycle as paid from the reserve: it does not count
     * against the month. Dated today if today is inside the cycle, otherwise on the cycle's start.
     */
    public static Profile markAnnualPaid(Profile p, CycleSummary.AnnualLine line, PayCycle cycle, LocalDate today) {
        if (p.movements().stream().anyMatch(m -> line.scheduledId().equals(m.scheduledId()))) return p;
        LocalDate date = cycle.contains(today) ? today : cycle.start();
        AnnualItem a = line.item();
        List<Movement> moves = new ArrayList<>(p.movements());
        moves.add(new Movement("r-" + line.scheduledId(), date, a.concept(), a.category(), a.amount(), true,
                line.scheduledId()));
        return p.withMovements(moves);
    }

    public static Profile unmarkPaid(Profile p, String scheduledId) {
        return p.withMovements(p.movements().stream().filter(m -> !scheduledId.equals(m.scheduledId())).toList());
    }

    // ---------------------------------------------------------- fixed payments

    /** Adds the payment, or replaces the one with the same id. */
    public static Profile saveFixed(Profile p, FixedPayment f) {
        Checks.positive(f.amount());
        Checks.present(f.category(), "category");
        if (f.from() != null && f.to() != null && f.to().isBefore(f.from()))
            throw new IllegalArgumentException("\"to\" is before \"from\"");
        return p.withConfig(p.config().withPlan(Lists.upsert(p.config().plan(), f, FixedPayment::id)));
    }

    /**
     * Stops a fixed payment after {@code lastCycle} (inclusive): it disappears from later cycles but
     * earlier ones stay as they were. Use it when a subscription is cancelled. If the payment would
     * then never apply at all (it starts after {@code lastCycle}), it is deleted instead.
     */
    public static Profile endFixed(Profile p, String id, YearMonth lastCycle) {
        FixedPayment f = p.config().plan().stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown fixed payment: " + id));
        if (f.from() != null && f.from().isAfter(lastCycle)) return deleteFixed(p, id);
        if (f.to() != null && !f.to().isAfter(lastCycle)) return p; // already ends by then
        return saveFixed(p, new FixedPayment(f.id(), f.day(), f.concept(), f.category(), f.amount(),
                f.from(), lastCycle, f.extra()));
    }

    public static Profile deleteFixed(Profile p, String id) {
        return p.withConfig(p.config().withPlan(p.config().plan().stream().filter(f -> !f.id().equals(id)).toList()));
    }

    // ------------------------------------------------------------ annual payments

    public static Profile saveAnnual(Profile p, AnnualItem a) {
        Checks.positive(a.amount());
        Checks.present(a.category(), "category");
        return p.withConfig(p.config().withAnnuals(Lists.upsert(p.config().annuals(), a, AnnualItem::id)));
    }

    public static Profile deleteAnnual(Profile p, String id) {
        return p.withConfig(p.config().withAnnuals(
                p.config().annuals().stream().filter(a -> !a.id().equals(id)).toList()));
    }
}
