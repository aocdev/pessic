package cat.pessic.core.profile;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.ine.IneReference;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NewProfileTest {

    @Test
    void ineLimitsFollowHouseholdTypeSizeAndIncome() {
        // Parella sense fills, 2 members, 4.000 €/month: INE total fits, so no scaling
        Profile p = NewProfile.create("  Marta i Joan ", Money.of("4000"), 28, 2, 2, true);
        assertEquals("Marta i Joan", p.name());
        assertEquals(2, p.config().members());
        var expected = IneReference.bundled().expected(Category.FOOD, 2, 2, Money.of("4000")).monthly();
        assertEquals(expected, p.config().limit(Category.FOOD));
        assertEquals(Category.ALL.size(), p.config().limits().size());
        assertTrue(p.config().plan().isEmpty());
        assertTrue(p.movements().isEmpty());
    }

    @Test
    void withoutIneEveryLimitStartsAtZero() {
        Profile p = NewProfile.create("Jo", Money.of("1500"), 1, 0, null, false);
        assertTrue(p.config().limits().values().stream().allMatch(v -> v.signum() == 0));
        assertEquals(Category.ALL.size(), p.config().limits().size());
        assertNull(p.config().members());
    }

    @Test
    void ineLimitsNeverExceedTheIncome() {
        Profile p = NewProfile.create("Jo", Money.of("1200"), 1, 3, 3, true);
        BigDecimal total = p.config().limits().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(total.compareTo(Money.of("1200")) <= 0, "total " + total);
    }

    @Test
    void idsAreUnique() {
        assertNotEquals(NewProfile.create("a", BigDecimal.ONE, 1, 0, null, false).id(),
                NewProfile.create("a", BigDecimal.ONE, 1, 0, null, false).id());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> NewProfile.create(" ", BigDecimal.ONE, 1, 0, null, true));
        assertThrows(IllegalArgumentException.class, () -> NewProfile.create("a", Money.of("-1"), 1, 0, null, true));
        assertThrows(IllegalArgumentException.class, () -> NewProfile.create("a", BigDecimal.ONE, 29, 0, null, true));
        assertThrows(IllegalArgumentException.class, () -> NewProfile.create("a", BigDecimal.ONE, 1, 0, 0, true));
    }
}
