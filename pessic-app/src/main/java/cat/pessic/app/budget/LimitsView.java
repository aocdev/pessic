package cat.pessic.app.budget;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.BudgetOps;
import cat.pessic.core.budget.Category;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.ine.Catalog;
import cat.pessic.core.ine.IneLimits;
import cat.pessic.core.ine.IneReference;
import cat.pessic.core.payment.FixedPayment;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "Límits": the monthly limit per category next to what INE expects households like this one to
 * spend, what is already committed (fixed payments, annual reserve) and what was spent this cycle.
 */
public final class LimitsView {
    private LimitsView() {}

    public static Region build(AppContext ctx) {
        var cfg = ctx.profile().config();
        CycleSummary s = ctx.summary();
        IneReference ine = IneReference.bundled();
        Map<Category, IneReference.Expectation> expected = ine.expected(cfg);
        Map<Category, TextField> fields = new EnumMap<>(Category.class);
        Map<Category, Label> warnings = new EnumMap<>(Category.class);
        Map<Category, BigDecimal> fixedMonthly = new EnumMap<>(Category.class);

        SimpleTable t = new SimpleTable(0, t("common.category"), ">" + t("budget.limits.monthlyLimit"), ">" + t("budget.limits.ineForecast"), ">" + t("budget.limits.fixedPerMonth"),
                ">" + t("budget.limits.annualsReserve"), ">" + t("budget.limits.spentThisMonth"));
        for (Category c : Category.ALL) {
            TextField f = Ui.amountField(cfg.limit(c));
            f.setPrefWidth(110);
            f.setAlignment(Pos.CENTER_RIGHT);
            fields.put(c, f);

            IneReference.Expectation e = expected.get(c);
            VBox ineCell = new VBox(1, SimpleTable.money(e.monthly()), Ui.label(ineParts(e), "small"));
            ineCell.setAlignment(Pos.CENTER_RIGHT);

            // recurring (not one-off) fixed payments of this category that apply to the viewed cycle
            BigDecimal fixed = cfg.plan().stream()
                    .filter(p -> p.category() == c && !p.extra() && p.appliesTo(ctx.cycle()))
                    .map(FixedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            fixedMonthly.put(c, fixed);
            Label warn = Ui.badge("", "st-warn");
            warnings.put(c, warn);
            VBox fixedCell = new VBox(1, SimpleTable.money(fixed, fixed.signum() == 0 ? "muted" : "num"), warn);
            fixedCell.setAlignment(Pos.CENTER_RIGHT);

            CycleSummary.Envelope env = s.envelopes().stream().filter(x -> x.category() == c).findFirst().orElseThrow();
            t.add(Ui.categoryText(c), f, ineCell, fixedCell,
                    SimpleTable.money(env.annualMonthly(), env.annualMonthly().signum() == 0 ? "muted" : "num"),
                    SimpleTable.money(env.fixedPaid().add(env.variableSpent()), "muted"));
        }

        Label total = Ui.label("", "num", "h2");
        Label savings = Ui.wrapped("", "muted");
        Label error = Ui.errorLabel();
        Label notice = Ui.wrapped("", "notice");
        Ui.showError(notice, null);
        BigDecimal reserve = s.monthlyReserve();
        BigDecimal goals = s.goalsMonthly();
        Runnable recompute = () -> {
            BigDecimal sum = BigDecimal.ZERO;
            boolean bad = false;
            for (var en : fields.entrySet()) {
                TextField f = en.getValue();
                Optional<BigDecimal> v = Ui.amount(f).filter(x -> x.signum() >= 0);
                f.getStyleClass().remove("invalid");
                if (v.isEmpty() && !f.getText().isBlank()) {
                    f.getStyleClass().add("invalid");
                    bad = true;
                }
                BigDecimal limit = v.orElse(BigDecimal.ZERO);
                sum = sum.add(limit);
                BigDecimal fixed = fixedMonthly.get(en.getKey());
                Label warn = warnings.get(en.getKey());
                boolean short_ = fixed.compareTo(limit) > 0;
                warn.setText(short_ ? t("budget.limits.overLimit") : "");
                warn.setVisible(short_);
                warn.setManaged(short_);
            }
            BigDecimal left = cfg.income().subtract(sum).subtract(reserve).subtract(goals);
            total.setText(t("budget.limits.total", Format.eur(sum)));
            savings.setText(t("budget.limits.summary", Format.eur(cfg.income()), Format.eur(sum), (reserve.signum() > 0 ? t("budget.limits.summaryReserve", Format.eur(reserve)) : ""), (goals.signum() > 0 ? t("budget.limits.summaryGoals", Format.eur(goals)) : ""), Format.eur(left), (left.signum() < 0 ? t("budget.limits.summaryOverIncome") : t("budget.limits.summarySavings", (cfg.savings().signum() > 0 ? t("budget.limits.summaryAutomaticSavings", Format.eur(cfg.savings())) : ".")))));
            savings.getStyleClass().remove("text-bad");
            if (left.signum() < 0) savings.getStyleClass().add("text-bad");
            Ui.showError(error, bad ? t("budget.limits.errorAmounts") : null);
        };
        fields.values().forEach(f -> f.textProperty().addListener((o, a, b) -> recompute.run()));
        recompute.run();

        Button save = Ui.primary(t("budget.limits.save"));
        save.setOnAction(e -> {
            Map<Category, BigDecimal> values = new EnumMap<>(Category.class);
            for (var en : fields.entrySet()) {
                String text = en.getValue().getText();
                Optional<BigDecimal> v = text.isBlank() ? Optional.of(BigDecimal.ZERO)
                        : Ui.amount(en.getValue()).filter(x -> x.signum() >= 0);
                if (v.isEmpty()) {
                    recompute.run();
                    en.getValue().requestFocus();
                    return;
                }
                values.put(en.getKey(), v.get());
            }
            if (ctx.update(p -> BudgetOps.setLimits(p, values))) Ui.info(ctx.window(), t("budget.limits.saved"));
        });
        Button fromIne = new Button(t("budget.limits.applyIne"));
        fromIne.setOnAction(e -> {
            IneLimits.Proposal proposal = BudgetOps.applyIneLimits(ctx.profile(), ine);
            proposal.limits().forEach((c, v) -> fields.get(c).setText(Format.input(v)));
            String msg = t("budget.limits.ineApplied");
            if (proposal.scaled()) {
                msg += t("budget.limits.ineScaled", Format.eur(proposal.ineTotal()), Format.eur(proposal.available()), proposal.scalePercent());
            }
            Ui.showError(notice, t("budget.limits.ineReview", msg));
        });
        HBox buttons = new HBox(8, fromIne, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, total, spacer, buttons);
        footer.setAlignment(Pos.CENTER_LEFT);

        return Ui.page(Ui.card(Ui.sectionHead(t("budget.limits.title")),
                Ui.wrapped(t("budget.limits.hint"), "muted"),
                Ui.wrapped(reference(ine, cfg), "small"),
                t.node(), notice, footer, savings, error));
    }

    /** "tipus 486,57 · membres 461,85 · ingressos 404,86" */
    private static String ineParts(IneReference.Expectation e) {
        StringBuilder sb = new StringBuilder(t("budget.limits.byType", Format.eur(e.byType())));
        if (e.bySize() != null) sb.append(t("budget.limits.membersSeparator")).append(Format.eur(e.bySize()));
        if (e.byIncome() != null) sb.append(t("budget.limits.incomeSeparator")).append(Format.eur(e.byIncome()));
        return sb.toString();
    }

    private static String reference(IneReference ine, BudgetConfig cfg) {
        String members = cfg.members() == null ? t("budget.limits.membersUnknown")
                : cfg.members() >= Catalog.MAX_MEMBERS ? t("budget.limits.membersFiveOrMore")
                : cfg.members() == 1 ? t("budget.limits.membersOne") : t("budget.limits.membersMany", cfg.members());
        String income = ine.incomeBand(cfg.income()).map(b -> t("budget.limits.incomeBand", b.replace("Hasta", t("budget.limits.ineBand.upTo"))
                .replace("De ", t("budget.limits.ineBand.from")).replace(" euros o más", t("budget.limits.ineBand.orMore")))).orElse(t("budget.limits.incomeUnknown"));
        return t("budget.limits.ineReference", ine.year(), Catalog.HOUSEHOLDS.get(cfg.household()), members, income);
    }
}
