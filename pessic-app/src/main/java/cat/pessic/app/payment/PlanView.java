package cat.pessic.app.payment;

import cat.pessic.app.AppContext;
import cat.pessic.app.account.AccountUi;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.MonthBox;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.app.income.IncomeDialog;
import cat.pessic.core.account.Cash;
import cat.pessic.core.budget.Category;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.common.Ids;
import cat.pessic.core.common.Money;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.payment.FixedPayment;
import cat.pessic.core.payment.PaymentOps;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Pagaments fixos": this cycle's payment plan (mark as paid) and the list of all fixed payments. */
public final class PlanView {
    private PlanView() {}

    public static Region build(AppContext ctx) {
        Region annual = annualDue(ctx);
        return annual == null ? Ui.page(cyclePlan(ctx), allPayments(ctx))
                : Ui.page(cyclePlan(ctx), annual, allPayments(ctx));
    }

    /**
     * Annual payments that fall in this cycle. They are paid from the reserve, so they don't change
     * the running balance of the month; the button records them as reserve movements.
     */
    private static Region annualDue(AppContext ctx) {
        CycleSummary s = ctx.summary();
        if (s.annualDue().isEmpty()) return null;
        SimpleTable t = new SimpleTable(1, "Mes", "Concepte", "Categoria", ">Import", "", "");
        for (CycleSummary.AnnualLine line : s.annualDue()) {
            var a = line.item();
            Button action = line.paid()
                    ? Ui.link("Desfés", () -> ctx.update(p -> PaymentOps.unmarkPaid(p, line.scheduledId())))
                    : new Button("Pagat amb la reserva");
            if (!line.paid()) {
                action.getStyleClass().add("small-button");
                action.setOnAction(e -> ctx.update(p -> PaymentOps.markAnnualPaid(p, line, s.cycle(), ctx.today())));
            }
            t.addStyled(line.paid() ? "row-done" : null, SimpleTable.text(Format.monthName(a.month())),
                    Ui.link(a.concept(), () -> AnnualsView.edit(ctx, a)), Ui.categoryText(a.category(), "muted"),
                    SimpleTable.money(a.amount().negate()), action,
                    Ui.rowActions(() -> AnnualsView.edit(ctx, a), () -> AnnualsView.remove(ctx, a)));
        }
        return Ui.card(Ui.sectionHead("Pagaments anuals d'aquest mes"), t.node(),
                Ui.wrapped("Surten del compte de la reserva d'anuals, no dels diners del mes: per això no canvien el saldo "
                        + "ni els límits. Marca'ls quan es cobrin.", "small"));
    }

