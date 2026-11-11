package cat.pessic.core.payment;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;

/** A payment made once a year, funded by setting aside 1/12 every month. */
public record AnnualItem(String id, int month, String concept, Category category, BigDecimal amount) {
    public AnnualItem {
        if (month < 1 || month > 12) throw new IllegalArgumentException("month must be 1..12: " + month);
        amount = Money.round(amount);
    }
}
