package cat.pessic.app.expense;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.common.Ui;
import cat.pessic.app.income.DestinationPicker;
import cat.pessic.core.budget.Category;
import cat.pessic.core.expense.ExpenseOps;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.savings.Pot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * "Apunta": the quick form for day-to-day expenses, extra money that comes in (an extra paycheck, a
 * bonus, a Bizum) and refunds of something bought.
 */
public final class ExpenseForm {

    enum Mode { EXPENSE, INCOME, REFUND }

    /** Remembered between redraws so several entries can be made in a row. */
    private static LocalDate lastDate;
    private static Category lastCategory = Category.FOOD;
    private static Mode mode = Mode.EXPENSE;
    private static boolean focusAfterRedraw;

    /** Id of the "monthly budget" choice in "Es paga amb". */
    private static final String MONTH = "";

    public static VBox build(AppContext ctx) {
        ToggleGroup modes = new ToggleGroup();
        HBox modeBar = new HBox(4);
        modeBar.getStyleClass().add("segmented");
        for (Mode m : Mode.values()) {
            ToggleButton b = new ToggleButton(switch (m) {
                case EXPENSE -> t("expense.form.modeExpense");
                case INCOME -> t("expense.form.modeIncome");
                case REFUND -> t("expense.form.modeRefund");
            });
            b.setToggleGroup(modes);
            b.setSelected(m == mode);
            b.getStyleClass().add("segment");
            b.setOnAction(e -> {
                if (mode == m) { b.setSelected(true); return; }
                mode = m;
                focusAfterRedraw = true;
                ctx.update(p -> p); // redraw with the other form
            });
            modeBar.getChildren().add(b);
        }

        VBox card = switch (mode) {
            case EXPENSE -> expense(ctx);
            case INCOME -> income(ctx);
            case REFUND -> refund(ctx);
        };
        card.getChildren().add(0, Ui.sectionHead(t("expense.form.title"), modeBar));
        return card;
    }

    private static DatePicker datePicker(AppContext ctx) {
        DatePicker date = Ui.datePicker(lastDate != null ? lastDate : ctx.today());
        date.setPrefWidth(150);
        return date;
    }

    private static LocalDate dateOf(DatePicker date) {
        return date.getConverter().fromString(date.getEditor().getText());
    }

    /** Remembers the date and moves to the cycle of the entry if it is another one. */
    private static void after(AppContext ctx, LocalDate d) {
        var target = ctx.calc().cycleContaining(d).key();
        if (!target.equals(ctx.cycle())) ctx.goToCycle(target);
    }

    private static HBox line(javafx.scene.Node... fields) {
        HBox line = Ui.row(fields);
        HBox.setHgrow(line.getChildren().get(1), Priority.ALWAYS);
        line.getChildren().get(0).setStyle("-fx-min-width: 150; -fx-max-width: 160;");
        line.getChildren().get(2).setStyle("-fx-min-width: 220; -fx-max-width: 280;");
        line.getChildren().get(3).setStyle("-fx-min-width: 110; -fx-max-width: 140;");
        return line;
    }

    private static void focusLater(TextField concept) {
        if (focusAfterRedraw) {
            focusAfterRedraw = false;
            javafx.application.Platform.runLater(concept::requestFocus);
        }
    }

    // ---------------------------------------------------------------- expense

    private static VBox expense(AppContext ctx) {
        DatePicker date = datePicker(ctx);
        TextField concept = new TextField();
        concept.setPromptText(t("expense.form.expensePrompt"));
        ComboBox<Category> category = Ui.categoryBox(lastCategory);
        Label categoryHelp = Ui.categoryHelp(category);
        TextField amount = Ui.amountField(null);
        amount.setPrefWidth(120);
        ComboBox<String> paidFrom = paidFromBox(ctx);
        Label error = Ui.errorLabel();
        var add = Ui.primary(t("expense.form.add"));

        Runnable submit = () -> {
            Optional<BigDecimal> imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            LocalDate d = dateOf(date);
            String c = concept.getText() == null ? "" : concept.getText().strip();
            if (d == null) { Ui.showError(error, t("common.errorPickDate")); return; }
            if (c.isEmpty()) { Ui.showError(error, t("common.errorConcept")); concept.requestFocus(); return; }
            if (imp.isEmpty()) { Ui.showError(error, t("expense.form.errorAmountExample")); amount.requestFocus(); return; }
            lastDate = d.equals(ctx.today()) ? null : d;
            lastCategory = category.getValue();
            focusAfterRedraw = true;
            String from = paidFrom.getValue() == null ? MONTH : paidFrom.getValue();
            boolean reserve = Pot.RESERVE_ID.equals(from);
            String goal = reserve || from.equals(MONTH) ? null : from;
            if (ctx.update(p -> ExpenseOps.addMovement(p, d, c, category.getValue(), imp.get(), reserve, goal))) after(ctx, d);
        };
        add.setOnAction(e -> submit.run());
        concept.setOnAction(e -> amount.requestFocus());
        amount.setOnAction(e -> submit.run());
        focusLater(concept);

        HBox from = new HBox(8, Ui.label(t("expense.form.paidWith"), "muted"), paidFrom);
        from.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return Ui.card(line(Ui.field(t("common.date"), date), Ui.field(t("common.concept"), concept), Ui.field(t("common.category"), category),
                        Ui.field(t("common.amountEur"), amount), add),
                categoryHelp, from,
                Ui.wrapped(t("expense.form.expenseHint"), "small"),
                error);
    }

