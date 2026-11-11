package cat.pessic.core.ine;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Expected values computed independently from ine/epf.tsv (INE EPF 2025). */
class IneReferenceTest {

    final IneReference ine = IneReference.bundled();

    @Test
    void bundledDataIsEpf2025AndAddsUpToThePressRelease() {
        assertEquals(2025, ine.year());
        BigDecimal annual = BigDecimal.ZERO;
        for (Category c : Category.ALL) annual = annual.add(ine.expected(c, Catalog.ALL_HOUSEHOLDS, null, null).monthly());
        // 35.100,69 €/year for the average household (press release: 35.101 €)
        BigDecimal diff = annual.multiply(BigDecimal.valueOf(12)).subtract(Money.of("35100.69")).abs();
        assertTrue(diff.compareTo(Money.of("0.20")) <= 0, "annual total " + annual.multiply(BigDecimal.valueOf(12)));
    }

    @Test
    void householdTypeOnly() {
        var e = ine.expected(Category.FOOD, 2, null, null);
        assertEquals(Money.of("486.57"), e.monthly());
        assertEquals(Money.of("486.57"), e.byType());
        assertNull(e.bySize());
        assertNull(e.byIncome());
        assertEquals(Money.of("89.88"), ine.expected(Category.INFO_COMMUNICATION, Catalog.ALL_HOUSEHOLDS, null, null).monthly());
    }

    @Test
    void meanOfTypeSizeAndIncome() {
        var e = ine.expected(Category.FOOD, 2, 2, Money.of("2400"));
        assertEquals(Money.of("486.57"), e.byType());
        assertEquals(Money.of("461.85"), e.bySize());
        assertEquals(Money.of("404.86"), e.byIncome());
        assertEquals(Money.of("451.09"), e.monthly());
    }

    @Test
    void missingIneValueIsLeftOutOfTheMean() {
        // INE publishes no education figure for households under 500 €/month
        var e = ine.expected(Category.EDUCATION, 0, 1, Money.of("450"));
        assertNull(e.byIncome());
        assertEquals(Money.of("8.32"), e.monthly());
    }

    @Test
    void incomeBandsAndLargeHouseholds() {
        assertEquals("De 2.000 a 2.499 euros", ine.incomeBand(Money.of("2400")).orElseThrow());
        assertEquals("De 2.000 a 2.499 euros", ine.incomeBand(Money.of("2499.99")).orElseThrow());
        assertEquals("De 2.500 a 2.999 euros", ine.incomeBand(Money.of("2500")).orElseThrow());
        assertEquals("5.000 euros o més".replace("més", "más"), ine.incomeBand(Money.of("12000")).orElseThrow());
        assertEquals("Hasta 499 euros", ine.incomeBand(Money.of("1")).orElseThrow());
        assertTrue(ine.incomeBand(BigDecimal.ZERO).isEmpty());
        assertEquals(ine.expected(Category.FOOD, 5, 5, null).bySize(), ine.expected(Category.FOOD, 5, 9, null).bySize());
    }
}
