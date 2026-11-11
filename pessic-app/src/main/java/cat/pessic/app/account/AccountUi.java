package cat.pessic.app.account;

import static cat.pessic.core.common.I18n.t;
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
            Button save = Ui.primary(t("common.save"));
            Runnable submit = () -> {
                var v = Ui.amount(amount);
                if (v.isEmpty()) { Ui.showError(error, t("account.errorBalanceExample")); return; }
                if (ctx.update(q -> AccountOps.setAccountBalance(q, v.get(), today))) offerFunding(ctx);
            };
            save.setOnAction(e -> submit.run());
            amount.setOnAction(e -> submit.run());
            HBox ask = new HBox(10, Ui.label(t("account.todayIHave"), "muted"), amount, Ui.label("€", "muted"), save);
            ask.setAlignment(Pos.CENTER_LEFT);
            return Ui.card(Ui.sectionHead(t("account.askTitle")),
                    Ui.wrapped(t("account.askHint"), "muted"),
                    ask, error);
        }

        Cash.Forecast f = Cash.forecast(p, today);
        Label big = Ui.label(Format.eur(f.today()), "kpi");
        if (f.today().signum() < 0) big.getStyleClass().add("text-bad");
        VBox now = new VBox(2, Ui.label(t("common.inAccountTodayEstimated"), "muted"), big,
                Ui.label(t("account.sinceBalanceOf", Format.date(p.ledger().account().since())), "small"));
        Label before = Ui.label(Format.eur(f.beforePayday()), "kpi");
        if (f.beforePayday().signum() < 0) before.getStyleClass().add("text-bad");
        VBox end = new VBox(2, Ui.label(t("common.beforeNextPayday"), "muted"), before,
                Ui.label(t("account.beforePaydayHint", Format.dayMonth(f.cycle().end())), "small"));
        Button adjust = Ui.link(t("account.adjustWithBank"), () -> adjust(ctx));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(32, now, end, spacer, adjust);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card(row);
        if (f.goesNegative()) {
            card.getChildren().add(Ui.wrapped(t("account.goesNegativeWarning", Format.dayMonth(f.lowestDate()), Format.eur(f.lowest())), "text-bad"));
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
        VBox form = new VBox(12, Ui.row(Ui.field(t("common.bankBalanceToday"), amount)), diff,
                Ui.wrapped(t("account.adjustHint", Format.eur(estimate)), "small"));
        EditDialog.<BigDecimal>show(ctx.window(), t("account.adjustTitle"), form, false, "", () -> {
            var v = Ui.amount(amount);
            return v.isPresent() ? EditDialog.Check.ok(v.get()) : EditDialog.Check.fail(t("account.errorBankBalance"));
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
        if (d.signum() == 0) return t("account.matches");
        return t("account.difference", Format.eur(d), (d.signum() < 0
                ? t("account.maybeUnrecordedExpenses")
                : t("account.maybeUnrecordedIncome")));
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
            rows.getChildren().add(fundRow(t("common.annualReserve"), f, needed.signum() > 0
                    ? t("account.reserveNeeded", Format.eur(needed))
                    : t("account.reserveEnough")));
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
            left.setText(t("account.leftInAccount", Format.eur(rest)));
            left.getStyleClass().remove("text-bad");
            if (rest.signum() < 0) left.getStyleClass().add("text-bad");
        };
        fields.values().forEach(t -> t.textProperty().addListener((o, a, b) -> recompute.run()));
        recompute.run();
        VBox form = new VBox(14,
                Ui.wrapped(t("account.fundHint", Format.eur(balance)), "muted"),
                rows, left);
        form.setPrefWidth(600);
        EditDialog.<Map<String, BigDecimal>>show(ctx.window(), t("account.fundTitle"), form, false, "",
                t("account.fundButton"), () -> {
                    Map<String, BigDecimal> values = new LinkedHashMap<>();
                    for (var en : fields.entrySet()) {
                        String t = en.getValue().getText();
                        var v = t.isBlank() ? Optional.of(BigDecimal.ZERO) : Ui.amount(en.getValue()).filter(x -> x.signum() >= 0);
                        if (v.isEmpty()) return EditDialog.Check.fail(t("account.errorAmount"));
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
