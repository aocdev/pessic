package cat.pessic.core.common;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12,5|12.50", "1.234,56|1234.56", "1.000|1000.00", "12.50|12.50",
            "  7 € |7.00", "0,005|0.01", "296.92|296.92"})
    void parsesLocalFormats(String input, String expected) {
        assertEquals(new BigDecimal(expected), AmountParser.parse(input).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1,2,3"})
    void rejectsGarbage(String input) {
        assertTrue(AmountParser.parse(input).isEmpty());
    }
}
