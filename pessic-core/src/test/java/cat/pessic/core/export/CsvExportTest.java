package cat.pessic.core.export;

import static org.junit.jupiter.api.Assertions.*;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.savings.Pot;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvExportTest {

    static String text(ByteArrayOutputStream out) {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void movementsUseExcelFriendlyFormatAndCycle() throws Exception {
        var moves = List.of(
                new Movement("b", LocalDate.of(2026, 11, 3), "Cafè; i croissant", Category.FOOD, Money.of("3.5"), false, null),
                new Movement("a", LocalDate.of(2026, 10, 30), "Préstec", Category.TRANSPORT, Money.of("296.92"), false, "prestec@2026-11"),
                new Movement("c", LocalDate.of(2026, 11, 28), "Assegurança", Category.TRANSPORT, Money.of("1234.5"), true, null));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExport.movements(out, moves, 28);
        String[] lines = text(out).split("\r\n");

        assertTrue(lines[0].startsWith("﻿Data;Cicle;Tipus"));
        assertTrue(lines[0].endsWith(";Tipus;Concepte;Codi;Categoria;Import;Paga la reserva;Pagament fix;Destinació"));
        assertEquals("30/10/2026;2026-11;Despesa;Préstec;07;Transport;296,92;no;sí;", lines[1]);
        assertEquals("03/11/2026;2026-11;Despesa;\"Cafè; i croissant\";01;Aliments i begudes no alcohòliques;3,50;no;no;", lines[2]);
        assertEquals("28/11/2026;2026-12;Despesa;Assegurança;07;Transport;1234,50;sí;no;", lines[3]);
        assertEquals(4, lines.length);
    }

    @Test
    void incomesAndRefundsAreRowsWithTheirType() throws Exception {
        var goal = Pot.newGoal("Viatge", Money.of("1200"), null, Money.of("100"), Money.of("0"), LocalDate.of(2026, 1, 1));
        var incomes = List.of(
                new Income("i1", LocalDate.of(2026, 11, 5), "Paga de Nadal", Money.of("2000"), Income.Kind.EXTRA_PAY,
                        Income.Destination.GOAL, null, goal.id(), null),
                new Income("i2", LocalDate.of(2026, 11, 6), "Jersei tornat", Money.of("30"), Income.Kind.REFUND,
                        Income.Destination.CATEGORY, Category.CLOTHING, null, null));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExport.movements(out, List.of(), incomes, List.of(goal), 1);
        String[] lines = text(out).split("\r\n");
        assertEquals("05/11/2026;2026-11;Ingrés;Paga de Nadal;;Paga extra;2000,00;no;no;Viatge", lines[1]);
        assertEquals("06/11/2026;2026-11;Devolució;Jersei tornat;03;Vestit i calçat;30,00;no;no;Torna a la categoria", lines[2]);
    }

    @Test
    void escapesQuotesAndFormulas() {
        assertEquals("\"Diu \"\"hola\"\"\"", CsvExport.field("Diu \"hola\""));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", CsvExport.field("=HYPERLINK(\"x\")"));
        assertTrue(CsvExport.field("=1+1").startsWith("'"));
        assertTrue(CsvExport.field("@SUM(A1)").startsWith("'"));
        assertEquals("-12,50", CsvExport.field("-12,50"), "negative amounts stay numbers");
        assertEquals("", CsvExport.field(null));
    }

    @Test
    void envelopesIncludeEveryCategoryAndATotal() throws Exception {
        var cfg = new BudgetConfig(Money.of("2000"), Money.of("0"), 1, 2,
                Map.of(Category.FOOD, Money.of("300"), Category.TRANSPORT, Money.of("100")), List.of(), List.of());
        var s = new BudgetCalculator(cfg).summarize(YearMonth.of(2026, 11),
                List.of(new Movement("a", LocalDate.of(2026, 11, 2), "Súper", Category.FOOD, Money.of("80"), false, null)),
                LocalDate.of(2026, 11, 9));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExport.envelopes(out, s);
        String[] lines = text(out).split("\r\n");

        assertEquals(1 + Category.ALL.size() + 1, lines.length);
        assertTrue(text(out).contains("2026-11;01;Aliments i begudes no alcohòliques;300,00;0,00;80,00;0,00;220,00;0,00"));
        assertEquals("2026-11;;Total;400,00;0,00;80,00;0,00;320,00;0,00", lines[lines.length - 1]);
    }
}
