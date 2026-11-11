package cat.pessic.core.account;

import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The day-to-day account, where the salary arrives. Pessic does not track savings or investment
 * accounts: money sent there is an outflow (automatic savings, a fixed payment, a contribution to
 * the reserve or a goal).
 *
 * @param opening what the account held at the end of {@code since}
 * @param since   from the next day on, movements change the estimated balance ({@link Cash})
 */
public record Account(BigDecimal opening, LocalDate since) {
    public Account {
        if (opening == null || since == null) throw new IllegalArgumentException("incomplete account");
        opening = Money.round(opening);
    }

    /** True if something dated {@code date} changes the balance. */
    public boolean counts(LocalDate date) {
        return date.isAfter(since);
    }
}
