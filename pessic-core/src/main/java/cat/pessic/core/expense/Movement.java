package cat.pessic.core.expense;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recorded expense.
 *
 * @param fromReserve paid from the annual reserve, so it does not count against the month
 * @param goalId      paid from this savings goal (for example the trip itself), so it does not count
 *                    against the month either; {@code null} otherwise
 * @param scheduledId set when this movement marks a planned payment as paid: a fixed payment
 *                    ({@code <fixedPaymentId>@<yyyy-MM>}) or an annual one ({@code <annualId>@<yyyy>});
 *                    otherwise {@code null}
 */
public record Movement(String id, LocalDate date, String concept, Category category, BigDecimal amount,
                       boolean fromReserve, String scheduledId, String goalId) {
    public Movement {
        amount = Money.round(amount);
        if (goalId != null && (goalId.isBlank() || fromReserve)) goalId = null;
    }

    /** An expense of the monthly budget or the reserve (not paid from a goal). */
    public Movement(String id, LocalDate date, String concept, Category category, BigDecimal amount,
                    boolean fromReserve, String scheduledId) {
        this(id, date, concept, category, amount, fromReserve, scheduledId, null);
    }

    /** Paid from the reserve or a goal: it does not use the monthly budget. */
    public boolean outsideBudget() {
        return fromReserve || goalId != null;
    }

    /** Records a monthly fixed payment (marked "Pagat" in the plan). */
    public boolean isFixed() {
        return scheduledId != null && !fromReserve;
    }

    /** Records an annual payment paid from the reserve. */
    public boolean isAnnual() {
        return scheduledId != null && fromReserve;
    }
}