    private static Region cyclePlan(AppContext ctx) {
        CycleSummary s = ctx.summary();
        var cfg = ctx.profile().config();
        String start = Format.dayMonth(s.cycle().start());
        BigDecimal afterSavings = cfg.income().subtract(cfg.savings());
        // this month, with the account balance known: the real forecast instead of the theoretical balance
        Cash.Forecast fc = s.cycle().contains(ctx.today()) ? Cash.forecast(ctx.profile(), ctx.today()) : null;
        boolean real = fc != null;

        SimpleTable t = new SimpleTable(1, "Dia", "Concepte", "Categoria", ">Import", real ? ">Saldo previst" : ">Saldo", "", "");
        t.addStyled("row-income", SimpleTable.text(start, "num"), SimpleTable.text("Ingressos"),
                SimpleTable.text("Ingressos", "muted"), SimpleTable.money(cfg.income(), "inc"),
                real ? null : SimpleTable.money(cfg.income()), null, null);
        var pay = s.expectedPaycheck();
        if (pay != null) {
            String name = "Paga extra de " + Format.monthName(pay.month().getMonthValue()).toLowerCase();
            javafx.scene.Node action;
            if (pay.received()) {
                action = Ui.link("Desfés", () -> ctx.update(p -> IncomeOps.removeIncome(p, pay.recorded().id())));
            } else {
                Button got = new Button("Rebuda");
                got.getStyleClass().add("small-button");
                got.setOnAction(e -> IncomeDialog.paycheck(ctx, pay));
                action = got;
            }
            t.addStyled(pay.received() ? "row-done" : "row-income", SimpleTable.text("", "num"),
                    new HBox(6, SimpleTable.text(name), Ui.badge(pay.received() ? "rebuda" : "prevista", "st-n")),
                    SimpleTable.text("Ingressos", "muted"),
                    SimpleTable.money(pay.received() ? pay.recorded().amount() : pay.amount(), "inc"), null, action, null);
        }
        if (cfg.savings().signum() > 0) {
            t.add(SimpleTable.text(start, "num"), SimpleTable.text("Transferència automàtica a estalvi"),
                    SimpleTable.text("Traspassos", "muted"), SimpleTable.money(cfg.savings().negate()),
                    real ? null : SimpleTable.money(afterSavings), null, null);
        }
        if (s.monthlyReserve().signum() > 0) {
            t.add(SimpleTable.text(start, "num"), SimpleTable.text("Transferència a la reserva d'anuals"),
                    SimpleTable.text("Traspassos", "muted"), SimpleTable.money(s.monthlyReserve().negate()),
                    real ? null : SimpleTable.money(afterSavings.subtract(s.monthlyReserve())), null, null);
        }
        if (s.goalsMonthly().signum() > 0) {
            t.add(SimpleTable.text(start, "num"), SimpleTable.text("Transferència a les guardioles"),
                    SimpleTable.text("Traspassos", "muted"), SimpleTable.money(s.goalsMonthly().negate()),
                    real ? null : SimpleTable.money(afterSavings.subtract(s.monthlyReserve()).subtract(s.goalsMonthly())),
                    null, null);
        }
        if (real) {
            t.addStyled("row-total", SimpleTable.text(Format.dayMonth(ctx.today()), "num"),
                    SimpleTable.text("Al compte avui (estimat)"), SimpleTable.text("Compte de la nòmina", "muted"), null,
                    SimpleTable.money(fc.today()), Ui.link("Ajusta", () -> AccountUi.adjust(ctx)), null);
        }
        for (CycleSummary.PlanLine line : s.plan()) {
            FixedPayment f = line.payment().payment();
            HBox concept = new HBox(6, Ui.link(f.concept(), () -> edit(ctx, f)));
            if (f.extra()) concept.getChildren().add(Ui.badge("puntual", "st-n"));
            if (line.overdue()) concept.getChildren().add(Ui.badge("vençut?", "st-warn"));
            BigDecimal after = real ? fc.balanceAfter(line.payment().scheduledId()) : line.balanceAfter();
            if (real && after != null && Money.isNegative(after)) {
                concept.getChildren().add(Ui.badge("sense saldo", "st-bad"));
            }
            Button action = line.paid()
                    ? Ui.link("Desfés", () -> ctx.update(p -> PaymentOps.unmarkPaid(p, line.payment().scheduledId())))
                    : new Button("Pagat");
            if (!line.paid()) {
                action.getStyleClass().add("small-button");
                action.setOnAction(e -> ctx.update(p -> PaymentOps.markPaid(p, line.payment(), s.cycle(), ctx.today())));
            }
            t.addStyled(line.paid() ? "row-done" : null, SimpleTable.text(Format.dayMonth(line.payment().date()), "num"),
                    concept, narrow(Ui.categoryText(f.category(), "muted")), SimpleTable.money(f.amount().negate()),
                    after == null ? null : SimpleTable.money(after), action,
                    Ui.rowActions(() -> edit(ctx, f), () -> remove(ctx, f)));
        }
        if (s.plan().isEmpty()) {
            t.addMessage("Encara no hi ha pagaments fixos aquest mes. Afegeix-hi el lloguer o la hipoteca, la llum, "
                    + "l'aigua, el telèfon, el gimnàs…");
        }
        if (real) {
            BigDecimal variableLeft = s.left().max(BigDecimal.ZERO);
            t.addStyled("row-total", SimpleTable.text(Format.dayMonth(s.cycle().end()), "num"),
                    SimpleTable.text("Abans de tornar a cobrar"),
                    narrow(SimpleTable.text("si gastes els " + Format.eur(variableLeft) + " que queden del pressupost", "muted")),
                    SimpleTable.money(variableLeft.negate()),
                    fc.beforePayday().signum() < 0 ? Ui.badge(Format.eur(fc.beforePayday()), "st-bad")
                            : SimpleTable.money(fc.beforePayday()), null, null);
        } else {
            BigDecimal end = s.plan().isEmpty() ? afterSavings.subtract(s.monthlyReserve()).subtract(s.goalsMonthly())
                    : s.plan().get(s.plan().size() - 1).balanceAfter();
            t.addStyled("row-total", null, SimpleTable.text("Per a despesa variable i coixí"),
                    SimpleTable.text("límit variable " + Format.eur(s.variableBudget()), "muted"), null,
                    SimpleTable.money(end), null, null);
        }

        Button add = Ui.primary("Afegeix un pagament fix");
        add.setOnAction(e -> edit(ctx, null));
        return Ui.card(Ui.sectionHead("Pla de pagaments del mes", add), t.node(),
                Ui.wrapped((real
                        ? "El saldo previst parteix del que hi ha avui al compte i hi resta els pagaments que falten i el que "
                        + "et queda per gastar del pressupost. Els que ja has pagat ja són fora del saldo. "
                        : ctx.profile().ledger().account() != null
                        ? "Aquest no és el mes actual: el saldo és teòric, com si el compte comencés amb la nòmina. "
                        : "El saldo és el que quedaria al compte només amb els pagaments fixos (digues quant tens al compte a "
                        + "«Resum» i serà el saldo real). ")
                        + "Prem «Pagat» quan surti del compte. Si dones de baixa un pagament, prem «Esborra»: "
                        + "pots deixar de comptar-lo a partir d'un mes sense tocar els mesos anteriors.", "small"));
    }

