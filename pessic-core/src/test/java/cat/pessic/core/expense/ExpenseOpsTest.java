package cat.pessic.core.expense;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.profile.NewProfile;
import cat.pessic.core.profile.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** Recording and deleting expenses. */
class ExpenseOpsTest {

    static final YearMonth NOV = YearMonth.of(2026, 11);

    final Profile base = NewProfile.create("Test", Money.of("2000"), 28, 2, null, false);

    @Test
    void addAndDeleteMovement() {
        Profile p = ExpenseOps.addMovement(base, LocalDate.of(2026, 11, 3), " Súper ", Category.FOOD, Money.of("42.10"), false);
        assertEquals(1, p.movements().size());
        Movement m = p.movements().get(0);
        assertEquals("Súper", m.concept());
        assertNull(m.scheduledId());
        assertTrue(ExpenseOps.deleteMovement(p, m.id()).movements().isEmpty());
        assertEquals(0, base.movements().size(), "original is untouched");
    }

    @Test
    void rejectsZeroAmountsAndUnknownCategories() {
        LocalDate d = LocalDate.of(2026, 11, 3);
        assertThrows(IllegalArgumentException.class, () -> ExpenseOps.addMovement(base, d, "x", Category.FOOD, Money.of("0"), false));
        assertThrows(IllegalArgumentException.class, () -> ExpenseOps.addMovement(base, d, "x", null, Money.of("1"), false));
    }
}
