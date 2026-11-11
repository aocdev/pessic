package cat.pessic.app.account;

import cat.pessic.app.AppContext;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.Ui;
import cat.pessic.core.account.AccountOps;
import cat.pessic.core.account.Cash;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.Pots;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The day-to-day account on screen: asking for its balance, showing the estimate and what is left
 * before payday, correcting it to what the bank says, and moving money already there into the
 * reserve and goals.
 */
public final class AccountUi {
    private AccountUi() {}

    /** Resum card: the question when the balance is unknown, otherwise the estimate. */
    public static Node card(AppContext ctx) {
        var p = ctx.profile();
        LocalDate today = ctx.today();
        if (p.ledger().account() == null) {
            TextField amount = Ui.amountField(null);
            amount.setPrefWidth(140);
            Label error = Ui.errorLabel();
            Button save = Ui.primary("Desa");
            Runnable submit = () -> {
                var v = Ui.amount(amount);
                if (v.isEmpty()) { Ui.showError(error, "Escriu el saldo, per exemple 512,40 (pot ser negatiu)."); return; }
                if (ctx.update(q -> AccountOps.setAccountBalance(q, v.get(), today))) offerFunding(ctx);
            };
            save.setOnAction(e -> submit.run());
            amount.setOnAction(e -> submit.run());
            HBox ask = new HBox(10, Ui.label("Avui hi tinc", "muted"), amount, Ui.label("€", "muted"), save);
            ask.setAlignment(Pos.CENTER_LEFT);
            return Ui.card(Ui.sectionHead("Quant tens al compte on cobres la nòmina?"),
                    Ui.wrapped("Mira el saldo a l'app del banc i escriu-lo aquí. A partir d'ara Pessic el seguirà: hi sumarà la "
                            + "nòmina i hi restarà el que apuntis, i et dirà quant et quedarà abans de tornar a cobrar. "
                            + "Els comptes d'estalvi o d'inversió no cal: el que hi envies és una sortida més.", "muted"),
                    ask, error);
        }

        Cash.Forecast f = Cash.forecast(p, today);
        Label big = Ui.label(Format.eur(f.today()), "kpi");
        if (f.today().signum() < 0) big.getStyleClass().add("text-bad");
        VBox now = new VBox(2, Ui.label("Al compte avui (estimat)", "muted"), big,
                Ui.label("des del saldo del " + Format.date(p.ledger().account().since()), "small"));
        Label before = Ui.label(Format.eur(f.beforePayday()), "kpi");
        if (f.beforePayday().signum() < 0) before.getStyleClass().add("text-bad");
        VBox end = new VBox(2, Ui.label("Abans de tornar a cobrar", "muted"), before,
                Ui.label("el " + Format.dayMonth(f.cycle().end()) + ", si pagues el que falta i gastes tot el pressupost", "small"));
        Button adjust = Ui.link("Ajusta amb el banc", () -> adjust(ctx));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(32, now, end, spacer, adjust);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card(row);
        if (f.goesNegative()) {
            card.getChildren().add(Ui.wrapped("Compte: el " + Format.dayMonth(f.lowestDate()) + " el saldo baixaria fins a "
                    + Format.eur(f.lowest()) + ". Mira «Pagaments fixos» per veure quin pagament no hi cap.", "text-bad"));
        }
        return card;
    }

