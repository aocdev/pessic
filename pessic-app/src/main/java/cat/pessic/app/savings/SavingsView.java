package cat.pessic.app.savings;

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
            Button save = Ui.primary("Desa");
            Runnable submit = () -> {
                var v = Ui.amount(amount).filter(x -> x.signum() >= 0);
                if (v.isEmpty()) { Ui.showError(error, "Escriu un import, per exemple 150,00 (o 0 si encara no hi tens res)."); return; }
                ctx.update(q -> SavingsOps.setReserveBalance(q, v.get(), ctx.today()));
            };
            save.setOnAction(e -> submit.run());
            amount.setOnAction(e -> submit.run());
            HBox ask = new HBox(10, Ui.label("Tinc", "muted"), amount, Ui.label("€ a la reserva avui", "muted"), save);
            ask.setAlignment(Pos.CENTER_LEFT);
            body.add(Ui.sectionHead("Reserva d'anuals"));
            body.add(Ui.wrapped("Quant tens ara al compte (o a la part del compte) on guardes els diners dels pagaments "
                    + "anuals? A partir d'aquí Pessic en seguirà el saldo: hi sumarà el que hi posis cada mes i hi restarà "
                    + "els anuals que paguis.", "muted"));
            body.add(ask);
            body.add(error);
            if (monthly.signum() > 0) {
                body.add(Ui.wrapped("Amb els teus anuals, cada mes hi hauries de posar " + Format.eur(monthly) + ".", "small"));
            }
            return Ui.card(body.toArray(Node[]::new));
        }

        BigDecimal balance = Pots.balance(reserve, p);
        Button in = new Button("Hi poso diners");
        in.setOnAction(e -> entry(ctx, reserve, true));
        Button out = new Button("En trec");
        out.setOnAction(e -> entry(ctx, reserve, false));
        Button adjust = Ui.link("Ajusta el saldo", () -> adjust(ctx, reserve, balance));
        body.add(Ui.sectionHead("Reserva d'anuals", adjust, out, in));
        Label big = Ui.label(Format.eur(balance), "hero-secondary");
        if (balance.signum() < 0) big.getStyleClass().add("text-bad");
        body.add(new VBox(2, Ui.label("Saldo avui", "muted"), big,
                Ui.label("Hi poses " + Format.eur(monthly) + " al mes · anuals " + Format.eur(monthly.multiply(BigDecimal.valueOf(12)))
                        + " l'any · des del " + Format.date(reserve.since()), "small")));

        Optional<Pots.Shortfall> sf = Pots.reserveShortfall(p, ctx.today());
        if (sf.isPresent()) {
            body.add(Ui.wrapped("Al " + Format.month(sf.get().cycle()).toLowerCase() + " toca pagar " + Format.eur(sf.get().due())
                    + " d'anuals i hi faltaran " + Format.eur(sf.get().missing()) + ", encara que cada mes hi posis el que toca. "
                    + "Posa-hi més diners abans (per exemple una paga extra o el que sobri d'un mes).", "text-bad"));
        } else if (monthly.signum() > 0) {
            body.add(Ui.wrapped("Si cada mes hi poses el que toca, n'hi ha prou per a tots els anuals dels pròxims 12 mesos.",
                    "inc"));
        }

        // coming annual payments
        YearMonth now = calc.cycleContaining(ctx.today()).key();
        SimpleTable next = new SimpleTable(1, "Mes", "Pagament", ">Import");
        for (int i = 0; i < 12; i++) {
            YearMonth k = now.plusMonths(i);
            for (var line : calc.annualDue(k, p.movements())) {
                if (line.paid()) continue;
                AnnualItem a = line.item();
                next.add(SimpleTable.text(Format.month(k)), SimpleTable.text(a.concept()), SimpleTable.money(a.amount()));
            }
        }
        if (!next.isEmpty()) body.add(new VBox(6, Ui.label("Pròxims anuals", "h3"), next.node()));
        body.add(activity(ctx, reserve));
        return Ui.card(body.toArray(Node[]::new));
    }

    // ---------------------------------------------------------------- goals

    private static Node goals(AppContext ctx) {
        var p = ctx.profile();
        Button add = Ui.primary("Nova guardiola");
        add.setOnAction(e -> editGoal(ctx, null));
        List<Node> body = new ArrayList<>();
        body.add(Ui.sectionHead("Guardioles", add));
        body.add(Ui.wrapped("Diners que aparto per a una cosa concreta: un viatge, un cotxe, un fons d'emergència… "
                + "El que hi poses cada mes es descompta de l'estalvi previst, com la reserva.", "muted"));
        List<Pot> goals = p.ledger().goals();
        if (goals.isEmpty()) {
            body.add(Ui.wrapped("Encara no en tens cap. Un bon començament és un fons d'emergència de tres mesos de despeses.",
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
        if (g.archived()) title.getChildren().add(Ui.badge("arxivada", "st-n"));
        else if (pr.reached()) title.getChildren().add(Ui.badge("aconseguida!", "st-ok"));
        else if (pr.neededMonthly() != null) {
            title.getChildren().add(pr.onTrack() ? Ui.badge("al dia", "st-ok") : Ui.badge("vas curt", "st-warn"));
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button in = new Button("Aporta");
        in.getStyleClass().add("small-button");
        in.setOnAction(e -> entry(ctx, g, true));
        Button out = new Button("Retira");
        out.getStyleClass().add("small-button");
        out.setOnAction(e -> entry(ctx, g, false));
        Button edit = Ui.link("Edita", () -> editGoal(ctx, g));
        Button archive = Ui.link(g.archived() ? "Recupera" : "Arxiva",
                () -> ctx.update(q -> SavingsOps.archiveGoal(q, g.id(), !g.archived())));
        title.getChildren().addAll(spacer, edit, archive, out, in);

        List<String> facts = new ArrayList<>();
        facts.add(Format.eur(pr.balance()) + (g.target() != null ? " de " + Format.eur(g.target()) + " (" + pr.percent() + " %)" : ""));
        if (g.targetMonth() != null) facts.add("per al " + Format.month(g.targetMonth()).toLowerCase());
        facts.add(Format.eur(g.monthly()) + " al mes");
        if (pr.neededMonthly() != null && !pr.reached()) facts.add("cal " + Format.eur(pr.neededMonthly()) + " al mes per arribar-hi");
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
            lines.add(new Line(e.date(), e.note().isEmpty() ? e.kind().label : e.note(), e.amount(), undo));
        }
        for (Income i : p.incomes()) {
            if (pot.id().equals(i.potId()) && pot.counts(i.date())) {
                lines.add(new Line(i.date(), i.concept().isEmpty() ? i.kind().label : i.concept(), i.amount(), null));
            }
        }
        for (Movement m : p.movements()) {
            boolean fromIt = pot.isReserve() ? m.fromReserve() : pot.id().equals(m.goalId());
            if (fromIt && pot.counts(m.date())) lines.add(new Line(m.date(), m.concept(), m.amount().negate(), null));
        }
        lines.sort(Comparator.comparing(Line::date).reversed());
        SimpleTable t = new SimpleTable(1, "Data", "Moviment", ">Import", "");
        lines.stream().limit(10).forEach(l -> {
            Node undo = l.undo() == null ? null : Ui.link("Esborra", l.undo());
            if (undo != null) undo.getStyleClass().add("danger-link");
            t.add(SimpleTable.text(Format.dayMonth(l.date()), "num"), SimpleTable.text(l.what()),
                    SimpleTable.money(l.amount(), l.amount().signum() > 0 ? "inc" : "num"), undo);
        });
        if (t.isEmpty()) t.addMessage("Encara no hi ha cap moviment. Les aportacions es fan en tancar cada mes.");
        return new VBox(6, Ui.label("Últims moviments", "h3"), t.node());
    }

    // ---------------------------------------------------------------- dialogs

    private static void entry(AppContext ctx, Pot pot, boolean in) {
        TextField amount = Ui.amountField(in && !pot.isReserve() ? pot.monthly() : null);
        TextField note = new TextField();
        note.setPromptText(in ? "Paga extra, sobrant…" : "Per a què?");
        var date = Ui.datePicker(ctx.today());
        String name = pot.isReserve() ? "la reserva" : "«" + pot.name() + "»";
        VBox form = new VBox(12, Ui.row(Ui.field("Data", date), Ui.field("Import (€)", amount)), Ui.row(Ui.field("Nota", note)),
                Ui.wrapped(in ? "Diners que passes a " + name + " a més de l'aportació mensual (que es fa en tancar el mes)."
                        : "Diners que treus de " + name + " i tornen al compte del dia a dia. Si és per pagar una cosa, "
                        + "millor apunta la despesa a «Moviments» i tria «Es paga amb " + name + "».", "small"));
        record Spec(LocalDate date, BigDecimal amount, String note) {}
        EditDialog.<Spec>show(ctx.window(), (in ? "Posa diners a " : "Treu diners de ") + name, form, false, "", () -> {
            LocalDate d = date.getConverter().fromString(date.getEditor().getText());
            var v = Ui.amount(amount).filter(x -> x.signum() > 0);
            if (d == null) return EditDialog.Check.fail("Tria una data.");
            if (v.isEmpty()) return EditDialog.Check.fail("Escriu un import més gran que zero.");
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
        VBox form = new VBox(12, Ui.row(Ui.field("Saldo real avui (€)", amount)),
                Ui.wrapped("Escriu el que hi ha de veritat al compte. Pessic hi afegirà un ajust per la diferència.", "small"));
        EditDialog.<BigDecimal>show(ctx.window(), "Ajusta el saldo", form, false, "", () -> {
            var v = Ui.amount(amount).filter(x -> x.signum() >= 0);
            return v.isPresent() ? EditDialog.Check.ok(v.get()) : EditDialog.Check.fail("Escriu un import.");
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<BigDecimal>(BigDecimal v)) {
                ctx.update(q -> SavingsOps.adjustPotBalance(q, pot.id(), v, ctx.today()));
            }
        });
    }

    static void editGoal(AppContext ctx, Pot existing) {
        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText("Viatge a Itàlia, fons d'emergència…");
        TextField target = Ui.amountField(existing == null ? null : existing.target());
        var month = MonthBox.create(existing == null ? null : existing.targetMonth(), ctx.cycle());
        TextField monthly = Ui.amountField(existing == null ? null : existing.monthly());
        TextField opening = Ui.amountField(existing == null ? null : existing.opening());
        VBox form = new VBox(12, Ui.row(Ui.field("Nom", name)),
                Ui.row(Ui.field("Objectiu (€, opcional)", target), Ui.field("Per a quan (opcional)", month)),
                Ui.row(Ui.field("Hi poso cada mes (€)", monthly), Ui.field(existing == null ? "Ja hi tinc (€)" : "Saldo inicial (€)", opening)),
                Ui.wrapped("Amb objectiu i data, Pessic et diu quant cal posar-hi cada mes per arribar-hi.", "small"));
        String title = existing == null ? "Nova guardiola" : "Edita «" + existing.name() + "»";
        EditDialog.<Pot>show(ctx.window(), title, form, existing != null, "Esborra la guardiola", () -> {
            String n = name.getText().strip();
            if (n.isEmpty()) return EditDialog.Check.fail("Posa-li un nom.");
            Optional<BigDecimal> t = target.getText().isBlank() ? Optional.empty() : Ui.amount(target).filter(x -> x.signum() > 0);
            if (!target.getText().isBlank() && t.isEmpty()) return EditDialog.Check.fail("L'objectiu no s'entén.");
            var m = monthly.getText().isBlank() ? Optional.of(BigDecimal.ZERO) : Ui.amount(monthly).filter(x -> x.signum() >= 0);
            if (m.isEmpty()) return EditDialog.Check.fail("L'import mensual no s'entén.");
            var o = opening.getText().isBlank() ? Optional.of(BigDecimal.ZERO) : Ui.amount(opening).filter(x -> x.signum() >= 0);
            if (o.isEmpty()) return EditDialog.Check.fail("El que ja hi tens no s'entén.");
            Pot pot = existing == null
                    ? Pot.newGoal(n, t.orElse(null), month.getValue(), m.get(), o.get(), ctx.today())
                    : new Pot(existing.id(), Pot.Kind.GOAL, n, t.orElse(null), month.getValue(), m.get(), o.get(),
                    existing.since(), existing.archived());
            return EditDialog.Check.ok(pot);
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Pot>(Pot value)) ctx.update(q -> SavingsOps.saveGoal(q, value));
            else if (existing != null && Ui.choose(ctx.window(), "Esborra «" + existing.name() + "»",
                    "Se n'esborraran les aportacions. Les despeses pagades amb aquesta guardiola es queden a «Moviments» però "
                            + "passaran a comptar al mes en què es van fer, i els ingressos que hi havies posat comptaran com a estalvi.",
                    0, "Esborra-la") == 0) {
                ctx.update(q -> SavingsOps.deleteGoal(q, existing.id()));
            }
        });
    }
}
