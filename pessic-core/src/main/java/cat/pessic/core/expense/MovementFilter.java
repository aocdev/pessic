package cat.pessic.core.expense;

import cat.pessic.core.budget.Category;
import cat.pessic.core.income.Income;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Search and filters of the "Moviments" tab, over expenses, extra incomes and refunds together.
 *
 * @param text     words that must all appear in the concept or category, ignoring case and accents
 *                 ("cafe" finds "Cafè"); blank for any
 * @param category only this category, or {@code null}
 * @param type     only this kind of row, or {@code null}
 * @param from     first day, inclusive, or {@code null}
 * @param to       last day, inclusive, or {@code null}
 */
public record MovementFilter(String text, Category category, Type type, LocalDate from, LocalDate to) {

    public enum Type {
        EXPENSE("Despeses"), INCOME("Ingressos"), REFUND("Devolucions");

        public final String label;

        Type(String label) { this.label = label; }
    }

    /** One row: an expense ({@code movement}) or an income or refund ({@code income}). */
    public record Row(Movement movement, Income income) {
        public LocalDate date() { return movement != null ? movement.date() : income.date(); }

        public String concept() {
            if (movement != null) return movement.concept();
            return income.concept().isEmpty() ? income.kind().label : income.concept();
        }

        public Category category() { return movement != null ? movement.category() : income.category(); }

        public BigDecimal amount() { return movement != null ? movement.amount() : income.amount(); }

        public Type type() {
            if (movement != null) return Type.EXPENSE;
            return income.isRefund() ? Type.REFUND : Type.INCOME;
        }

        /** Positive for money that comes in (income, refund), negative for expenses. */
        public BigDecimal signedAmount() { return movement != null ? amount().negate() : amount(); }
    }

    /** Totals of the filtered rows. */
    public record Totals(int count, BigDecimal expenses, BigDecimal incomes, BigDecimal refunds) {
        /** Expenses minus refunds. */
        public BigDecimal netSpent() { return expenses.subtract(refunds); }
    }

    public static final MovementFilter ALL = new MovementFilter("", null, null, null, null);

    public boolean isEmpty() {
        return (text == null || text.isBlank()) && category == null && type == null;
    }

    /** Matching rows, newest first. */
    public List<Row> apply(List<Movement> movements, List<Income> incomes) {
        List<Row> rows = new ArrayList<>();
        movements.forEach(m -> rows.add(new Row(m, null)));
        incomes.forEach(i -> rows.add(new Row(null, i)));
        String[] words = text == null || text.isBlank() ? new String[0] : fold(text).split("\\s+");
        return rows.stream()
                .filter(r -> from == null || !r.date().isBefore(from))
                .filter(r -> to == null || !r.date().isAfter(to))
                .filter(r -> type == null || r.type() == type)
                .filter(r -> category == null || r.category() == category)
                .filter(r -> {
                    if (words.length == 0) return true;
                    String hay = fold(r.concept() + " " + (r.category() == null ? "" : r.category().displayName()));
                    for (String w : words) if (!hay.contains(w)) return false;
                    return true;
                })
                .sorted(Comparator.comparing(Row::date).reversed())
                .toList();
    }

    public static Totals totals(List<Row> rows) {
        BigDecimal exp = BigDecimal.ZERO, inc = BigDecimal.ZERO, ref = BigDecimal.ZERO;
        for (Row r : rows) {
            switch (r.type()) {
                case EXPENSE -> exp = exp.add(r.amount());
                case INCOME -> inc = inc.add(r.amount());
                case REFUND -> ref = ref.add(r.amount());
            }
        }
        return new Totals(rows.size(), exp, inc, ref);
    }

    /** Lower case without accents: "Cafè" → "cafe". */
    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }
}
