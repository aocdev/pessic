package cat.pessic.core.budget;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void thirteenCoicop2018GroupsInOrder() {
        assertEquals(13, Category.ALL.size());
        for (int i = 0; i < 13; i++) assertEquals(String.format("%02d", i + 1), Category.ALL.get(i).code());
        assertEquals(Category.INFO_COMMUNICATION, Category.ofCode("08"));
        assertEquals("08 · Informació i comunicacions", Category.INFO_COMMUNICATION.label());
    }

    @Test
    void everyLegacyNameMapsToADistinctCategory() {
        var seen = new HashSet<Category>();
        for (String legacy : new String[]{"Habitatge, aigua, llum i gas", "Alimentació", "Transport",
                "Restaurants i hotels", "Oci, esport i cultura", "Vestit i calçat", "Sanitat",
                "Cura personal i altres", "Assegurances i serveis financers", "Mobles i llar", "Comunicacions",
                "Educació", "Alcohol i tabac"}) {
            assertTrue(seen.add(Category.fromLegacy(legacy).orElseThrow()), legacy);
        }
        assertEquals(Category.INFO_COMMUNICATION, Category.fromLegacy("Comunicacions").orElseThrow());
        assertTrue(Category.fromLegacy("Unknown").isEmpty());
    }

    @Test
    void parseAcceptsCodesLegacyAndDisplayNames() {
        assertEquals(Category.HOUSING, Category.parse("04").orElseThrow());
        assertEquals(Category.HOUSING, Category.parse("Habitatge, aigua, llum i gas").orElseThrow());
        assertEquals(Category.HOUSING, Category.parse(Category.HOUSING.displayName()).orElseThrow());
        assertTrue(Category.parse(null).isEmpty());
    }

    @Test
    void ineNamesMatchTheEpfTables() {
        assertEquals(Category.INFO_COMMUNICATION, Category.fromIneName("Información y comunicaciones").orElseThrow());
        assertEquals(Category.HOUSING, Category.fromIneName("Vivienda, agua, electricidad, gas y otros combustibles").orElseThrow());
    }
}
