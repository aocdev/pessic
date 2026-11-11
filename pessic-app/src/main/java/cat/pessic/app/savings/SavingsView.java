package cat.pessic.app.savings;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.MonthBox;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.core.savings.Pots;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Estalvi": the real balance of the annual reserve and the savings goals ("guardioles"). */
public final class SavingsView {
    private SavingsView() {}

    public static Region build(AppContext ctx) {
        return Ui.page(reserve(ctx), goals(ctx));
    }

    // ---------------------------------------------------------------- reserve

    private static Node reserve(AppContext ctx) {
        var p = ctx.profile();
        Pot reserve = p.ledger().reserve();
        BudgetCalculator calc = BudgetCalculator.of(p);
        BigDecimal monthly = Money.round(calc.monthlyReserve());
        List<Node> body = new ArrayList<>();

        if (!reserve.openingSet()) {
            TextField amount = Ui.amountField(null);
            amount.setPrefWidth(140);
            Label error = Ui.errorLabel();
            Button save = Ui.primary(t("common.save"));
            Runnable submit = () -> {
                var v = Ui.amount(amount).filter(x -> x.signum() >= 0);
                if (v.isEmpty()) { Ui.showError(error, t("savings.reserve.errorOpening")); return; }
                ctx.update(q -> SavingsOps.setReserveBalance(q, v.get(), ctx.today()));
            };
            save.setOnAction(e -> submit.run());
            amount.setOnAction(e -> submit.run());
            HBox ask = new HBox(10, Ui.label(t("savings.reserve.iHave"), "muted"), amount, Ui.label(t("savings.reserve.inReserveToday"), "muted"), save);
            ask.setAlignment(Pos.CENTER_LEFT);
            body.add(Ui.sectionHead(t("common.annualReserve")));
            body.add(Ui.wrapped(t("savings.reserve.askHint"), "muted"));
            body.add(ask);
            body.add(error);
            if (monthly.signum() > 0) {
                body.add(Ui.wrapped(t("savings.reserve.monthlyNeeded", Format.eur(monthly)), "small"));
            }
            return Ui.card(body.toArray(Node[]::new));
        }

        BigDecimal balance = Pots.balance(reserve, p);
        Button in = new Button(t("savings.reserve.putIn"));
        in.setOnAction(e -> entry(ctx, reserve, true));
        Button out = new Button(t("savings.reserve.takeOut"));
        out.setOnAction(e -> entry(ctx, reserve, false));
        Button adjust = Ui.link(t("savings.adjustBalance"), () -> adjust(ctx, reserve, balance));
        body.add(Ui.sectionHead(t("common.annualReserve"), adjust, out, in));
        Label big = Ui.label(Format.eur(balance), "hero-secondary");
        if (balance.signum() < 0) big.getStyleClass().add("text-bad");
        body.add(new VBox(2, Ui.label(t("savings.reserve.balanceToday"), "muted"), big,
                Ui.label(t("savings.reserve.facts", Format.eur(monthly), Format.eur(monthly.multiply(BigDecimal.valueOf(12))), Format.date(reserve.since())), "small")));

        Optional<Pots.Shortfall> sf = Pots.reserveShortfall(p, ctx.today());
        if (sf.isPresent()) {
            body.add(Ui.wrapped(t("savings.reserve.shortfall", Format.month(sf.get().cycle()).toLowerCase(), Format.eur(sf.get().due()), Format.eur(sf.get().missing())), "text-bad"));
        } else if (monthly.signum() > 0) {
            body.add(Ui.wrapped(t("savings.reserve.enough"),
                    "inc"));
        }

        // coming annual payments
        YearMonth now = calc.cycleContaining(ctx.today()).key();
        SimpleTable next = new SimpleTable(1, t("common.month"), t("common.payment"), ">" + t("common.amount"));
        for (int i = 0; i < 12; i++) {
            YearMonth k = now.plusMonths(i);
            for (var line : calc.annualDue(k, p.movements())) {
                if (line.paid()) continue;
                AnnualItem a = line.item();
                next.add(SimpleTable.text(Format.month(k)), SimpleTable.text(a.concept()), SimpleTable.money(a.amount()));
            }
        }
        if (!next.isEmpty()) body.add(new VBox(6, Ui.label(t("savings.reserve.upcoming"), "h3"), next.node()));
        body.add(activity(ctx, reserve));
        return Ui.card(body.toArray(Node[]::new));
    }

    // ---------------------------------------------------------------- goals

    private static Node goals(AppContext ctx) {
        var p = ctx.profile();
        Button add = Ui.primary(t("savings.goal.new"));
        add.setOnAction(e -> editGoal(ctx, null));
        List<Node> body = new ArrayList<>();
        body.add(Ui.sectionHead(t("savings.goals.title"), add));
        body.add(Ui.wrapped(t("savings.goals.hint"), "muted"));
        List<Pot> goals = p.ledger().goals();
        if (goals.isEmpty()) {
            body.add(Ui.wrapped(t("savings.goals.none"),
                    "small"));
        }
        for (Pot g : goals) body.add(goalCard(ctx, g));
        return Ui.card(body.toArray(Node[]::new));
    }