    /** "Els diners del mes", "La reserva d'anuals" and the active goals. */
    private static ComboBox<String> paidFromBox(AppContext ctx) {
        List<Pot> goals = ctx.profile().ledger().activeGoals();
        ComboBox<String> box = new ComboBox<>();
        box.getItems().add(MONTH);
        box.getItems().add(Pot.RESERVE_ID);
        goals.forEach(g -> box.getItems().add(g.id()));
        box.setValue(MONTH);
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String id) {
                if (id == null || id.equals(MONTH)) return t("expense.form.paidWithMonth");
                if (id.equals(Pot.RESERVE_ID)) return t("common.theAnnualReserve");
                return t("expense.form.paidWithGoal", goals.stream().filter(g -> g.id().equals(id)).map(Pot::name).findFirst().orElse(id));
            }
            @Override public String fromString(String s) { return null; }
        });
        return box;
    }

    // ---------------------------------------------------------------- income

    private static VBox income(AppContext ctx) {
        DatePicker date = datePicker(ctx);
        TextField concept = new TextField();
        concept.setPromptText(t("expense.form.incomePrompt"));
        ComboBox<Income.Kind> kind = kindBox(Income.Kind.OTHER);
        TextField amount = Ui.amountField(null);
        DestinationPicker dest = new DestinationPicker(ctx.profile().ledger().activeGoals(), null, null);
        Label error = Ui.errorLabel();
        var add = Ui.primary(t("expense.form.add"));

        Runnable submit = () -> {
            Optional<BigDecimal> imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            LocalDate d = dateOf(date);
            if (d == null) { Ui.showError(error, t("common.errorPickDate")); return; }
            if (imp.isEmpty()) { Ui.showError(error, t("expense.form.errorAmount")); amount.requestFocus(); return; }
            String bad = dest.check();
            if (bad != null) { Ui.showError(error, bad); return; }
            lastDate = d.equals(ctx.today()) ? null : d;
            focusAfterRedraw = true;
            String c = concept.getText() == null ? "" : concept.getText().strip();
            if (ctx.update(p -> IncomeOps.addIncome(p, d, c, imp.get(), kind.getValue(), dest.value(), null,
                    dest.goalId(), null))) after(ctx, d);
        };
        add.setOnAction(e -> submit.run());
        concept.setOnAction(e -> amount.requestFocus());
        focusLater(concept);

        return Ui.card(line(Ui.field(t("common.date"), date), Ui.field(t("common.concept"), concept), Ui.field(t("common.type"), kind),
                        Ui.field(t("common.amountEur"), amount), add),
                dest.node(),
                Ui.wrapped(t("expense.form.incomeHint"),
                        "small"),
                error);
    }

    static ComboBox<Income.Kind> kindBox(Income.Kind selected) {
        ComboBox<Income.Kind> box = new ComboBox<>();
        box.getItems().setAll(Income.Kind.EXTRA_PAY, Income.Kind.BONUS, Income.Kind.OTHER);
        box.setValue(selected);
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Income.Kind k) { return k == null ? "" : k.label(); }
            @Override public Income.Kind fromString(String s) { return null; }
        });
        return box;
    }

    // ---------------------------------------------------------------- refund

    private static VBox refund(AppContext ctx) {
        DatePicker date = datePicker(ctx);
        TextField concept = new TextField();
        concept.setPromptText(t("expense.form.refundPrompt"));
        ComboBox<Category> category = Ui.categoryBox(lastCategory);
        TextField amount = Ui.amountField(null);
        Label error = Ui.errorLabel();
        var add = Ui.primary(t("expense.form.add"));

        Runnable submit = () -> {
            Optional<BigDecimal> imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            LocalDate d = dateOf(date);
            String c = concept.getText() == null ? "" : concept.getText().strip();
            if (d == null) { Ui.showError(error, t("common.errorPickDate")); return; }
            if (c.isEmpty()) { Ui.showError(error, t("expense.form.errorRefundConcept")); concept.requestFocus(); return; }
            if (imp.isEmpty()) { Ui.showError(error, t("expense.form.errorAmount")); amount.requestFocus(); return; }
            lastDate = d.equals(ctx.today()) ? null : d;
            lastCategory = category.getValue();
            focusAfterRedraw = true;
            if (ctx.update(p -> IncomeOps.addIncome(p, d, c, imp.get(), Income.Kind.REFUND, Income.Destination.CATEGORY,
                    category.getValue(), null, null))) after(ctx, d);
        };
        add.setOnAction(e -> submit.run());
        concept.setOnAction(e -> amount.requestFocus());
        amount.setOnAction(e -> submit.run());
        focusLater(concept);

        return Ui.card(line(Ui.field(t("common.date"), date), Ui.field(t("common.concept"), concept), Ui.field(t("common.category"), category),
                        Ui.field(t("common.amountEur"), amount), add),
                Ui.wrapped(t("expense.form.refundHint"), "small"),
                error);
    }

    private ExpenseForm() {}
}
