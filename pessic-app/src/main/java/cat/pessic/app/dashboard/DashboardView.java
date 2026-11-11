package cat.pessic.app.dashboard;

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
                ? Format.month(first) + " ha acabat. Tanca'l per guardar-ne el resum, posar diners a la reserva i a les "
                + "guardioles i decidir què fas amb el que ha sobrat."
                : "Tens " + pending.size() + " mesos acabats sense tancar, des de " + Format.month(first).toLowerCase()
                + ". Tanca'ls d'un en un, començant pel més antic.";
        Button close = Ui.primary("Tanca " + Format.month(first).toLowerCase());
        close.setOnAction(e -> MonthCloseDialog.show(ctx, first));
        Button later = Ui.link("Més tard", () -> {
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
        SimpleTable t = new SimpleTable(1, "Quan", "Què", ">Import", "");
        for (CycleSummary.PlanLine line : s.plan()) {
            if (line.paid()) continue;
            LocalDate d = line.payment().date();
            if (d.isAfter(today.plusDays(DUE_SOON_DAYS))) continue;
            var f = line.payment().payment();
            String when = d.isBefore(today) ? "fa " + days(today, d) : d.equals(today) ? "avui" : "d'aquí " + days(d, today);
            Button paid = new Button("Pagat");
            paid.getStyleClass().add("small-button");
            paid.setOnAction(e -> ctx.update(p -> PaymentOps.markPaid(p, line.payment(), s.cycle(), today)));
            HBox what = new HBox(6, SimpleTable.text(f.concept()));
            if (d.isBefore(today)) what.getChildren().add(Ui.badge("vençut?", "st-warn"));
            t.add(SimpleTable.text(when + " · " + Format.dayMonth(d), "num"), what, SimpleTable.money(f.amount().negate()), paid);
        }
        for (CycleSummary.AnnualLine line : s.annualDue()) {
            if (line.paid()) continue;
            Button paid = new Button("Pagat amb la reserva");
            paid.getStyleClass().add("small-button");
            paid.setOnAction(e -> ctx.update(p -> PaymentOps.markAnnualPaid(p, line, s.cycle(), today)));
            t.add(SimpleTable.text("aquest mes", "num"), new HBox(6, SimpleTable.text(line.item().concept()),
                    Ui.badge("anual", "st-n")), SimpleTable.money(line.item().amount().negate()), paid);
        }
        var pay = s.expectedPaycheck();
        if (pay != null && !pay.received()) {
            Button got = new Button("Rebuda");
            got.getStyleClass().add("small-button");
            got.setOnAction(e -> IncomeDialog.paycheck(ctx, pay));
            t.addStyled("row-income", SimpleTable.text("aquest mes", "num"),
                    SimpleTable.text("Paga extra de " + Format.monthName(pay.month().getMonthValue()).toLowerCase()),
                    SimpleTable.money(pay.amount(), "inc"), got);
        }
        var shortfall = Pots.reserveShortfall(ctx.profile(), today);
        if (t.isEmpty() && shortfall.isEmpty()) return null;
        List<Node> body = new ArrayList<>();
        body.add(Ui.sectionHead("Propers pagaments"));
        if (!t.isEmpty()) body.add(t.node());
        shortfall.ifPresent(sf -> body.add(Ui.wrapped("Al " + Format.month(sf.cycle()).toLowerCase() + " toca pagar "
                + Format.eur(sf.due()) + " d'anuals i a la reserva hi faltaran " + Format.eur(sf.missing())
                + ". Mira-ho a «Estalvi».", "text-bad")));
        return Ui.card(body.toArray(Node[]::new));
    }

    private static String days(LocalDate later, LocalDate earlier) {
        long n = java.time.temporal.ChronoUnit.DAYS.between(earlier, later);
        return n == 1 ? "1 dia" : n + " dies";
    }

    private static Node kpis(AppContext ctx, CycleSummary s) {
        var cfg = ctx.profile().config();
        double rate = s.income().signum() == 0 ? 0 : s.projectedSavings().doubleValue() / s.income().doubleValue();
        int pct = s.budget().signum() == 0 ? 0 : (int) Math.round(s.spent().doubleValue() / s.budget().doubleValue() * 100);
        HBox row = new HBox(12,
                tile("Ingressos", Format.eur(s.income()), "el dia " + cfg.payday() + " de cada mes"
                        + (s.newMoney().signum() > 0 ? " · + " + Format.eur(s.newMoney()) + " extra aquest mes" : "")),
                tile("Pressupost", Format.eur(s.budget()),
                        (s.extraThisCycle().signum() > 0 ? "inclou " + Format.eur(s.extraThisCycle()) + " puntuals · " : "")
                                + budgetExtras(s)
                                + (s.monthlyReserve().signum() > 0 ? "+ " + Format.eur(s.monthlyReserve()) + " a la reserva" : "sense reserva d'anuals")
                                + (s.goalsMonthly().signum() > 0 ? " · " + Format.eur(s.goalsMonthly()) + " a guardioles" : "")),
                tile("Estalvi previst", Format.eur(s.projectedSavings()),
                        String.format(Format.CA, "%.1f %% dels ingressos", rate * 100)),
                tile("Gastat fins ara", Format.eur(s.spent()), pct + " % del pressupost"
                        + (s.refunds().signum() > 0 ? " · descomptades " + Format.eur(s.refunds()) + " de devolucions" : "")));
        if (s.projectedSavings().signum() < 0) ((VBox) row.getChildren().get(2)).getStyleClass().add("tile-bad");
        return row;
    }

    /** "+ 499,48 € sobrant del mes anterior · + 150,00 € d'ingressos extra · " */
    private static String budgetExtras(CycleSummary s) {
        BigDecimal carry = s.incomes().stream().filter(i -> i.kind() == Income.Kind.CARRY)
                .filter(i -> i.destination() == Income.Destination.BUDGET)
                .map(Income::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal other = s.extraBudget().subtract(carry);
        return (carry.signum() > 0 ? "+ " + Format.eur(carry) + " sobrant del mes anterior · " : "")
                + (other.signum() > 0 ? "+ " + Format.eur(other) + " d'ingressos extra · " : "");
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
            case OVER_BUDGET -> Ui.badge("Pressupost superat", "st-bad");
            case AHEAD_OF_PACE -> Ui.badge("Vas per sobre del ritme", "st-warn");
            case ON_TRACK -> Ui.badge("Dins del ritme", "st-ok");
        };
        VBox left = new VBox(6, Ui.label("Et queda per a la despesa del dia a dia", "muted"),
                Ui.label(Format.eur(s.left()), "hero"),
                Ui.label("Pressupost " + Format.eur(s.budget()) + " − gastat " + Format.eur(s.spent())
                        + " − fixos pendents " + Format.eur(s.pendingFixed()), "small"));
        VBox right = new VBox(6, Ui.label("Límit per dia", "muted"),
                Ui.label(s.perDay() == null ? "—" : Format.eur(s.perDay()), "hero-secondary"),
                new HBox(8, status, Ui.label(s.daysLeft() > 0 ? s.daysLeft() + " dies" : "mes tancat", "small")));
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
            Label amt = noLimit ? Ui.label("sense límit", "muted")
                    : Ui.label(Format.eur(used) + " / " + Format.eur(e.limit()), "num");
            Label restLabel = noLimit ? Ui.label("", "muted")
                    : e.over() ? Ui.badge(Format.eur(e.remaining()), "st-bad")
                    : BudgetCalculator.isLow(e) ? Ui.badge("queden " + Format.eur(e.remaining()), "st-warn")
                    : Ui.label("queden " + Format.eur(e.remaining()), "muted");
            VBox restBox = new VBox(2, restLabel, Ui.label("INE " + Format.eur(ineMonthly), "small"));
            if (e.isEmpty()) {
                title.getStyleClass().add("env-empty");
                amt.getStyleClass().add("env-empty");
            }
            grid.addRow(row++, title, b, amt, restBox);
        }
        List<Node> body = new ArrayList<>();
        javafx.scene.control.Button export = new javafx.scene.control.Button("Exporta (.csv)…");
        export.setOnAction(ev -> ctx.exportEnvelopesCsv());
        body.add(Ui.sectionHead("Sobres per categoria", legend(), export));
        body.add(grid);
        body.add(Ui.wrapped("Les 13 categories de l'INE (COICOP 2018). La línia vertical marca el que gasten al mes les llars "
                + "com la teva segons l'INE (EPF " + IneReference.bundled().year() + "); els límits es canvien a «Límits». "
                + "Passa el ratolí per sobre d'una categoria per veure què hi va.", "small"));
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
                ? "Anual: " + mine.get(0).concept() + ", " + Format.eur(mine.get(0).amount()) + " al "
                        + Format.monthName(mine.get(0).month()).toLowerCase()
                : "Anuals: " + mine.size() + " pagaments, " + Format.eur(e.annualYearly()) + " l'any";
        String reserve = " · " + Format.eur(e.annualMonthly()) + "/mes a la reserva";
        String due = e.annualDue().signum() == 0 ? ""
                : e.annualPaid().compareTo(e.annualDue()) >= 0 ? ". Aquest mes: pagat de la reserva"
                : ". Aquest mes toca pagar-lo de la reserva";
        return what + reserve + due;
    }

    /** Explains an envelope whose limit was raised by one-off payments this cycle. */
    private record ExtraNote(String text) {
        ExtraNote(CycleSummary.Envelope e) {
            this(e.limit().compareTo(e.baseLimit()) > 0
                    ? "Límit " + Format.eur(e.baseLimit()) + " + " + Format.eur(e.limit().subtract(e.baseLimit())) + " puntual aquest mes"
                    : null);
        }
    }

    private static Node legend() {
        return new HBox(14, legendItem("seg-fixed", "Fix pagat"), legendItem("seg-var", "Variable"),
                legendItem("seg-pending", "Fix pendent"), legendItem("seg-annual", "Anual (reserva)"),
                legendItem("ine-swatch", "INE"));
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
                case PENDING -> Ui.badge("pendent", "st-n");
                case OVER -> Ui.badge("passat " + Format.eur(w.remaining().negate()), "st-bad");
                case WARNING -> Ui.badge("queden " + Format.eur(w.remaining()), "st-warn");
                case OK -> Ui.badge("queden " + Format.eur(w.remaining()), "st-ok");
            };
            VBox card = new VBox(4,
                    Ui.label("Setmana " + w.number() + " · " + Format.dayMonth(w.start()) + "–" + Format.dayMonth(w.end()), "small"),
                    Ui.label(Format.eur(w.spent()) + " de " + Format.eur(w.limit()), "num"), st);
            card.getStyleClass().add("week");
            if (w.current()) card.getStyleClass().add("now");
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
            row.getChildren().add(card);
        }
        int days = s.cycle().days();
        String perWeek = Format.eur(s.variableBudget().multiply(java.math.BigDecimal.valueOf(7))
                .divide(java.math.BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP));
        return Ui.card(Ui.sectionHead("Límit setmanal",
                Ui.label("Despesa variable: " + Format.eur(s.variableBudget()) + " al mes, " + perWeek + " per setmana", "small")), row);
    }

    private static Node rules(AppContext ctx, CycleSummary s) {
        var cfg = ctx.profile().config();
        boolean hasRes = s.monthlyReserve().signum() > 0;
        boolean hasSav = cfg.savings().signum() > 0;
        List<String> r = new ArrayList<>();
        if (hasSav || hasRes) {
            List<String> t = new ArrayList<>();
            if (hasSav) t.add("una transferència automàtica de " + Format.eur(cfg.savings()) + " a estalvi");
            if (hasRes) t.add("una de " + Format.eur(s.monthlyReserve()) + " a la reserva d'anuals");
            r.add("El dia " + cfg.payday() + ", quan cobres, fes " + String.join(" i ", t) + ". El que queda al compte és el teu mes.");
        } else {
            r.add("El dia " + cfg.payday() + ", quan cobres, el que entra és el teu mes. Si pots, programa una transferència automàtica a estalvi aquell mateix dia.");
        }
        String perWeek = Format.eur(s.variableBudget().multiply(java.math.BigDecimal.valueOf(7))
                .divide(java.math.BigDecimal.valueOf(s.cycle().days()), 2, java.math.RoundingMode.HALF_UP));
        r.add("Cada setmana té el seu límit (" + perWeek + "). El que no gastes passa a la setmana següent; el que et passes, es resta de les que queden.");
        r.add("Un sobre en vermell es tanca fins al mes següent. Si de veritat cal gastar-hi més, abaixa el límit d'un altre sobre en la mateixa quantitat.");
        if (hasRes) r.add("Els pagaments anuals surten de la reserva, mai dels diners del mes. Marca'ls a «Pagaments fixos» quan es cobrin.");
        r.add("Quan acabi el mes, tanca'l: es guarda el resum a «Històric» i decideixes on va el que ha sobrat.");
        VBox list = new VBox(6);
        for (int i = 0; i < r.size(); i++) list.getChildren().add(Ui.wrapped((i + 1) + ". " + r.get(i)));
        return Ui.card(Ui.sectionHead("Les regles del mes"), list);
    }
}