    /** Long category names wrap instead of squeezing the concept column. */
    private static javafx.scene.control.Label narrow(javafx.scene.control.Label l) {
        l.setWrapText(true);
        l.setPrefWidth(190);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Region allPayments(AppContext ctx) {
        SimpleTable t = new SimpleTable(1, "Dia", "Concepte", "Categoria", "Període", ">Import", "");
        ctx.profile().config().plan().stream()
                .sorted(Comparator.comparingInt(FixedPayment::day).thenComparing(FixedPayment::concept))
                .forEach(f -> {
                    HBox concept = new HBox(6, Ui.link(f.concept(), () -> edit(ctx, f)));
                    if (f.extra()) concept.getChildren().add(Ui.badge("puntual", "st-n"));
                    boolean ended = f.to() != null && f.to().isBefore(ctx.cycle());
                    t.addStyled(ended ? "row-done" : null, SimpleTable.text(String.valueOf(f.day()), "num"), concept,
                            Ui.categoryText(f.category(), "muted"), SimpleTable.text(period(f), "muted"),
                            SimpleTable.money(f.amount()), Ui.rowActions(() -> edit(ctx, f), () -> remove(ctx, f)));
                });
        if (t.isEmpty()) t.addMessage("Cap pagament fix encara.");
        return Ui.card(Ui.sectionHead("Tots els pagaments fixos"),
                Ui.wrapped("Inclou els que encara no han començat o ja s'han acabat, que no surten al pla d'aquest mes.", "small"),
                t.node());
    }

    private static String period(FixedPayment f) {
        if (f.from() == null && f.to() == null) return "cada mes";
        if (f.from() != null && f.from().equals(f.to())) return "només " + Format.month(f.from());
        return (f.from() != null ? "des de " + Format.month(f.from()) : "")
                + (f.from() != null && f.to() != null ? " " : "")
                + (f.to() != null ? "fins a " + Format.month(f.to()) : "");
    }

    static void edit(AppContext ctx, FixedPayment existing) {
        TextField concept = new TextField(existing == null ? "" : existing.concept());
        concept.setPromptText("Lloguer, llum, gimnàs…");
        ComboBox<Category> category = Ui.categoryBox(existing == null ? Category.HOUSING : existing.category());
        TextField amount = Ui.amountField(existing == null ? null : existing.amount());
        Spinner<Integer> day = new Spinner<>(1, 31, existing == null ? ctx.profile().config().payday() : existing.day());
        day.setEditable(true);
        YearMonth around = ctx.cycle();
        ComboBox<YearMonth> from = MonthBox.create(existing == null ? null : existing.from(), around);
        ComboBox<YearMonth> to = MonthBox.create(existing == null ? null : existing.to(), around);
        CheckBox extra = new CheckBox("És un pagament puntual: suma'l al límit de la categoria aquells mesos");
        extra.setSelected(existing != null && existing.extra());
        extra.setWrapText(true);

        VBox form = new VBox(12,
                Ui.row(Ui.field("Concepte", concept)),
                Ui.row(Ui.field("Categoria", category), Ui.field("Import (€)", amount), Ui.field("Dia del mes", day)),
                Ui.categoryHelp(category),
                Ui.row(Ui.field("Des de (opcional)", from), Ui.field("Fins a (opcional)", to)),
                extra,
                Ui.wrapped("Si el dia és anterior al dia que cobres, el pagament cau al segon mes del cicle. "
                        + "Els dies que no existeixen (31 de febrer) passen a l'últim dia del mes.", "small"));

        String title = existing == null ? "Nou pagament fix" : "Edita el pagament fix";
        EditDialog.<FixedPayment>show(ctx.window(), title, form, existing != null, "Esborra aquest pagament", () -> {
            String c = concept.getText().strip();
            if (c.isEmpty()) return EditDialog.Check.fail("Escriu un concepte.");
            var imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            if (imp.isEmpty()) return EditDialog.Check.fail("Escriu un import més gran que zero.");
            int d;
            try {
                d = Integer.parseInt(day.getEditor().getText().strip());
            } catch (NumberFormatException e) {
                d = day.getValue();
            }
            if (d < 1 || d > 31) return EditDialog.Check.fail("El dia ha de ser entre 1 i 31.");
            if (from.getValue() != null && to.getValue() != null && to.getValue().isBefore(from.getValue()))
                return EditDialog.Check.fail("«Fins a» no pot ser abans de «Des de».");
            return EditDialog.Check.ok(new FixedPayment(existing == null ? Ids.next("f") : existing.id(), d, c,
                    category.getValue(), imp.get(), from.getValue(), to.getValue(), extra.isSelected()));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<FixedPayment>(FixedPayment value)) ctx.update(p -> PaymentOps.saveFixed(p, value));
            else if (existing != null) remove(ctx, existing);
        });
    }

