package cat.pessic.app.common;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/** Catalan formatting: "1.234,56 €", "Novembre 2026". */
public final class Format {
    public static final Locale CA = Locale.forLanguageTag("ca-ES");
    private static final DecimalFormat EUR;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(CA);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        sym.setMinusSign('−');
        EUR = new DecimalFormat("#,##0.00 €", sym);
    }

    private Format() {}

    public static String eur(BigDecimal v) {
        if (v == null) return "—";
        if (v.abs().compareTo(new BigDecimal("0.004")) < 0) v = BigDecimal.ZERO;
        synchronized (EUR) {
            return EUR.format(v);
        }
    }

    /** Amount as typed in a form field: "1234,50" (no thousands separator, no symbol). */
    public static String input(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public static String dayMonth(java.time.LocalDate d) {
        return d.getDayOfMonth() + "/" + d.getMonthValue();
    }

    /** "9 de novembre de 2026" */
    public static String date(java.time.LocalDate d) {
        String m = monthName(d.getMonthValue()).toLowerCase();
        return d.getDayOfMonth() + (m.matches("^[aeiouà].*") ? " d'" : " de ") + m + " de " + d.getYear();
    }

    public static String monthName(int month) {
        String m = java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, CA);
        return Character.toUpperCase(m.charAt(0)) + m.substring(1);
    }

    public static String month(YearMonth ym) {
        String m = ym.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, CA);
        return Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + ym.getYear();
    }
}