    /** "Ajusta amb el banc": records the difference between the bank and the estimate. */
    public static void adjust(AppContext ctx) {
        LocalDate today = ctx.today();
        BigDecimal estimate = Cash.estimate(ctx.profile(), today);
        TextField amount = Ui.amountField(estimate);
        Label diff = Ui.wrapped("", "small");
        amount.textProperty().addListener((o, a, b) -> diff.setText(differenceText(estimate, Ui.amount(amount))));
        VBox form = new VBox(12, Ui.row(Ui.field("Saldo que diu el banc avui (€)", amount)), diff,
                Ui.wrapped("Pessic calcula " + Format.eur(estimate) + ". Si el banc diu una altra cosa, sol ser una despesa "
                        + "que no has apuntat (o un ingrés). Pessic hi afegeix un ajust per la diferència.", "small"));
        EditDialog.<BigDecimal>show(ctx.window(), "Ajusta el saldo del compte", form, false, "", () -> {
            var v = Ui.amount(amount);
            return v.isPresent() ? EditDialog.Check.ok(v.get()) : EditDialog.Check.fail("Escriu el saldo del banc.");
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<BigDecimal>(BigDecimal v)) {
                ctx.update(q -> AccountOps.adjustAccount(q, v, today, null));
            }
        });
    }

    /** "Diferència −23,40 €: potser hi ha despeses sense apuntar." */
    public static String differenceText(BigDecimal estimate, Optional<BigDecimal> actual) {
        if (estimate == null || actual.isEmpty()) return "";
        BigDecimal d = actual.get().subtract(estimate);
        if (d.signum() == 0) return "Quadra amb el que calcula Pessic.";
        return "Diferència " + Format.eur(d) + (d.signum() < 0
                ? ": potser hi ha despeses sense apuntar."
                : ": potser hi ha algun ingrés sense apuntar.");
    }

    /**
     * Right after the balance is first given: offers to move part of it into the reserve (what it
     * needs for the next 12 months of annual payments) and into the goals. Does nothing when there
     * is nothing to fund.
     */
    public static void offerFunding(AppContext ctx) {
        var p = ctx.profile();
        LocalDate today = ctx.today();
        BigDecimal balance = Cash.estimate(p, today);
        if (balance == null || balance.signum() <= 0) return;
        Pot reserve = p.ledger().reserve();
        BigDecimal needed = Pots.reserveNeededNow(p, today)
                .subtract(reserve.openingSet() ? Pots.balance(reserve, p) : BigDecimal.ZERO).max(BigDecimal.ZERO);
        boolean hasAnnuals = !p.config().annuals().isEmpty();
        var goals = p.ledger().activeGoals();
        if (!hasAnnuals && goals.isEmpty()) return;

        Map<String, TextField> fields = new LinkedHashMap<>();
        VBox rows = new VBox(8);
        if (hasAnnuals) {
            TextField f = Ui.amountField(needed.min(balance));
            fields.put(Pot.RESERVE_ID, f);
            rows.getChildren().add(fundRow("Reserva d'anuals", f, needed.signum() > 0
                    ? "Amb " + Format.eur(needed) + " n'hi ha prou per a tots els anuals dels pròxims 12 mesos."
                    : "Ja té prou per als pròxims 12 mesos."));
        }
        for (Pot g : goals) {
            TextField f = Ui.amountField(null);
            fields.put(g.id(), f);
            rows.getChildren().add(fundRow("«" + g.name() + "»", f, null));
        }
        Label left = Ui.label("", "h3");
        Runnable recompute = () -> {
            BigDecimal sum = fields.values().stream().map(t -> Ui.amount(t).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal rest = balance.subtract(sum);
            left.setText("Et quedaran " + Format.eur(rest) + " al compte");
            left.getStyleClass().remove("text-bad");
            if (rest.signum() < 0) left.getStyleClass().add("text-bad");
        };
        fields.values().forEach(t -> t.textProperty().addListener((o, a, b) -> recompute.run()));
        recompute.run();
        VBox form = new VBox(14,
                Ui.wrapped("Tens " + Format.eur(balance) + " al compte. Si una part ja és per als anuals o per a una guardiola, "
                        + "passa-la ara: Pessic la treu del saldo del compte i la posa a la reserva o a la guardiola. "
                        + "Deixa-ho a 0 si no vols apartar res.", "muted"),
                rows, left);
        form.setPrefWidth(600);
        EditDialog.<Map<String, BigDecimal>>show(ctx.window(), "Vols apartar diners que ja tens?", form, false, "",
                "Aparta'ls", () -> {
                    Map<String, BigDecimal> values = new LinkedHashMap<>();
                    for (var en : fields.entrySet()) {
                        String t = en.getValue().getText();
                        var v = t.isBlank() ? Optional.of(BigDecimal.ZERO) : Ui.amount(en.getValue()).filter(x -> x.signum() >= 0);
                        if (v.isEmpty()) return EditDialog.Check.fail("Hi ha un import que no s'entén.");
                        values.put(en.getKey(), v.get());
                    }
                    return EditDialog.Check.ok(values);
                }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Map<String, BigDecimal>>(Map<String, BigDecimal> v)) {
                ctx.update(q -> AccountOps.fundPots(q, v, today));
            }
        });
    }

    private static Node fundRow(String name, TextField field, String help) {
        field.setPrefWidth(140);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, Ui.label(name), spacer, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return help == null ? row : new VBox(2, row, Ui.wrapped(help, "small"));
    }
}