    private static Node goalCard(AppContext ctx, Pot g) {
        var pr = Pots.progress(g, ctx.profile(), ctx.today());
        Label name = Ui.label(g.name(), "h3");
        HBox title = new HBox(8, name);
        title.setAlignment(Pos.CENTER_LEFT);
        if (g.archived()) title.getChildren().add(Ui.badge(t("savings.goal.badgeArchived"), "st-n"));
        else if (pr.reached()) title.getChildren().add(Ui.badge(t("savings.goal.badgeReached"), "st-ok"));
        else if (pr.neededMonthly() != null) {
            title.getChildren().add(pr.onTrack() ? Ui.badge(t("savings.goal.badgeOnTrack"), "st-ok") : Ui.badge(t("savings.goal.badgeBehind"), "st-warn"));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button in = new Button(t("savings.goal.contribute"));
        in.getStyleClass().add("small-button");
        in.setOnAction(e -> entry(ctx, g, true));
        Button out = new Button(t("savings.goal.withdraw"));
        out.getStyleClass().add("small-button");
        out.setOnAction(e -> entry(ctx, g, false));
        Button edit = Ui.link(t("common.edit"), () -> editGoal(ctx, g));
        Button archive = Ui.link(g.archived() ? t("savings.goal.unarchive") : t("savings.goal.archive"),
                () -> ctx.update(q -> SavingsOps.archiveGoal(q, g.id(), !g.archived())));
        title.getChildren().addAll(spacer, edit, archive, out, in);

        List<String> facts = new ArrayList<>();
        facts.add(Format.eur(pr.balance()) + (g.target() != null ? t("savings.goal.ofTarget", Format.eur(g.target()), pr.percent()) : ""));
        if (g.targetMonth() != null) facts.add(t("savings.goal.byMonth", Format.month(g.targetMonth()).toLowerCase()));
        facts.add(t("savings.goal.perMonth", Format.eur(g.monthly())));
        if (pr.neededMonthly() != null && !pr.reached()) facts.add(t("savings.goal.neededPerMonth", Format.eur(pr.neededMonthly())));
        VBox box = new VBox(6, title);
        if (g.target() != null) {
            ProgressBar bar = new ProgressBar(Math.min(1, Math.max(0, pr.balance().doubleValue() / g.target().doubleValue())));
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add("goal-bar");
            box.getChildren().add(bar);
        }
        box.getChildren().add(Ui.label(String.join(" · ", facts), "small"));
        box.getStyleClass().add("goal");
        if (g.archived()) box.getStyleClass().add("env-empty");
        return box;
    }

    /** Last entries, incomes and expenses of a pot. */
    private static Node activity(AppContext ctx, Pot pot) {
        record Line(LocalDate date, String what, BigDecimal amount, Runnable undo) {}
        var p = ctx.profile();
        List<Line> lines = new ArrayList<>();
        for (PotEntry e : p.ledger().entries()) {
            if (!e.potId().equals(pot.id()) || !pot.counts(e.date())) continue;
            Runnable undo = e.cycle() == null ? () -> ctx.update(q -> SavingsOps.removePotEntry(q, e.id())) : null;
            lines.add(new Line(e.date(), e.note().isEmpty() ? e.kind().label() : e.note(), e.amount(), undo));
        }
        for (Income i : p.incomes()) {
            if (pot.id().equals(i.potId()) && pot.counts(i.date())) {
                lines.add(new Line(i.date(), i.concept().isEmpty() ? i.kind().label() : i.concept(), i.amount(), null));
            }
        }
        for (Movement m : p.movements()) {
            boolean fromIt = pot.isReserve() ? m.fromReserve() : pot.id().equals(m.goalId());
            if (fromIt && pot.counts(m.date())) lines.add(new Line(m.date(), m.concept(), m.amount().negate(), null));
        }
        lines.sort(Comparator.comparing(Line::date).reversed());
        SimpleTable t = new SimpleTable(1, t("common.date"), t("savings.entries.movement"), ">" + t("common.amount"), "");
        lines.stream().limit(10).forEach(l -> {
            Node undo = l.undo() == null ? null : Ui.link(t("common.delete"), l.undo());
            if (undo != null) undo.getStyleClass().add("danger-link");
            t.add(SimpleTable.text(Format.dayMonth(l.date()), "num"), SimpleTable.text(l.what()),
                    SimpleTable.money(l.amount(), l.amount().signum() > 0 ? "inc" : "num"), undo);
        });
        if (t.isEmpty()) t.addMessage(t("savings.entries.none"));
        return new VBox(6, Ui.label(t("savings.entries.title"), "h3"), t.node());
    }

    // ---------------------------------------------------------------- dialogs

    private static void entry(AppContext ctx, Pot pot, boolean in) {
        TextField amount = Ui.amountField(in && !pot.isReserve() ? pot.monthly() : null);
        TextField note = new TextField();
        note.setPromptText(in ? t("savings.entry.notePrompt") : t("savings.entry.whatFor"));
        var date = Ui.datePicker(ctx.today());
        String name = pot.isReserve() ? t("savings.entry.theReserve") : "«" + pot.name() + "»";
        VBox form = new VBox(12, Ui.row(Ui.field(t("common.date"), date), Ui.field(t("common.amountEur"), amount)), Ui.row(Ui.field(t("savings.entry.note"), note)),
                Ui.wrapped(in ? t("savings.entry.putInHint", name)
                        : t("savings.entry.takeOutHint", name, name), "small"));
        record Spec(LocalDate date, BigDecimal amount, String note) {}
        EditDialog.<Spec>show(ctx.window(), (in ? t("savings.entry.putInTitle") : t("savings.entry.takeOutTitle")) + name, form, false, "", () -> {
            LocalDate d = date.getConverter().fromString(date.getEditor().getText());
            var v = Ui.amount(amount).filter(x -> x.signum() > 0);
            if (d == null) return EditDialog.Check.fail(t("common.errorPickDate"));
            if (v.isEmpty()) return EditDialog.Check.fail(t("savings.entry.errorAmount"));
            return EditDialog.Check.ok(new Spec(d, v.get(), note.getText()));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Spec>(Spec s)) {
                ctx.update(q -> SavingsOps.addPotEntry(q, pot.id(), s.date(), in ? s.amount() : s.amount().negate(),
                        in ? PotEntry.Kind.CONTRIBUTION : PotEntry.Kind.WITHDRAWAL, s.note()));
            }
        });
    }

