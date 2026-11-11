package cat.pessic.core.export;

import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.budget.PayCycle;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.history.History;
import cat.pessic.core.income.Income;
import cat.pessic.core.savings.Pot;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * CSV files that open directly in Excel / LibreOffice set up for Catalan or Spanish:
 * ';' separator, decimal comma, dd/MM/yyyy dates, UTF-8 with BOM, CRLF line endings.
 */
public final class CsvExport {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final char SEP = ';';

    private CsvExport() {}

    /** One row per movement, oldest first, with the budget cycle it belongs to. */
    public static void movements(OutputStream out, List<Movement> movements, int payday) throws IOException {
        movements(out, movements, List.of(), List.of(), payday);
    }

    /**
     * Expenses, extra incomes and refunds, oldest first. Amounts are always positive; the "Tipus"
     * column says which is which.
     *
     * @param pots to name the goal an expense was paid from or an income went to
     */
    public static void movements(OutputStream out, List<Movement> movements, List<Income> incomes, List<Pot> pots,
                                 int payday) throws IOException {
        record Line(java.time.LocalDate date, String concept, String[] fields) {}
        java.util.function.Function<String, String> potName = id -> id == null ? "" : Pot.RESERVE_ID.equals(id)
                ? "Reserva d'anuals" : pots.stream().filter(x -> x.id().equals(id)).map(Pot::name).findFirst().orElse(id);
        List<Line> lines = new java.util.ArrayList<>();
        for (Movement m : movements) {
            lines.add(new Line(m.date(), m.concept(), new String[]{m.date().format(DATE),
                    PayCycle.containing(m.date(), payday).key().toString(), "Despesa", m.concept(), m.category().code(),
                    m.category().displayName(), amount(m.amount()), m.fromReserve() ? "sí" : "no", m.isFixed() ? "sí" : "no",
                    m.goalId() != null ? "Pagat amb " + potName.apply(m.goalId()) : ""}));
        }
        for (Income i : incomes) {
            String dest = switch (i.destination()) {
                case RESERVE, GOAL -> potName.apply(i.potId());
                default -> i.destination().label;
            };
            lines.add(new Line(i.date(), i.concept(), new String[]{i.date().format(DATE),
                    PayCycle.containing(i.date(), payday).key().toString(), i.isRefund() ? "Devolució" : "Ingrés",
                    i.concept().isEmpty() ? i.kind().label : i.concept(), i.category() == null ? "" : i.category().code(),
                    i.category() == null ? i.kind().label : i.category().displayName(), amount(i.amount()), "no", "no", dest}));
        }
        Writer w = open(out);
        row(w, "Data", "Cicle", "Tipus", "Concepte", "Codi", "Categoria", "Import", "Paga la reserva", "Pagament fix",
                "Destinació");
        lines.sort(Comparator.comparing(Line::date).thenComparing(Line::concept));
        for (Line l : lines) row(w, l.fields());
        w.flush();
    }

    /** One row per cycle of the history, oldest first. */
    public static void history(OutputStream out, List<History.Row> rows) throws IOException {
        Writer w = open(out);
        row(w, "Cicle", "Estat", "Ingressos", "Ingressos extra", "Pressupost", "Gastat", "A guardioles", "Estalvi",
                "% estalvi", "Diferència amb el banc");
        for (History.Row r : rows) {
            row(w, r.cycle().toString(), r.closed() ? "tancat" : "estimat", amount(r.income()), amount(r.extra()),
                    amount(r.budget()), amount(r.spent()), amount(r.toPots()), amount(r.saved()),
                    r.savingsRate().toPlainString(), r.bankDifference() == null ? "" : amount(r.bankDifference()));
        }
        w.flush();
    }

    /** One row per category for a cycle: limit, what was spent and what is left. */
    public static void envelopes(OutputStream out, CycleSummary s) throws IOException {
        Writer w = open(out);
        row(w, "Cicle", "Codi", "Categoria", "Límit", "Fixos pagats", "Variable", "Fixos pendents", "Queda", "Anual (reserva)");
        String cycle = s.cycle().key().toString();
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (CycleSummary.Envelope e : s.envelopes()) {
            BigDecimal[] v = {e.limit(), e.fixedPaid(), e.variableSpent(), e.fixedPending(), e.remaining(), e.annualDue()};
            for (int i = 0; i < v.length; i++) totals[i] = totals[i].add(v[i]);
            row(w, cycle, e.category().code(), e.category().displayName(), amount(v[0]), amount(v[1]), amount(v[2]),
                    amount(v[3]), amount(v[4]), amount(v[5]));
        }
        row(w, cycle, "", "Total", amount(totals[0]), amount(totals[1]), amount(totals[2]), amount(totals[3]),
                amount(totals[4]), amount(totals[5]));
        w.flush();
    }

    private static Writer open(OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('﻿'); // BOM: without it Excel reads UTF-8 as ANSI and breaks accents
        return w;
    }

    static String amount(BigDecimal v) {
        return Money.round(v).toPlainString().replace('.', ',');
    }

    private static void row(Writer w, String... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) w.write(SEP);
            w.write(field(fields[i]));
        }
        w.write("\r\n");
    }

    /** Quotes when needed and neutralises text that a spreadsheet would run as a formula. */
    static String field(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(,\\d+)?")) s = "'" + s;
        if (s.indexOf(SEP) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
