package cat.pessic.core.budget;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.common.Money;
import cat.pessic.core.profile.NewProfile;
import cat.pessic.core.profile.Profile;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Category limits and the budget settings. */
class BudgetOpsTest {

    static final YearMonth NOV = YearMonth.of(2026, 11);

    final Profile base = NewProfile.create("Test", Money.of("2000"), 28, 2, null, false);

    @Test
    void limitsAndSettings() {
        Profile p = BudgetOps.setLimits(base, Map.of(Category.TRANSPORT, Money.of("150")));
        assertEquals(Money.of("150"), p.config().limit(Category.TRANSPORT));
        assertEquals(Money.of("0"), p.config().limit(Category.FOOD));

        Profile s = BudgetOps.updateSettings(p, " Nou ", Money.of("2500"), Money.of("300"), 1, 4, 3);
        assertEquals("Nou", s.name());
        assertEquals(1, s.config().payday());
        assertEquals(3, s.config().members());
        assertEquals(Money.of("150"), s.config().limit(Category.TRANSPORT), "settings keep the limits");
    }
}
