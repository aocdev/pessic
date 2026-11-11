package cat.pessic.app.monthclose;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.account.AccountUi;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.core.account.Cash;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.monthclose.MonthClose;
import cat.pessic.core.savings.Pot;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Closing a month: what happened (planned against real per category), the monthly contributions to
 * the reserve and the goals, and where the leftover goes. Writes a snapshot for "Històric".
 */
public final class MonthCloseDialog {
    private MonthCloseDialog() {}

    public static void show(AppContext ctx, YearMonth key) {
        MonthClose.Draft d = MonthClose.draft(ctx.profile(), key, ctx.today());
        CycleSummary s = d.summary();
        String month = Format.month(key);

        // ---- what happened
        SimpleTable cats = new SimpleTable(0, t("common.category"), ">" + t("common.limit"), ">" + t("common.spent"), ">" + t("monthclose.difference"));
        for (CycleSummary.Envelope e : s.envelopes()) {
            BigDecimal spent = e.fixedPaid().add(e.variableSpent());
            if (e.limit().signum() == 0 && spent.signum() == 0) continue;
            BigDecimal diff = e.limit().subtract(spent);
            cats.add(Ui.categoryText(e.category()), SimpleTable.money(e.limit(), "muted"), SimpleTable.money(spent),
                    diff.signum() < 0 ? Ui.badge(Format.eur(diff), "st-bad") : SimpleTable.money(diff, "inc"));
        }
        if (cats.isEmpty()) cats.addMessage(t("monthclose.noCategories"));
        BigDecimal leftover = d.leftover();
        VBox summary = new VBox(4,
                Ui.label(t("monthclose.income", Format.eur(s.income()), (s.newMoney().signum() > 0 ? t("monthclose.incomeExtra", Format.eur(s.newMoney())) : "")), "num"),
                Ui.label(t("monthclose.leftoverFormula", Format.eur(s.budget()), Format.eur(s.spent()), (s.refunds().signum() > 0 ? t("monthclose.refundsDeducted", Format.eur(s.refunds())) : ""), Format.eur(leftover)), "num"),
                leftover.signum() >= 0
                        ? Ui.label(t("monthclose.leftOver", Format.eur(leftover)), "h2", "inc")
                        : Ui.label(t("monthclose.overspent", Format.eur(leftover.negate())), "h2", "text-bad"));
        VBox form = new VBox(14, summary, cats.node());
        if (!d.unpaidFixed().isEmpty()) {
            form.getChildren().add(Ui.wrapped(t("monthclose.unpaidFixed", String.join(", ", d.unpaidFixed().stream().map(p -> p.payment().concept()).toList())),
                    "text-warn"));
        }

        // ---- contributions
        Map<String, TextField> amounts = new LinkedHashMap<>();
        if (!d.contributions().isEmpty()) {
            VBox list = new VBox(8, Ui.label(t("monthclose.contributions.title"), "h3"),
                    Ui.wrapped(t("monthclose.contributions.hint"), "small"));
            for (MonthClose.Contribution c : d.contributions()) {
                TextField f = Ui.amountField(c.planned());
                f.setPrefWidth(120);
                amounts.put(c.pot().id(), f);
                String name = c.pot().isReserve() ? t("common.annualReserve") : "«" + c.pot().name() + "»";
                HBox row = new HBox(10, Ui.label(name), spacer(), f);
                row.setAlignment(Pos.CENTER_LEFT);
                list.getChildren().add(row);
            }
            form.getChildren().add(list);
        }

        // ---- leftover
        ToggleGroup dest = new ToggleGroup();
        List<Pot> goals = ctx.profile().ledger().activeGoals();
        ComboBox<Pot> goal = new ComboBox<>();
        goal.getItems().setAll(goals);
        goal.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Pot p) { return p == null ? "" : p.name(); }
            @Override public Pot fromString(String x) { return null; }
        });
        if (goals.size() == 1) goal.setValue(goals.get(0));
        if (leftover.signum() > 0) {
            RadioButton savings = radio(dest, t("common.savings"), CycleClose.Leftover.SAVINGS);
            savings.setSelected(true);
            RadioButton carry = radio(dest, t("monthclose.leftoverToNextMonth"), CycleClose.Leftover.CARRY);
            RadioButton reserve = radio(dest, t("monthclose.leftoverToReserve"), CycleClose.Leftover.RESERVE);
            RadioButton toGoal = radio(dest, t("monthclose.leftoverToGoal"), CycleClose.Leftover.GOAL);
            toGoal.setDisable(goals.isEmpty());
            goal.disableProperty().bind(toGoal.selectedProperty().not());
            HBox goalBox = new HBox(6, toGoal, goal);
            goalBox.setAlignment(Pos.CENTER_LEFT);
            FlowPane options = new FlowPane(18, 8, savings, carry, reserve, goalBox);
            form.getChildren().add(new VBox(8, Ui.label(t("monthclose.leftoverQuestion", Format.eur(leftover)), "h3"),
                    options,
                    Ui.wrapped(t("monthclose.leftoverHint"), "small")));
        } else if (leftover.signum() < 0) {
            form.getChildren().add(Ui.wrapped(t("monthclose.overspentHint"), "small"));
        }
        // ---- check the account against the bank
        BigDecimal estimate = Cash.estimate(ctx.profile(), ctx.today());
        TextField bank = Ui.amountField(null);
        bank.setPrefWidth(140);
        bank.setPromptText(estimate == null ? t("monthclose.optional") : Format.input(estimate));
        javafx.scene.control.Label diff = Ui.wrapped("", "small");
        bank.textProperty().addListener((o, a, b) -> diff.setText(bank.getText().isBlank() ? ""
                : AccountUi.differenceText(estimate, Ui.amount(bank))));
        HBox bankRow = new HBox(10, Ui.label(t("monthclose.bankBalance")), spacer(), bank);
        bankRow.setAlignment(Pos.CENTER_LEFT);
        form.getChildren().add(new VBox(8, Ui.label(t("monthclose.checkAccount"), "h3"),
                Ui.wrapped(estimate == null
                        ? t("monthclose.checkAccountNoBalance")
                        : t("monthclose.checkAccountHint", Format.eur(estimate)), "small"),
                bankRow, diff));
        form.setPrefWidth(640);

        record Choice(Map<String, BigDecimal> amounts, CycleClose.Leftover to, String goalId, BigDecimal bank) {}
        EditDialog.<Choice>show(ctx.window(), t("monthclose.title", month.toLowerCase()), form, false, "", t("monthclose.closeButton"), () -> {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (var en : amounts.entrySet()) {
                var v = en.getValue().getText().isBlank() ? java.util.Optional.of(BigDecimal.ZERO)
                        : Ui.amount(en.getValue()).filter(x -> x.signum() >= 0);
                if (v.isEmpty()) return EditDialog.Check.fail(t("monthclose.errorContribution"));
                values.put(en.getKey(), v.get());
            }
            CycleClose.Leftover to = dest.getSelectedToggle() == null ? CycleClose.Leftover.SAVINGS
                    : (CycleClose.Leftover) dest.getSelectedToggle().getUserData();
            if (to == CycleClose.Leftover.GOAL && goal.getValue() == null) return EditDialog.Check.fail(t("common.errorPickGoal"));
            BigDecimal actual = null;
            if (!bank.getText().isBlank()) {
                var v = Ui.amount(bank);
                if (v.isEmpty()) return EditDialog.Check.fail(t("monthclose.errorBankBalance"));
                actual = v.get();
            }
            return EditDialog.Check.ok(new Choice(values, to, goal.getValue() == null ? null : goal.getValue().id(), actual));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Choice>(Choice c)) {
                if (ctx.update(p -> MonthClose.close(p, key, c.amounts(), c.to(), c.goalId(), c.bank(), ctx.today()))) {
                    Ui.info(ctx.window(), t("monthclose.closed", month));
                }
            }
        });
    }

    private static RadioButton radio(ToggleGroup g, String text, CycleClose.Leftover v) {
        RadioButton r = new RadioButton(text);
        r.setUserData(v);
        r.setToggleGroup(g);
        return r;
    }

    private static javafx.scene.layout.Region spacer() {
        var r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }
}
