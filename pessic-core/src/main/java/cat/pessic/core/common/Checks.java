package cat.pessic.core.common;

import java.math.BigDecimal;

/** Argument checks shared by the operations of every feature. */
public final class Checks {
    private Checks() {}

    public static void positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
    }

    public static void present(Object value, String what) {
        if (value == null) throw new IllegalArgumentException(what + " is required");
    }
}