    /**
     * Removing a fixed payment usually means it was cancelled: offer to stop counting it from the
     * viewed month on (earlier months unchanged), or to delete it everywhere if it was a mistake.
     */
    static void remove(AppContext ctx, FixedPayment f) {
        YearMonth viewed = ctx.cycle();
        boolean paidThisCycle = ctx.profile().movements().stream()
                .anyMatch(m -> (f.id() + "@" + viewed).equals(m.scheduledId()));
        YearMonth stopFrom = paidThisCycle ? viewed.plusMonths(1) : viewed;
        YearMonth lastCycle = stopFrom.minusMonths(1);
        boolean canStop = (f.from() == null || !f.from().isAfter(lastCycle))
                && (f.to() == null || f.to().isAfter(lastCycle));

        String deleteText = "Si el vas afegir per error, esborra'l del tot: desapareixerà també dels mesos anteriors. "
                + "Els pagaments que ja havies marcat com a pagats es queden a «Moviments».";
        if (canStop) {
            String text = "Si l'has donat de baixa o ja no el pagues, Pessic el deixarà de comptar a partir de "
                    + Format.month(stopFrom) + " i els mesos anteriors es quedaran com estaven."
                    + (paidThisCycle ? " Aquest mes ja està marcat com a pagat, per això deixa de comptar el mes següent." : "")
                    + "\n\n" + deleteText;
            int choice = Ui.choose(ctx.window(), "Treu «" + f.concept() + "»", text, 1,
                    "Ja no el pago des de " + Format.month(stopFrom), "Esborra'l del tot");
            if (choice == 0) ctx.update(p -> PaymentOps.endFixed(p, f.id(), lastCycle));
            else if (choice == 1) ctx.update(p -> PaymentOps.deleteFixed(p, f.id()));
        } else {
            int choice = Ui.choose(ctx.window(), "Esborra «" + f.concept() + "»", deleteText, 0, "Esborra'l del tot");
            if (choice == 0) ctx.update(p -> PaymentOps.deleteFixed(p, f.id()));
        }
    }
}
