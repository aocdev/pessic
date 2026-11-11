package cat.pessic.app.dashboard;

import static cat.pessic.core.common.I18n.plural;
import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.account.AccountUi;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.app.expense.ExpenseForm;
import cat.pessic.app.income.IncomeDialog;
import cat.pessic.app.monthclose.MonthCloseDialog;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.income.Income;
import cat.pessic.core.ine.IneReference;
import cat.pessic.core.monthclose.MonthClose;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.PaymentOps;
import cat.pessic.core.savings.Pots;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Resum": key figures, quick expense entry, envelopes, weekly limit and the month's rules. */
public final class DashboardView {
    private DashboardView() {}

    /** The user hid the "close the month" banner for this session. */
    private static boolean closeBannerHidden;
    /** Payments due within this many days are listed in "Propers pagaments". */
    static final int DUE_SOON_DAYS = 3;

    public static Region build(AppContext ctx) {
        CycleSummary s = ctx.summary();
        List<Node> parts = new ArrayList<>();
        Node close = closeBanner(ctx);
        if (close != null) parts.add(close);
        Node due = dueSoon(ctx, s);
        if (due != null) parts.add(due);
        parts.add(AccountUi.card(ctx));
        parts.addAll(List.of(kpis(ctx, s), hero(s), ExpenseForm.build(ctx), envelopes(s, ctx), weeks(s), rules(ctx, s)));
        return Ui.page(parts.toArray(Node[]::new));
    }

    /** "L'octubre ha acabat: tanca'l" for the oldest ended cycle not closed yet. */
    private static Node closeBanner(AppContext ctx) {
        if (closeBannerHidden) return null;
        var pending = MonthClose.pending(ctx.profile(), ctx.today());
        if (pending.isEmpty()) return null;
        YearMonth first = pending.get(0);
        String text = pending.size() == 1
                ? t("dashboard.close.oneEnded", Format.month(first))
                : t("dashboard.close.severalEnded", pending.size(), Format.month(first).toLowerCase());
        Button close = Ui.primary(t("dashboard.close.button", Format.month(first).toLowerCase()));
        close.setOnAction(e -> MonthCloseDialog.show(ctx, first));
        Button later = Ui.link(t("dashboard.close.later"), () -> {
            closeBannerHidden = true;
            ctx.update(p -> p);
        });
        Label body = Ui.wrapped(text);
        HBox.setHgrow(body, Priority.ALWAYS);
        close.setMinWidth(Region.USE_PREF_SIZE);
        later.setMinWidth(Region.USE_PREF_SIZE);
        HBox box = new HBox(16, body, later, close);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().addAll("card", "banner");
        return box;
    }

    /**
     * Payments of the current cycle that need attention: fixed ones overdue or due within
     * {@link #DUE_SOON_DAYS} days, annual ones due this month, an extra paycheck not recorded yet,
     * and a coming shortfall of the reserve. Null when there is nothing.
     */
    private static Node dueSoon(AppContext ctx, CycleSummary s) {
        LocalDate today = ctx.today();
        if (!s.cycle().contains(today)) return null;
        SimpleTable t = new SimpleTable(1, t("dashboard.upcoming.when"), t("dashboard.upcoming.what"), ">" + t("common.amount"), "");
        for (CycleSummary.PlanLine line : s.plan()) {
            if (line.paid()) continue;
            LocalDate d = line.payment().date();
            if (d.isAfter(today.plusDays(DUE_SOON_DAYS))) continue;
            var f = line.payment().payment();
            String when = d.isBefore(today) ? t("dashboard.upcoming.ago", days(today, d)) : d.equals(today) ? t("dashboard.upcoming.today") : t("dashboard.upcoming.inDays", days(d, today));
            Button paid = new Button(t("common.paid"));
            paid.getStyleClass().add("small-button");
            paid.setOnAction(e -> ctx.update(p -> PaymentOps.markPaid(p, line.payment(), s.cycle(), today)));
            HBox what = new HBox(6, SimpleTable.text(f.concept()));
            if (d.isBefore(today)) what.getChildren().add(Ui.badge(t("common.overdue"), "st-warn"));
            t.add(SimpleTable.text(when + " · " + Format.dayMonth(d), "num"), what, SimpleTable.money(f.amount().negate()), paid);
        }
        for (CycleSummary.AnnualLine line : s.annualDue()) {
            if (line.paid()) continue;
            Button paid = new Button(t("common.paidFromReserve"));
            paid.getStyleClass().add("small-button");
            paid.setOnAction(e -> ctx.update(p -> PaymentOps.markAnnualPaid(p, line, s.cycle(), today)));
            t.add(SimpleTable.text(t("dashboard.upcoming.thisMonth"), "num"), new HBox(6, SimpleTable.text(line.item().concept()),
                    Ui.badge(t("common.annual"), "st-n")), SimpleTable.money(line.item().amount().negate()), paid);
        }
        var pay = s.expectedPaycheck();
        if (pay != null && !pay.received()) {
            Button got = new Button(t("common.received"));
            got.getStyleClass().add("small-button");
            got.setOnAction(e -> IncomeDialog.paycheck(ctx, pay));
            t.addStyled("row-income", SimpleTable.text(t("dashboard.upcoming.thisMonth"), "num"),
                    SimpleTable.text(t("dashboard.upcoming.extraPaycheck", Format.monthName(pay.month().getMonthValue()).toLowerCase())),
                    SimpleTable.money(pay.amount(), "inc"), got);
        }
        var shortfall = Pots.reserveShortfall(ctx.profile(), today);
        if (t.isEmpty() && shortfall.isEmpty()) return null;
        List<Node> body = new ArrayList<>();
        body.add(Ui.sectionHead(t("dashboard.upcoming.title")));
        if (!t.isEmpty()) body.add(t.node());
        shortfall.ifPresent(sf -> body.add(Ui.wrapped(t("dashboard.upcoming.reserveShortfall", Format.month(sf.cycle()).toLowerCase(), Format.eur(sf.due()), Format.eur(sf.missing())), "text-bad")));
        return Ui.card(body.toArray(Node[]::new));
    }

