package cat.pessic.core.common;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Helpers for euro amounts. Stored amounts always have scale 2; intermediate
 * results (divisions) keep {@link #CALC} precision and are rounded only when shown.
 */
public final class Money {
    public static final MathContext CALC = MathContext.DECIMAL64;
    /** Amounts closer than this to zero count as zero (same tolerance as the PWA). */
    public static final BigDecimal EPSILON = new BigDecimal("0.004");

    private Money() {}

    public static BigDecimal of(String value) {
        return round(new BigDecimal(value));
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static long toCents(BigDecimal value) {
        return round(value).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return a.divide(b, CALC);
    }

    public static BigDecimal divide(BigDecimal a, long b) {
        return a.divide(BigDecimal.valueOf(b), CALC);
    }

    public static boolean isNegative(BigDecimal value) {
        return value.compareTo(EPSILON.negate()) < 0;
    }
}
