package cat.pessic.app.monthclose;

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
        SimpleTable cats = new SimpleTable(0, "Categoria", ">Límit", ">Gastat", ">Diferència");
        for (CycleSummary.Envelope e : s.envelopes()) {
            BigDecimal spent = e.fixedPaid().add(e.variableSpent());
            if (e.limit().signum() == 0 && spent.signum() == 0) continue;
            BigDecimal diff = e.limit().subtract(spent);
            cats.add(Ui.categoryText(e.category()), SimpleTable.money(e.limit(), "muted"), SimpleTable.money(spent),
                    diff.signum() < 0 ? Ui.badge(Format.eur(diff), "st-bad") : SimpleTable.money(diff, "inc"));
        }
        if (cats.isEmpty()) cats.addMessage("Cap categoria amb límit o despesa.");
        BigDecimal leftover = d.leftover();
        VBox summary = new VBox(4,
                Ui.label("Ingressos " + Format.eur(s.income())
                        + (s.newMoney().signum() > 0 ? " + " + Format.eur(s.newMoney()) + " extra" : ""), "num"),
                Ui.label("Pressupost " + Format.eur(s.budget()) + " − gastat " + Format.eur(s.spent())
                        + (s.refunds().signum() > 0 ? " (ja descomptades " + Format.eur(s.refunds()) + " de devolucions)" : "")
                        + " = " + Format.eur(leftover), "num"),
                leftover.signum() >= 0
                        ? Ui.label("Ha sobrat " + Format.eur(leftover), "h2", "inc")
                        : Ui.label("T'has passat " + Format.eur(leftover.negate()), "h2", "text-bad"));
        VBox form = new VBox(14, summary, cats.node());
        if (!d.unpaidFixed().isEmpty()) {
            form.getChildren().add(Ui.wrapped("Aquests pagaments fixos no es van marcar com a pagats: "
                    + String.join(", ", d.unpaidFixed().stream().map(p -> p.payment().concept()).toList())
                    + ". Si els vas pagar, cancel·la, marca'ls a «Pagaments fixos» d'aquell mes i torna a tancar-lo.",
                    "text-warn"));
        }

        // ---- contributions
        Map<String, TextField> amounts = new LinkedHashMap<>();
        if (!d.contributions().isEmpty()) {
            VBox list = new VBox(8, Ui.label("Aportacions del mes", "h3"),
                    Ui.wrapped("El que passes del compte del dia a dia a la reserva i a les guardioles. Canvia l'import si "
                            + "aquest mes n'has posat més o menys; posa 0 si no n'has posat.", "small"));
            for (MonthClose.Contribution c : d.contributions()) {
                TextField f = Ui.amountField(c.planned());
                f.setPrefWidth(120);
                amounts.put(c.pot().id(), f);
                String name = c.pot().isReserve() ? "Reserva d'anuals" : "«" + c.pot().name() + "»";
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
            RadioButton savings = radio(dest, "Estalvi", CycleClose.Leftover.SAVINGS);
            savings.setSelected(true);
            RadioButton carry = radio(dest, "Al mes següent", CycleClose.Leftover.CARRY);
            RadioButton reserve = radio(dest, "A la reserva d'anuals", CycleClose.Leftover.RESERVE);
            RadioButton toGoal = radio(dest, "A una guardiola", CycleClose.Leftover.GOAL);
            toGoal.setDisable(goals.isEmpty());
            goal.disableProperty().bind(toGoal.selectedProperty().not());
            HBox goalBox = new HBox(6, toGoal, goal);
            goalBox.setAlignment(Pos.CENTER_LEFT);
            FlowPane options = new FlowPane(18, 8, savings, carry, reserve, goalBox);
            form.getChildren().add(new VBox(8, Ui.label("Què fas amb els " + Format.eur(leftover) + " que han sobrat?", "h3"),
                    options,
                    Ui.wrapped("«Al mes següent» els suma al pressupost del mes que ve. «Estalvi» vol dir que no els "
                            + "gastaràs.", "small")));
        } else if (leftover.signum() < 0) {
            form.getChildren().add(Ui.wrapped("El que t'has passat ha sortit del que havia de ser estalvi. El mes que ve, "
                    + "mira a «Límits» quina categoria cal ajustar.", "small"));
        }
        // ---- check the account against the bank
        BigDecimal estimate = Cash.estimate(ctx.profile(), ctx.today());
        TextField bank = Ui.amountField(null);
        bank.setPrefWidth(140);
        bank.setPromptText(estimate == null ? "opcional" : Format.input(estimate));
        javafx.scene.control.Label diff = Ui.wrapped("", "small");
        bank.textProperty().addListener((o, a, b) -> diff.setText(bank.getText().isBlank() ? ""
                : AccountUi.differenceText(estimate, Ui.amount(bank))));
        HBox bankRow = new HBox(10, Ui.label("Saldo que diu el banc avui"), spacer(), bank);
        bankRow.setAlignment(Pos.CENTER_LEFT);
        form.getChildren().add(new VBox(8, Ui.label("Comprova el compte", "h3"),
                Ui.wrapped(estimate == null
                        ? "Si escrius quant tens avui al compte on cobres, Pessic en seguirà el saldo a partir d'ara."
                        : "Pessic calcula que avui hi tens " + Format.eur(estimate) + ". Escriu el que diu el banc i "
                        + "veuràs si hi ha despeses sense apuntar (la diferència queda a «Històric»). Deixa-ho en blanc per "
                        + "no comprovar-ho.", "small"),
                bankRow, diff));
        form.setPrefWidth(640);

        record Choice(Map<String, BigDecimal> amounts, CycleClose.Leftover to, String goalId, BigDecimal bank) {}
        EditDialog.<Choice>show(ctx.window(), "Tanca " + month.toLowerCase(), form, false, "", "Tanca el mes", () -> {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            for (var en : amounts.entrySet()) {
                var v = en.getValue().getText().isBlank() ? java.util.Optional.of(BigDecimal.ZERO)
                        : Ui.amount(en.getValue()).filter(x -> x.signum() >= 0);
                if (v.isEmpty()) return EditDialog.Check.fail("Hi ha una aportació que no s'entén.");
                values.put(en.getKey(), v.get());
            }
            CycleClose.Leftover to = dest.getSelectedToggle() == null ? CycleClose.Leftover.SAVINGS
                    : (CycleClose.Leftover) dest.getSelectedToggle().getUserData();
            if (to == CycleClose.Leftover.GOAL && goal.getValue() == null) return EditDialog.Check.fail("Tria la guardiola.");
            BigDecimal actual = null;
            if (!bank.getText().isBlank()) {
                var v = Ui.amount(bank);
                if (v.isEmpty()) return EditDialog.Check.fail("El saldo del banc no s'entén.");
                actual = v.get();
            }
            return EditDialog.Check.ok(new Choice(values, to, goal.getValue() == null ? null : goal.getValue().id(), actual));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Choice>(Choice c)) {
                if (ctx.update(p -> MonthClose.close(p, key, c.amounts(), c.to(), c.goalId(), c.bank(), ctx.today()))) {
                    Ui.info(ctx.window(), month + " tancat. El trobaràs a «Històric».");
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
