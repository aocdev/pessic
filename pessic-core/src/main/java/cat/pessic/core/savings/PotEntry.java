package cat.pessic.core.savings;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money moved into (positive) or out of (negative) a {@link Pot} that is neither an income nor an
 * expense: the monthly contribution posted when a month is closed, an extra contribution, taking
 * money out, or correcting the balance.
 *
 * @param cycle the cycle ({@code yyyy-MM}) whose close posted it, or {@code null}
 */
public record PotEntry(String id, String potId, LocalDate date, BigDecimal amount, Kind kind, String note,
                       java.time.YearMonth cycle) {

    public enum Kind {
        CONTRIBUTION, LEFTOVER, WITHDRAWAL, ADJUST;

        /** Name shown to the user. */
        public String label() { return t("savings.entry." + name()); }

        public String code() { return name().toLowerCase(); }

        public static Kind ofCode(String code) { return valueOf(code.toUpperCase()); }
    }

    public PotEntry {
        amount = Money.round(amount);
        note = note == null ? "" : note.strip();
        if (potId == null || date == null || kind == null) throw new IllegalArgumentException("incomplete pot entry");
    }
}
