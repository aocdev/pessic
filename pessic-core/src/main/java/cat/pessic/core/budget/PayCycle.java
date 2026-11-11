package cat.pessic.core.budget;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * A budget month that runs from payday to the day before the next payday.
 * It is named after a calendar month: when payday is after the 15th, the cycle
 * takes the name of the following month (paid on 28 Oct, the money is for November).
 */
public record PayCycle(YearMonth key, LocalDate start, LocalDate end) {

    public static PayCycle of(YearMonth key, int payday) {
        YearMonth startMonth = key.minusMonths(shift(payday));
        LocalDate start = startMonth.atDay(payday);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return new PayCycle(key, start, end);
    }

    public static PayCycle containing(LocalDate date, int payday) {
        YearMonth month = YearMonth.from(date);
        if (date.getDayOfMonth() < payday) month = month.minusMonths(1);
        return of(month.plusMonths(shift(payday)), payday);
    }

    private static int shift(int payday) {
        return payday > 15 ? 1 : 0;
    }

    public int days() {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public PayCycle next() {
        return of(key.plusMonths(1), start.getDayOfMonth());
    }

    public PayCycle previous() {
        return of(key.minusMonths(1), start.getDayOfMonth());
    }
}
