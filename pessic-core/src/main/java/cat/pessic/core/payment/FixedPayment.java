package cat.pessic.core.payment;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A recurring payment on a given day of the month.
 *
 * @param from  first cycle it applies to (inclusive), or {@code null} for always
 * @param to    last cycle it applies to (inclusive), or {@code null} for no end
 * @param extra a one-off charge: it raises that category's limit in the cycles where it applies
 */
public record FixedPayment(String id, int day, String concept, Category category, BigDecimal amount,
                           YearMonth from, YearMonth to, boolean extra) {
    public FixedPayment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(category);
        if (day < 1 || day > 31) throw new IllegalArgumentException("day must be 1..31: " + day);
        amount = Money.round(amount);
    }

    public boolean appliesTo(YearMonth cycle) {
        return (from == null || !cycle.isBefore(from)) && (to == null || !cycle.isAfter(to));
    }
}
