package cat.pessic.app.expense;

import cat.pessic.app.AppContext;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.core.budget.Category;
import cat.pessic.core.expense.ExpenseOps;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.expense.MovementFilter;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.payment.PaymentOps;
import cat.pessic.core.savings.Pot;
import java.time.LocalDate;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Moviments": entry form, then every expense, income and refund with search and filters. */
public final class MovementsView {
    private MovementsView() {}

    enum Period {
        CYCLE("El mes que mires"), ALL("Tot l'historial"), RANGE("Entre dues dates");

        final String label;

        Period(String label) { this.label = label; }
    }

    /** Filters survive redraws (adding or deleting a row) while the app is open. */
    private static String text = "";
    private static Category category;
    private static MovementFilter.Type type;
    private static Period period = Period.CYCLE;
    private static LocalDate from, to;

    public static Region build(AppContext ctx) {
        TextField search = new TextField(text);
        search.setPromptText("Cerca: cafè, súper, Netflix…");
        ComboBox<Category> cat = new ComboBox<>();
        cat.getItems().add(null);
        cat.getItems().addAll(Category.ALL);
        cat.setValue(category);
        cat.setConverter(converter(c -> c == null ? "Totes les categories" : c.label()));
        ComboBox<MovementFilter.Type> kind = new ComboBox<>();
        kind.getItems().add(null);
        kind.getItems().addAll(MovementFilter.Type.values());
        kind.setValue(type);
        kind.setConverter(converter(t -> t == null ? "Tot" : t.label));
        ComboBox<Period> when = new ComboBox<>();
        when.getItems().setAll(Period.values());
        when.setValue(period);
        when.setConverter(converter(p -> p == null ? "" : p.label));
        var cycle = ctx.summary().cycle();
        DatePicker fromPicker = Ui.datePicker(from != null ? from : cycle.start());
        DatePicker toPicker = Ui.datePicker(to != null ? to : cycle.end());
        HBox range = new HBox(8, Ui.field("Des de", fromPicker), Ui.field("Fins a", toPicker));
        Button clear = Ui.link("Treu els filtres", () -> {
            search.setText("");
            cat.setValue(null);
            kind.setValue(null);
            when.setValue(Period.CYCLE);
        });

        VBox results = new VBox(12);
        Label count = Ui.label("", "small");
        Runnable refresh = () -> {
            text = search.getText();
            category = cat.getValue();
            type = kind.getValue();
            period = when.getValue();
            from = fromPicker.getValue();
            to = toPicker.getValue();
            range.setVisible(period == Period.RANGE);
            range.setManaged(period == Period.RANGE);
            LocalDate f = switch (period) {
                case CYCLE -> cycle.start();
                case ALL -> null;
                case RANGE -> from;
            };
            LocalDate t = switch (period) {
                case CYCLE -> cycle.end();
                case ALL -> null;
                case RANGE -> to;
            };
            var filter = new MovementFilter(text, category, type, f, t);
            var rows = filter.apply(ctx.profile().movements(), ctx.profile().incomes());
            var totals = MovementFilter.totals(rows);
            count.setText(rows.size() + (rows.size() == 1 ? " moviment" : " moviments")
                    + " · gastat " + Format.eur(totals.expenses())
                    + (totals.refunds().signum() > 0 ? " − devolucions " + Format.eur(totals.refunds())
                    + " = " + Format.eur(totals.netSpent()) : "")
                    + (totals.incomes().signum() > 0 ? " · ingressos " + Format.eur(totals.incomes()) : ""));
            clear.setVisible(!filter.isEmpty() || period != Period.CYCLE);
            results.getChildren().setAll(table(ctx, rows, filter.isEmpty() && period == Period.CYCLE));
        };
        search.textProperty().addListener((o, a, b) -> refresh.run());
        cat.valueProperty().addListener((o, a, b) -> refresh.run());
        kind.valueProperty().addListener((o, a, b) -> refresh.run());
        when.valueProperty().addListener((o, a, b) -> refresh.run());
        fromPicker.valueProperty().addListener((o, a, b) -> refresh.run());
        toPicker.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        HBox filters = Ui.row(Ui.field("Cerca", search), Ui.field("Categoria", cat), Ui.field("Tipus", kind),
                Ui.field("Període", when));
        HBox.setHgrow(filters.getChildren().get(0), Priority.ALWAYS);
        filters.getChildren().get(1).setStyle("-fx-min-width: 200; -fx-max-width: 260;");
        filters.getChildren().get(2).setStyle("-fx-min-width: 130; -fx-max-width: 150;");
        filters.getChildren().get(3).setStyle("-fx-min-width: 170; -fx-max-width: 190;");
        HBox summary = new HBox(12, count, clear);
        summary.setAlignment(Pos.CENTER_LEFT);

        Button export = new Button("Exporta (.csv)…");
        export.setOnAction(e -> ctx.exportMovementsCsv(true));
        return Ui.page(ExpenseForm.build(ctx),
                Ui.card(Ui.sectionHead("Moviments", export), filters, range, summary, results));
    }

