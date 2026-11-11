package cat.pessic.core.account;

import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A correction of the account's estimated balance to what the bank says ("Ajusta"). Usually the
 * expenses that were not recorded in Pessic.
 *
 * @param amount positive when the bank has more than the estimate
 * @param cycle  the cycle whose close made the check, or {@code null}
 */
public record AccountEntry(String id, LocalDate date, BigDecimal amount, String note, YearMonth cycle) {
    public AccountEntry {
        if (id == null || date == null) throw new IllegalArgumentException("incomplete account entry");
        amount = Money.round(amount);
        note = note == null ? "" : note.strip();
    }
}
