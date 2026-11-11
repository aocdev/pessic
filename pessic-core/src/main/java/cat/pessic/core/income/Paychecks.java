package cat.pessic.core.income;

import cat.pessic.core.budget.PayCycle;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * How the salary is paid: in 12 payments, or in 14 with two extra paychecks a year (usually June and
 * December). Only used to plan what is coming; extra money is recorded as an {@link Income} when it
 * arrives, so changing this setting (a new job) never changes past months.
 *
 * @param count       12 or 14
 * @param extraAmount amount of each extra paycheck, or {@code null} for the same as the monthly income
 * @param months      calendar months (1..12) the extra paychecks arrive in
 */
public record Paychecks(int count, BigDecimal extraAmount, List<Integer> months) {

    public static final Paychecks TWELVE = new Paychecks(12, null, List.of(6, 12));

    public Paychecks {
        if (count != 12 && count != 14) throw new IllegalArgumentException("paychecks must be 12 or 14: " + count);
        if (extraAmount != null) {
            if (extraAmount.signum() < 0) throw new IllegalArgumentException("extra paycheck must be >= 0");
            extraAmount = Money.round(extraAmount);
        }
        months = months == null ? List.of(6, 12) : months.stream().distinct().sorted().toList();
        for (int m : months) if (m < 1 || m > 12) throw new IllegalArgumentException("month must be 1..12: " + m);
        if (count == 14 && months.size() != 2) throw new IllegalArgumentException("14 paychecks need two months");
    }

    public boolean hasExtras() {
        return count == 14;
    }

    public BigDecimal amount(BigDecimal income) {
        return extraAmount != null ? extraAmount : Money.round(income);
    }

    /** {@code "6,12"} */
    public String monthsText() {
        return String.join(",", months.stream().map(String::valueOf).toList());
    }

    public static List<Integer> parseMonths(String s) {
        if (s == null || s.isBlank()) return List.of(6, 12);
        return java.util.Arrays.stream(s.split(",")).map(String::strip).filter(x -> !x.isEmpty())
                .map(Integer::parseInt).toList();
    }

    /** Id that links an expected extra paycheck to the income that records it. */
    public static String scheduledId(YearMonth month) {
        return "pay@" + month;
    }

    /**
     * The extra paycheck that arrives in the cycle {@code key}, if any. It arrives with the payroll of
     * its month, on payday, so it belongs to the cycle containing that date.
     */
    public Optional<YearMonth> extraIn(YearMonth key, int payday) {
        if (!hasExtras()) return Optional.empty();
        for (int year = key.getYear() - 1; year <= key.getYear(); year++) {
            for (int m : months) {
                LocalDate arrives = LocalDate.of(year, m, payday);
                if (PayCycle.containing(arrives, payday).key().equals(key)) return Optional.of(YearMonth.of(year, m));
            }
        }
        return Optional.empty();
    }
}