    private static String days(LocalDate later, LocalDate earlier) {
        long n = java.time.temporal.ChronoUnit.DAYS.between(earlier, later);
        return plural("dashboard.days", n);
    }

    private static Node kpis(AppContext ctx, CycleSummary s) {
        var cfg = ctx.profile().config();
        double rate = s.income().signum() == 0 ? 0 : s.projectedSavings().doubleValue() / s.income().doubleValue();
        int pct = s.budget().signum() == 0 ? 0 : (int) Math.round(s.spent().doubleValue() / s.budget().doubleValue() * 100);
        HBox row = new HBox(12,
                tile(t("common.income"), Format.eur(s.income()), t("dashboard.kpi.paydayEachMonth", cfg.payday(), (s.newMoney().signum() > 0 ? t("dashboard.kpi.extraThisMonth", Format.eur(s.newMoney())) : ""))),
                tile(t("common.budget"), Format.eur(s.budget()),
                        (s.extraThisCycle().signum() > 0 ? t("dashboard.kpi.includesOneOff", Format.eur(s.extraThisCycle())) : "")
                                + budgetExtras(s)
                                + (s.monthlyReserve().signum() > 0 ? t("dashboard.kpi.toReserve", Format.eur(s.monthlyReserve())) : t("dashboard.kpi.noReserve"))
                                + (s.goalsMonthly().signum() > 0 ? t("dashboard.kpi.toGoals", Format.eur(s.goalsMonthly())) : "")),
                tile(t("dashboard.kpi.projectedSavings"), Format.eur(s.projectedSavings()),
                        t("dashboard.ofIncome", String.format(Format.CA, "%.1f", rate * 100))),
                tile(t("dashboard.kpi.spentSoFar"), Format.eur(s.spent()), t("dashboard.kpi.percentOfBudget", pct, (s.refunds().signum() > 0 ? t("dashboard.kpi.refundsDeducted", Format.eur(s.refunds())) : ""))));
        if (s.projectedSavings().signum() < 0) ((VBox) row.getChildren().get(2)).getStyleClass().add("tile-bad");
        return row;
    }

