package cat.pessic.core.income;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.savings.Pot;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money that comes in besides the monthly salary: an extra paycheck, a bonus, someone paying you
 * back, a refund of a purchase, or the leftover of the previous month carried over.
 *
 * @param destination where the money goes; a refund always goes back to its {@code category}
 * @param category    the category a refund returns to; {@code null} otherwise
 * @param potId       the savings pot it goes into when the destination is the reserve or a goal
 * @param scheduledId {@code pay@yyyy-MM} when it records an expected extra paycheck
 */
public record Income(String id, LocalDate date, String concept, BigDecimal amount, Kind kind,
                     Destination destination, Category category, String potId, String scheduledId) {

    public enum Kind {
        EXTRA_PAY("Paga extra"), BONUS("Bonus o incentiu"), REFUND("Devolució"), CARRY("Sobrant del mes anterior"),
        OTHER("Altres ingressos");

        public final String label;

        Kind(String label) { this.label = label; }

        public String code() { return name().toLowerCase(); }

        public static Kind ofCode(String code) { return valueOf(code.toUpperCase()); }
    }

    public enum Destination {
        /** Adds to what can be spent in the cycle it arrives in. */
        BUDGET("Pressupost d'aquest mes"),
        /** Goes into the annual reserve. */
        RESERVE("Reserva d'anuals"),
        /** Goes into a savings goal ({@code potId}). */
        GOAL("Una guardiola"),
        /** Not spent: it stays as savings. */
        SAVINGS("Estalvi"),
        /** A refund: it lowers what was spent in {@code category}. */
        CATEGORY("Torna a la categoria");

        public final String label;

        Destination(String label) { this.label = label; }

        public String code() { return name().toLowerCase(); }

        public static Destination ofCode(String code) { return valueOf(code.toUpperCase()); }
    }

    public Income {
        amount = Money.round(amount);
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (kind == null || destination == null || date == null) throw new IllegalArgumentException("incomplete income");
        concept = concept == null ? "" : concept.strip();
        if ((kind == Kind.REFUND) != (destination == Destination.CATEGORY))
            throw new IllegalArgumentException("a refund (and only a refund) goes back to a category");
        if (destination == Destination.CATEGORY && category == null)
            throw new IllegalArgumentException("a refund needs a category");
        if (destination != Destination.CATEGORY) category = null;
        if (destination == Destination.RESERVE) potId = Pot.RESERVE_ID;
        else if (destination == Destination.GOAL) {
            if (potId == null || potId.isBlank()) throw new IllegalArgumentException("choose a goal");
        } else potId = null;
    }

    public boolean isRefund() {
        return kind == Kind.REFUND;
    }

    /**
     * Money that is new income: not a refund (it gives back money already spent) nor the previous
     * month's leftover carried over (it was already counted then).
     */
    public boolean isNewMoney() {
        return kind != Kind.REFUND && kind != Kind.CARRY;
    }
}