    private static Node table(AppContext ctx, List<MovementFilter.Row> rows, boolean unfiltered) {
        SimpleTable t = new SimpleTable(1, "Data", "Concepte", "Categoria", ">Import", "");
        for (MovementFilter.Row r : rows) {
            if (r.movement() != null) expenseRow(ctx, t, r.movement());
            else incomeRow(ctx, t, r.income());
        }
        if (t.isEmpty()) {
            t.addMessage(unfiltered
                    ? "Encara no has apuntat res aquest mes. Afegeix la primera despesa amb el formulari de dalt, "
                    + "o marca com a pagat un pagament fix."
                    : "Cap moviment coincideix amb la cerca.");
        }
        return t.node();
    }

    private static void expenseRow(AppContext ctx, SimpleTable t, Movement m) {
        HBox concept = new HBox(6, SimpleTable.text(m.concept()));
        if (m.isFixed()) concept.getChildren().add(Ui.badge("fix", "st-n"));
        if (m.isAnnual()) concept.getChildren().add(Ui.badge("anual", "st-n"));
        else if (m.fromReserve()) concept.getChildren().add(Ui.badge("reserva", "st-n"));
        if (m.goalId() != null) concept.getChildren().add(Ui.badge("de «" + potName(ctx, m.goalId()) + "»", "st-n"));
        boolean planned = m.scheduledId() != null;
        Button del = Ui.link(planned ? "Desfés el pagament" : "Esborra", () -> {
            if (planned) ctx.update(p -> PaymentOps.unmarkPaid(p, m.scheduledId()));
            else ctx.update(p -> ExpenseOps.deleteMovement(p, m.id()));
        });
        del.getStyleClass().add("danger-link");
        t.add(SimpleTable.text(Format.dayMonth(m.date()), "num"), concept,
                Ui.categoryText(m.category(), "muted"), SimpleTable.money(m.amount().negate()), del);
    }

    private static void incomeRow(AppContext ctx, SimpleTable t, Income i) {
        HBox concept = new HBox(6, SimpleTable.text(i.concept().isEmpty() ? i.kind().label : i.concept()));
        String where = switch (i.destination()) {
            case BUDGET -> i.kind() == Income.Kind.CARRY ? "sobrant del mes anterior" : "al pressupost del mes";
            case RESERVE -> "a la reserva";
            case GOAL -> "a «" + potName(ctx, i.potId()) + "»";
            case SAVINGS -> "a l'estalvi";
            case CATEGORY -> "devolució";
        };
        concept.getChildren().add(Ui.badge(where, i.isRefund() ? "st-n" : "st-ok"));
        Node category = i.category() != null ? Ui.categoryText(i.category(), "muted") : Ui.label(i.kind().label, "muted");
        Node action;
        if (i.kind() == Income.Kind.CARRY) {
            action = Ui.label("ve del tancament", "small");
        } else {
            Button del = Ui.link("Esborra", () -> ctx.update(p -> IncomeOps.removeIncome(p, i.id())));
            del.getStyleClass().add("danger-link");
            action = del;
        }
        t.addStyled("row-income", SimpleTable.text(Format.dayMonth(i.date()), "num"), concept, category,
                SimpleTable.money(i.amount(), "inc"), action);
    }

    private static String potName(AppContext ctx, String id) {
        if (Pot.RESERVE_ID.equals(id)) return "reserva";
        return ctx.profile().ledger().pot(id).map(Pot::name).orElse("guardiola");
    }

    private static <T> javafx.util.StringConverter<T> converter(java.util.function.Function<T, String> f) {
        return new javafx.util.StringConverter<>() {
            @Override public String toString(T v) { return f.apply(v); }
            @Override public T fromString(String s) { return null; }
        };
    }
}
