package cat.pessic.core.ine;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.payment.AnnualItem;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class IneLimitsTest {

    final IneReference ine = IneReference.bundled();

    static BudgetConfig config(String income, String savings, List<AnnualItem> annuals) {
        var zero = new EnumMap<Category, BigDecimal>(Category.class);
        return new BudgetConfig(Money.of(income), Money.of(savings), 1, 2, 2, zero, List.of(), annuals);
    }

    static BigDecimal total(IneLimits.Proposal p) {
        return p.limits().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void usesTheExpectationWhenItFits() {
        var p = IneLimits.propose(config("6000", "0", List.of()), ine);
        assertFalse(p.scaled());
        assertEquals(ine.expected(Category.FOOD, 2, 2, Money.of("6000")).monthly(), p.limits().get(Category.FOOD));
        assertEquals(13, p.limits().size());
    }

    @Test
    void scalesEverythingDownToFitTheIncomeAfterSavings() {
        var p = IneLimits.propose(config("2400", "400", List.of()), ine);
        assertTrue(p.scaled());
        assertEquals(Money.of("2738.35"), p.ineTotal());
        assertEquals(Money.of("2000.00"), p.available());
        assertTrue(total(p).compareTo(Money.of("2000")) <= 0);
        assertTrue(total(p).compareTo(Money.of("1999.80")) > 0, "only rounding is lost: " + total(p));
        assertEquals(73, p.scalePercent());
    }

    @Test
    void annualReserveCoversPartOfTheCategory() {
        var insurance = new AnnualItem("a", 5, "Assegurança", Category.INSURANCE_FINANCE, Money.of("1200"));
        var with = IneLimits.propose(config("9000", "0", List.of(insurance)), ine);
        var without = IneLimits.propose(config("9000", "0", List.of()), ine);
        assertEquals(without.limits().get(Category.INSURANCE_FINANCE).subtract(Money.of("100")).max(BigDecimal.ZERO),
                with.limits().get(Category.INSURANCE_FINANCE));
        assertEquals(without.limits().get(Category.FOOD), with.limits().get(Category.FOOD));
    }

    @Test
    void nothingAvailableMeansZeroLimits() {
        var p = IneLimits.propose(config("500", "500", List.of()), ine);
        assertEquals(0, total(p).signum());
    }
}