    /** "+ 499,48 € sobrant del mes anterior · + 150,00 € d'ingressos extra · " */
    private static String budgetExtras(CycleSummary s) {
        BigDecimal carry = s.incomes().stream().filter(i -> i.kind() == Income.Kind.CARRY)
                .filter(i -> i.destination() == Income.Destination.BUDGET)
                .map(Income::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal other = s.extraBudget().subtract(carry);
        return (carry.signum() > 0 ? t("dashboard.kpi.carriedOver", Format.eur(carry)) : "")
                + (other.signum() > 0 ? t("dashboard.kpi.extraIncome", Format.eur(other)) : "");
    }

    private static Node tile(String label, String value, String sub) {
        Label su = Ui.wrapped(sub, "small");
        VBox box = new VBox(4, Ui.label(label, "muted"), Ui.label(value, "kpi"), su);
        box.getStyleClass().add("card");
        box.setPrefWidth(240);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Node hero(CycleSummary s) {
        Label status = switch (s.status()) {
            case OVER_BUDGET -> Ui.badge(t("dashboard.status.overBudget"), "st-bad");
            case AHEAD_OF_PACE -> Ui.badge(t("dashboard.status.aheadOfPace"), "st-warn");
            case ON_TRACK -> Ui.badge(t("dashboard.status.onTrack"), "st-ok");
        };
        VBox left = new VBox(6, Ui.label(t("dashboard.left.title"), "muted"),
                Ui.label(Format.eur(s.left()), "hero"),
                Ui.label(t("dashboard.left.formula", Format.eur(s.budget()), Format.eur(s.spent()), Format.eur(s.pendingFixed())), "small"));
        VBox right = new VBox(6, Ui.label(t("dashboard.left.perDay"), "muted"),
                Ui.label(s.perDay() == null ? "—" : Format.eur(s.perDay()), "hero-secondary"),
                new HBox(8, status, Ui.label(s.daysLeft() > 0 ? plural("dashboard.days", s.daysLeft()) : t("dashboard.left.monthClosed"), "small")));
        right.setAlignment(Pos.TOP_RIGHT);
        ((HBox) right.getChildren().get(2)).setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(16, left, spacer, right);
        box.getStyleClass().addAll("card", "hero-card");
        return box;
    }

    private static Node envelopes(CycleSummary s, AppContext ctx) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        ColumnConstraints name = new ColumnConstraints(200, 240, 300);
        ColumnConstraints bar = new ColumnConstraints();
        bar.setHgrow(Priority.ALWAYS);
        ColumnConstraints amounts = new ColumnConstraints();
        amounts.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints rest = new ColumnConstraints(150, 170, 220);
        grid.getColumnConstraints().addAll(name, bar, amounts, rest);
        var cfg = ctx.profile().config();
        var ine = IneReference.bundled().expected(cfg);
        int row = 0;
        for (CycleSummary.Envelope e : s.envelopes()) {
            BigDecimal ineMonthly = ine.get(e.category()).monthly();
            Label categoryName = Ui.categoryText(e.category());
            categoryName.setWrapText(true);
            categoryName.setMinHeight(Region.USE_PREF_SIZE);
            VBox title = new VBox(2, categoryName);
            ExtraNote extra = new ExtraNote(e);
            if (extra.text != null) title.getChildren().add(Ui.wrapped(extra.text, "small"));
            String annualNote = annualNote(cfg.annuals(), e);
            if (annualNote != null) title.getChildren().add(Ui.wrapped(annualNote, "small", "annual-note"));

            EnvelopeBar b = new EnvelopeBar(e.fixedPaid().doubleValue(), e.variableSpent().doubleValue(),
                    e.fixedPending().doubleValue(), e.annualDue().doubleValue(),
                    e.limit().doubleValue(), ineMonthly.doubleValue(), e.over());
            BigDecimal used = e.fixedPaid().add(e.variableSpent());
            boolean noLimit = e.limit().signum() == 0 && used.signum() == 0 && e.fixedPending().signum() == 0;
            Label amt = noLimit ? Ui.label(t("dashboard.envelopes.noLimit"), "muted")
                    : Ui.label(Format.eur(used) + " / " + Format.eur(e.limit()), "num");
            Label restLabel = noLimit ? Ui.label("", "muted")
                    : e.over() ? Ui.badge(Format.eur(e.remaining()), "st-bad")
                    : BudgetCalculator.isLow(e) ? Ui.badge(t("dashboard.envelopes.left", Format.eur(e.remaining())), "st-warn")
                    : Ui.label(t("dashboard.envelopes.left", Format.eur(e.remaining())), "muted");
            VBox restBox = new VBox(2, restLabel, Ui.label(t("dashboard.envelopes.ine", Format.eur(ineMonthly)), "small"));
            if (e.isEmpty()) {
                title.getStyleClass().add("env-empty");
                amt.getStyleClass().add("env-empty");
            }
            grid.addRow(row++, title, b, amt, restBox);
        }
        List<Node> body = new ArrayList<>();
        javafx.scene.control.Button export = new javafx.scene.control.Button(t("common.exportCsv"));
        export.setOnAction(ev -> ctx.exportEnvelopesCsv());
        body.add(Ui.sectionHead(t("dashboard.envelopes.title"), legend(), export));
        body.add(grid);
        body.add(Ui.wrapped(t("dashboard.envelopes.hint", IneReference.bundled().year()), "small"));
        return Ui.card(body.toArray(Node[]::new));
    }

    /**
     * "Anual: Assegurança de la casa, 360,00 € al març · 30,00 €/mes a la reserva", plus whether it is
     * due this month. Null when the category has no annual payments.
     */
    private static String annualNote(List<AnnualItem> annuals, CycleSummary.Envelope e) {
        List<AnnualItem> mine = annuals.stream().filter(a -> a.category() == e.category())
                .sorted(java.util.Comparator.comparingInt(AnnualItem::month)).toList();
        if (mine.isEmpty()) return null;
        String what = mine.size() == 1
                ? t("dashboard.envelopes.annualOne", mine.get(0).concept(), Format.eur(mine.get(0).amount()), Format.monthName(mine.get(0).month()).toLowerCase())
                : t("dashboard.envelopes.annualMany", mine.size(), Format.eur(e.annualYearly()));
        String reserve = t("dashboard.envelopes.perMonthToReserve", Format.eur(e.annualMonthly()));
        String due = e.annualDue().signum() == 0 ? ""
                : e.annualPaid().compareTo(e.annualDue()) >= 0 ? t("dashboard.envelopes.paidFromReserveThisMonth")
                : t("dashboard.envelopes.dueFromReserveThisMonth");
        return what + reserve + due;
    }

    /** Explains an envelope whose limit was raised by one-off payments this cycle. */
    private record ExtraNote(String text) {
        ExtraNote(CycleSummary.Envelope e) {
            this(e.limit().compareTo(e.baseLimit()) > 0
                    ? t("dashboard.envelopes.limitWithOneOff", Format.eur(e.baseLimit()), Format.eur(e.limit().subtract(e.baseLimit())))
                    : null);
        }
    }

    private static Node legend() {
        return new HBox(14, legendItem("seg-fixed", t("dashboard.envelopes.fixedPaid")), legendItem("seg-var", t("dashboard.envelopes.variable")),
                legendItem("seg-pending", t("dashboard.envelopes.fixedPending")), legendItem("seg-annual", t("dashboard.envelopes.annualReserve")),
                legendItem("ine-swatch", t("common.ine")));
    }

    private static Node legendItem(String style, String text) {
        Region sw = new Region();
        sw.getStyleClass().addAll("swatch", style);
        HBox h = new HBox(6, sw, Ui.label(text, "small"));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static Node weeks(CycleSummary s) {
        HBox row = new HBox(10);
        for (CycleSummary.Week w : s.weeks()) {
            Label st = switch (w.state()) {
                case PENDING -> Ui.badge(t("dashboard.weeks.pending"), "st-n");
                case OVER -> Ui.badge(t("dashboard.weeks.over", Format.eur(w.remaining().negate())), "st-bad");
                case WARNING -> Ui.badge(t("dashboard.envelopes.left", Format.eur(w.remaining())), "st-warn");
                case OK -> Ui.badge(t("dashboard.envelopes.left", Format.eur(w.remaining())), "st-ok");
            };
            VBox card = new VBox(4,
                    Ui.label(t("dashboard.weeks.week", w.number(), Format.dayMonth(w.start()), Format.dayMonth(w.end())), "small"),
                    Ui.label(t("dashboard.weeks.spentOfLimit", Format.eur(w.spent()), Format.eur(w.limit())), "num"), st);
            card.getStyleClass().add("week");
            if (w.current()) card.getStyleClass().add("now");
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
            row.getChildren().add(card);
        }
        int days = s.cycle().days();
        String perWeek = Format.eur(s.variableBudget().multiply(java.math.BigDecimal.valueOf(7))
                .divide(java.math.BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP));
        return Ui.card(Ui.sectionHead(t("dashboard.weeks.title"),
                Ui.label(t("dashboard.weeks.hint", Format.eur(s.variableBudget()), perWeek), "small")), row);
    }

    private static Node rules(AppContext ctx, CycleSummary s) {
        var cfg = ctx.profile().config();
        boolean hasRes = s.monthlyReserve().signum() > 0;
        boolean hasSav = cfg.savings().signum() > 0;
        List<String> r = new ArrayList<>();
        if (hasSav || hasRes) {
            List<String> t = new ArrayList<>();
            if (hasSav) t.add(t("dashboard.rules.transferToSavings", Format.eur(cfg.savings())));
            if (hasRes) t.add(t("dashboard.rules.transferToReserve", Format.eur(s.monthlyReserve())));
            r.add(t("dashboard.rules.paydayWithTransfers", cfg.payday(), String.join(" " + t("common.and") + " ", t)));
        } else {
            r.add(t("dashboard.rules.paydayPlain", cfg.payday()));
        }
        String perWeek = Format.eur(s.variableBudget().multiply(java.math.BigDecimal.valueOf(7))
                .divide(java.math.BigDecimal.valueOf(s.cycle().days()), 2, java.math.RoundingMode.HALF_UP));
        r.add(t("dashboard.rules.weekly", perWeek));
        r.add(t("dashboard.rules.redEnvelope"));
        if (hasRes) r.add(t("dashboard.rules.annuals"));
        r.add(t("dashboard.rules.closeMonth"));
        VBox list = new VBox(6);
        for (int i = 0; i < r.size(); i++) list.getChildren().add(Ui.wrapped((i + 1) + ". " + r.get(i)));
        return Ui.card(Ui.sectionHead(t("dashboard.rules.title")), list);
    }
}
