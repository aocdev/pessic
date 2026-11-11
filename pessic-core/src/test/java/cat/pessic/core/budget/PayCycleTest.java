package cat.pessic.core.budget;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PayCycleTest {

    @Test
    void latePaydayNamesCycleAfterFollowingMonth() {
        // Paid on 28 Oct -> that money is "November"
        PayCycle c = PayCycle.of(YearMonth.of(2026, 11), 28);
        assertEquals(LocalDate.of(2026, 10, 28), c.start());
        assertEquals(LocalDate.of(2026, 11, 27), c.end());
        assertEquals(31, c.days());
    }

    @Test
    void earlyPaydayKeepsCalendarMonthName() {
        PayCycle c = PayCycle.of(YearMonth.of(2026, 11), 5);
        assertEquals(LocalDate.of(2026, 11, 5), c.start());
        assertEquals(LocalDate.of(2026, 12, 4), c.end());
    }

    @Test
    void paydayOneIsCalendarMonth() {
        PayCycle c = PayCycle.of(YearMonth.of(2026, 2), 1);
        assertEquals(LocalDate.of(2026, 2, 1), c.start());
        assertEquals(LocalDate.of(2026, 2, 28), c.end());
    }

    @Test
    void containingFindsTheRightCycle() {
        assertEquals(YearMonth.of(2026, 11), PayCycle.containing(LocalDate.of(2026, 11, 9), 28).key());
        assertEquals(YearMonth.of(2026, 11), PayCycle.containing(LocalDate.of(2026, 10, 28), 28).key());
        assertEquals(YearMonth.of(2026, 12), PayCycle.containing(LocalDate.of(2026, 11, 28), 28).key());
        assertEquals(YearMonth.of(2027, 1), PayCycle.containing(LocalDate.of(2026, 12, 30), 28).key());
        assertEquals(YearMonth.of(2026, 10), PayCycle.containing(LocalDate.of(2026, 11, 4), 5).key());
    }

    @Test
    void cyclesAreContiguous() {
        PayCycle c = PayCycle.of(YearMonth.of(2026, 1), 20);
        for (int i = 0; i < 24; i++) {
            PayCycle n = c.next();
            assertEquals(c.end().plusDays(1), n.start());
            assertEquals(c, n.previous());
            c = n;
        }
    }
}
