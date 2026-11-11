package cat.pessic.app.payment;

import static cat.pessic.core.common.I18n.t;
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
        SimpleTable t = new SimpleTable(1, t("common.month"), t("common.concept"), t("common.category"), ">" + t("common.amount"), "", "");
        for (CycleSummary.AnnualLine line : s.annualDue()) {
            var a = line.item();
            Button action = line.paid()
                    ? Ui.link(t("payment.plan.undo"), () -> ctx.update(p -> PaymentOps.unmarkPaid(p, line.scheduledId())))
                    : new Button(t("common.paidFromReserve"));
            if (!line.paid()) {
                action.getStyleClass().add("small-button");
                action.setOnAction(e -> ctx.update(p -> PaymentOps.markAnnualPaid(p, line, s.cycle(), ctx.today())));
            }
            t.addStyled(line.paid() ? "row-done" : null, SimpleTable.text(Format.monthName(a.month())),
                    Ui.link(a.concept(), () -> AnnualsView.edit(ctx, a)), Ui.categoryText(a.category(), "muted"),
                    SimpleTable.money(a.amount().negate()), action,
                    Ui.rowActions(() -> AnnualsView.edit(ctx, a), () -> AnnualsView.remove(ctx, a)));
        }
        return Ui.card(Ui.sectionHead(t("payment.plan.annualsTitle")), t.node(),
                Ui.wrapped(t("payment.plan.annualsHint"), "small"));
    }

    private static Region cyclePlan(AppContext ctx) {
        CycleSummary s = ctx.summary();
        var cfg = ctx.profile().config();
        String start = Format.dayMonth(s.cycle().start());
        BigDecimal afterSavings = cfg.income().subtract(cfg.savings());
        // this month, with the account balance known: the real forecast instead of the theoretical balance
        Cash.Forecast fc = s.cycle().contains(ctx.today()) ? Cash.forecast(ctx.profile(), ctx.today()) : null;
        boolean real = fc != null;

        SimpleTable t = new SimpleTable(1, t("payment.plan.day"), t("common.concept"), t("common.category"), ">" + t("common.amount"), real ? ">" + t("payment.plan.forecastBalance") : ">" + t("payment.plan.balance"), "", "");
        t.addStyled("row-income", SimpleTable.text(start, "num"), SimpleTable.text(t("common.income")),
                SimpleTable.text(t("common.income"), "muted"), SimpleTable.money(cfg.income(), "inc"),
                real ? null : SimpleTable.money(cfg.income()), null, null);
        var pay = s.expectedPaycheck();
        if (pay != null) {
            String name = t("payment.plan.extraPaycheck", Format.monthName(pay.month().getMonthValue()).toLowerCase());
            javafx.scene.Node action;
            if (pay.received()) {
                action = Ui.link(t("payment.plan.undo"), () -> ctx.update(p -> IncomeOps.removeIncome(p, pay.recorded().id())));
            } else {
                Button got = new Button(t("common.received"));
                got.getStyleClass().add("small-button");
                got.setOnAction(e -> IncomeDialog.paycheck(ctx, pay));
                action = got;
            }
            t.addStyled(pay.received() ? "row-done" : "row-income", SimpleTable.text("", "num"),
                    new HBox(6, SimpleTable.text(name), Ui.badge(pay.received() ? t("payment.plan.badgeReceived") : t("payment.plan.badgeExpected"), "st-n")),
                    SimpleTable.text(t("common.income"), "muted"),
                    SimpleTable.money(pay.received() ? pay.recorded().amount() : pay.amount(), "inc"), null, action, null);
        }
        if (cfg.savings().signum() > 0) {
            t.add(SimpleTable.text(start, "num"), SimpleTable.text(t("payment.plan.transferToSavings")),
                    SimpleTable.text(t("payment.plan.transfers"), "muted"), SimpleTable.money(cfg.savings().negate()),
                    real ? null : SimpleTable.money(afterSavings), null, null);
        }
        if (s.monthlyReserve().signum() > 0) {
            t.add(SimpleTable.text(start, "num"), SimpleTable.text(t("payment.plan.transferToReserve")),
                    SimpleTable.text(t("payment.plan.transfers"), "muted"), SimpleTable.money(s.monthlyReserve().negate()),
                    real ? null : SimpleTable.money(afterSavings.subtract(s.monthlyReserve())), null, null);
        }
        if (s.goalsMonthly().signum() > 0) {
            t.add(SimpleTable.text(start, "num"), SimpleTable.text(t("payment.plan.transferToGoals")),
                    SimpleTable.text(t("payment.plan.transfers"), "muted"), SimpleTable.money(s.goalsMonthly().negate()),
                    real ? null : SimpleTable.money(afterSavings.subtract(s.monthlyReserve()).subtract(s.goalsMonthly())),
                    null, null);
        }
        if (real) {
            t.addStyled("row-total", SimpleTable.text(Format.dayMonth(ctx.today()), "num"),
                    SimpleTable.text(t("common.inAccountTodayEstimated")), SimpleTable.text(t("payment.plan.payrollAccount"), "muted"), null,
                    SimpleTable.money(fc.today()), Ui.link(t("payment.plan.adjust"), () -> AccountUi.adjust(ctx)), null);
        }
        for (CycleSummary.PlanLine line : s.plan()) {
            FixedPayment f = line.payment().payment();
            HBox concept = new HBox(6, Ui.link(f.concept(), () -> edit(ctx, f)));
            if (f.extra()) concept.getChildren().add(Ui.badge(t("payment.plan.badgeOneOff"), "st-n"));
            if (line.overdue()) concept.getChildren().add(Ui.badge(t("common.overdue"), "st-warn"));
            BigDecimal after = real ? fc.balanceAfter(line.payment().scheduledId()) : line.balanceAfter();
            if (real && after != null && Money.isNegative(after)) {
                concept.getChildren().add(Ui.badge(t("payment.plan.badgeNoBalance"), "st-bad"));
            }
            Button action = line.paid()
                    ? Ui.link(t("payment.plan.undo"), () -> ctx.update(p -> PaymentOps.unmarkPaid(p, line.payment().scheduledId())))
                    : new Button(t("common.paid"));
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
            t.addMessage(t("payment.plan.emptyMonth"));
        }
        if (real) {
            BigDecimal variableLeft = s.left().max(BigDecimal.ZERO);
            t.addStyled("row-total", SimpleTable.text(Format.dayMonth(s.cycle().end()), "num"),
                    SimpleTable.text(t("common.beforeNextPayday")),
                    narrow(SimpleTable.text(t("payment.plan.ifYouSpendTheRest", Format.eur(variableLeft)), "muted")),
                    SimpleTable.money(variableLeft.negate()),
                    fc.beforePayday().signum() < 0 ? Ui.badge(Format.eur(fc.beforePayday()), "st-bad")
                            : SimpleTable.money(fc.beforePayday()), null, null);
        } else {
            BigDecimal end = s.plan().isEmpty() ? afterSavings.subtract(s.monthlyReserve()).subtract(s.goalsMonthly())
                    : s.plan().get(s.plan().size() - 1).balanceAfter();
            t.addStyled("row-total", null, SimpleTable.text(t("payment.plan.forVariableSpending")),
                    SimpleTable.text(t("payment.plan.variableLimit", Format.eur(s.variableBudget())), "muted"), null,
                    SimpleTable.money(end), null, null);
        }

        Button add = Ui.primary(t("payment.plan.add"));
        add.setOnAction(e -> edit(ctx, null));
        return Ui.card(Ui.sectionHead(t("payment.plan.title"), add), t.node(),
                Ui.wrapped(t("payment.plan.hint", (real
                        ? t("payment.plan.forecastHint")
                        : ctx.profile().ledger().account() != null
                        ? t("payment.plan.notCurrentMonthHint")
                        : t("payment.plan.noAccountHint"))), "small"));
    }

    /** Long category names wrap instead of squeezing the concept column. */
    private static javafx.scene.control.Label narrow(javafx.scene.control.Label l) {
        l.setWrapText(true);
        l.setPrefWidth(190);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Region allPayments(AppContext ctx) {
        SimpleTable t = new SimpleTable(1, t("payment.plan.day"), t("common.concept"), t("common.category"), t("common.period"), ">" + t("common.amount"), "");
        ctx.profile().config().plan().stream()
                .sorted(Comparator.comparingInt(FixedPayment::day).thenComparing(FixedPayment::concept))
                .forEach(f -> {
                    HBox concept = new HBox(6, Ui.link(f.concept(), () -> edit(ctx, f)));
                    if (f.extra()) concept.getChildren().add(Ui.badge(t("payment.plan.badgeOneOff"), "st-n"));
                    boolean ended = f.to() != null && f.to().isBefore(ctx.cycle());
                    t.addStyled(ended ? "row-done" : null, SimpleTable.text(String.valueOf(f.day()), "num"), concept,
                            Ui.categoryText(f.category(), "muted"), SimpleTable.text(period(f), "muted"),
                            SimpleTable.money(f.amount()), Ui.rowActions(() -> edit(ctx, f), () -> remove(ctx, f)));
                });
        if (t.isEmpty()) t.addMessage(t("payment.plan.noneYet"));
        return Ui.card(Ui.sectionHead(t("payment.plan.allTitle")),
                Ui.wrapped(t("payment.plan.allHint"), "small"),
                t.node());
    }

    private static String period(FixedPayment f) {
        if (f.from() == null && f.to() == null) return t("payment.plan.everyMonth");
        if (f.from() != null && f.from().equals(f.to())) return t("payment.plan.onlyIn", Format.month(f.from()));
        return (f.from() != null ? t("payment.plan.fromMonth", Format.month(f.from())) : "")
                + (f.from() != null && f.to() != null ? " " : "")
                + (f.to() != null ? t("payment.plan.toMonth", Format.month(f.to())) : "");
    }

    static void edit(AppContext ctx, FixedPayment existing) {
        TextField concept = new TextField(existing == null ? "" : existing.concept());
        concept.setPromptText(t("payment.plan.conceptPrompt"));
        ComboBox<Category> category = Ui.categoryBox(existing == null ? Category.HOUSING : existing.category());
        TextField amount = Ui.amountField(existing == null ? null : existing.amount());
        Spinner<Integer> day = new Spinner<>(1, 31, existing == null ? ctx.profile().config().payday() : existing.day());
        day.setEditable(true);
        YearMonth around = ctx.cycle();
        ComboBox<YearMonth> from = MonthBox.create(existing == null ? null : existing.from(), around);
        ComboBox<YearMonth> to = MonthBox.create(existing == null ? null : existing.to(), around);
        CheckBox extra = new CheckBox(t("payment.plan.oneOffCheck"));
        extra.setSelected(existing != null && existing.extra());
        extra.setWrapText(true);

        VBox form = new VBox(12,
                Ui.row(Ui.field(t("common.concept"), concept)),
                Ui.row(Ui.field(t("common.category"), category), Ui.field(t("common.amountEur"), amount), Ui.field(t("payment.plan.dayOfMonth"), day)),
                Ui.categoryHelp(category),
                Ui.row(Ui.field(t("payment.plan.fromOptional"), from), Ui.field(t("payment.plan.toOptional"), to)),
                extra,
                Ui.wrapped(t("payment.plan.dayHint"), "small"));

        String title = existing == null ? t("payment.plan.newTitle") : t("payment.plan.editTitle");
        EditDialog.<FixedPayment>show(ctx.window(), title, form, existing != null, t("common.deletePayment"), () -> {
            String c = concept.getText().strip();
            if (c.isEmpty()) return EditDialog.Check.fail(t("common.errorConcept"));
            var imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            if (imp.isEmpty()) return EditDialog.Check.fail(t("payment.plan.errorAmount"));
            int d;
            try {
                d = Integer.parseInt(day.getEditor().getText().strip());
            } catch (NumberFormatException e) {
                d = day.getValue();
            }
            if (d < 1 || d > 31) return EditDialog.Check.fail(t("payment.plan.errorDay"));
            if (from.getValue() != null && to.getValue() != null && to.getValue().isBefore(from.getValue()))
                return EditDialog.Check.fail(t("payment.plan.errorRange"));
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

        String deleteText = t("payment.plan.endOrDeleteHint");
        if (canStop) {
            String text = t("payment.plan.endHint", Format.month(stopFrom), (paidThisCycle ? t("payment.plan.endAlreadyPaid") : ""), deleteText);
            int choice = Ui.choose(ctx.window(), t("payment.plan.endTitle", f.concept()), text, 1,
                    t("payment.plan.endFrom", Format.month(stopFrom)), t("payment.plan.deleteForGood"));
            if (choice == 0) ctx.update(p -> PaymentOps.endFixed(p, f.id(), lastCycle));
            else if (choice == 1) ctx.update(p -> PaymentOps.deleteFixed(p, f.id()));
        } else {
            int choice = Ui.choose(ctx.window(), t("payment.plan.deleteTitle", f.concept()), deleteText, 0, t("payment.plan.deleteForGood"));
            if (choice == 0) ctx.update(p -> PaymentOps.deleteFixed(p, f.id()));
        }
    }
}
