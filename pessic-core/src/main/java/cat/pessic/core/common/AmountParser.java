package cat.pessic.core.common;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parses amounts the way people type them in Catalan/Spanish: "1.234,56", "12,5", "12.50", "1.000 €".
 */
public final class AmountParser {
    private AmountParser() {}

    public static Optional<BigDecimal> parse(String input) {
        if (input == null) return Optional.empty();
        String s = input.strip().replaceAll("[\\s€]", "");
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.matches("^\\d{1,3}(\\.\\d{3})+$")) {
            s = s.replace(".", "");
        }
        try {
            return Optional.of(Money.round(new BigDecimal(s)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