    private static void adjust(AppContext ctx, Pot pot, BigDecimal balance) {
        TextField amount = Ui.amountField(balance);
        VBox form = new VBox(12, Ui.row(Ui.field(t("savings.adjust.actualBalance"), amount)),
                Ui.wrapped(t("savings.adjust.hint"), "small"));
        EditDialog.<BigDecimal>show(ctx.window(), t("savings.adjustBalance"), form, false, "", () -> {
            var v = Ui.amount(amount).filter(x -> x.signum() >= 0);
            return v.isPresent() ? EditDialog.Check.ok(v.get()) : EditDialog.Check.fail(t("savings.adjust.errorAmount"));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<BigDecimal>(BigDecimal v)) {
                ctx.update(q -> SavingsOps.adjustPotBalance(q, pot.id(), v, ctx.today()));
            }
        });
    }

    static void editGoal(AppContext ctx, Pot existing) {
        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText(t("savings.goal.namePrompt"));
        TextField target = Ui.amountField(existing == null ? null : existing.target());
        var month = MonthBox.create(existing == null ? null : existing.targetMonth(), ctx.cycle());
        TextField monthly = Ui.amountField(existing == null ? null : existing.monthly());
        TextField opening = Ui.amountField(existing == null ? null : existing.opening());
        VBox form = new VBox(12, Ui.row(Ui.field(t("common.name"), name)),
                Ui.row(Ui.field(t("savings.goal.targetOptional"), target), Ui.field(t("savings.goal.byWhenOptional"), month)),
                Ui.row(Ui.field(t("savings.goal.monthly"), monthly), Ui.field(existing == null ? t("savings.goal.alreadyHave") : t("savings.goal.openingBalance"), opening)),
                Ui.wrapped(t("savings.goal.formHint"), "small"));
        String title = existing == null ? t("savings.goal.new") : t("savings.goal.editTitle", existing.name());
        EditDialog.<Pot>show(ctx.window(), title, form, existing != null, t("savings.goal.delete"), () -> {
            String n = name.getText().strip();
            if (n.isEmpty()) return EditDialog.Check.fail(t("savings.goal.errorName"));
            Optional<BigDecimal> t = target.getText().isBlank() ? Optional.empty() : Ui.amount(target).filter(x -> x.signum() > 0);
            if (!target.getText().isBlank() && t.isEmpty()) return EditDialog.Check.fail(t("savings.goal.errorTarget"));
            var m = monthly.getText().isBlank() ? Optional.of(BigDecimal.ZERO) : Ui.amount(monthly).filter(x -> x.signum() >= 0);
            if (m.isEmpty()) return EditDialog.Check.fail(t("savings.goal.errorMonthly"));
            var o = opening.getText().isBlank() ? Optional.of(BigDecimal.ZERO) : Ui.amount(opening).filter(x -> x.signum() >= 0);
            if (o.isEmpty()) return EditDialog.Check.fail(t("savings.goal.errorOpening"));
            Pot pot = existing == null
                    ? Pot.newGoal(n, t.orElse(null), month.getValue(), m.get(), o.get(), ctx.today())
                    : new Pot(existing.id(), Pot.Kind.GOAL, n, t.orElse(null), month.getValue(), m.get(), o.get(),
                    existing.since(), existing.archived());
            return EditDialog.Check.ok(pot);
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Pot>(Pot value)) ctx.update(q -> SavingsOps.saveGoal(q, value));
            else if (existing != null && Ui.choose(ctx.window(), t("savings.goal.deleteTitle", existing.name()),
                    t("savings.goal.deleteQuestion"),
                    0, t("savings.goal.deleteConfirm")) == 0) {
                ctx.update(q -> SavingsOps.deleteGoal(q, existing.id()));
            }
        });
    }
}
