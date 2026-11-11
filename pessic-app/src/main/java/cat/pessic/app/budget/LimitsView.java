package cat.pessic.app.budget;

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

        SimpleTable t = new SimpleTable(0, "Categoria", ">Límit mensual (€)", ">Previsió INE", ">Fixos al mes",
                ">Anuals (reserva)", ">Gastat aquest mes");
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
                warn.setText(short_ ? "supera el límit" : "");
                warn.setVisible(short_);
                warn.setManaged(short_);
            }
            BigDecimal left = cfg.income().subtract(sum).subtract(reserve).subtract(goals);
            total.setText("Total: " + Format.eur(sum));
            savings.setText("Ingressos " + Format.eur(cfg.income()) + " − límits " + Format.eur(sum)
                    + (reserve.signum() > 0 ? " − reserva d'anuals " + Format.eur(reserve) : "")
                    + (goals.signum() > 0 ? " − guardioles " + Format.eur(goals) : "")
                    + " = " + Format.eur(left)
                    + (left.signum() < 0 ? ". Els límits superen els ingressos." : " d'estalvi previst"
                    + (cfg.savings().signum() > 0 ? " (inclou els " + Format.eur(cfg.savings()) + " d'estalvi automàtic)." : ".")));
            savings.getStyleClass().remove("text-bad");
            if (left.signum() < 0) savings.getStyleClass().add("text-bad");
            Ui.showError(error, bad ? "Hi ha imports que no s'entenen (marcats en vermell)." : null);
        };
        fields.values().forEach(f -> f.textProperty().addListener((o, a, b) -> recompute.run()));
        recompute.run();

        Button save = Ui.primary("Desa els límits");
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
            if (ctx.update(p -> BudgetOps.setLimits(p, values))) Ui.info(ctx.window(), "Límits desats.");
        });
        Button fromIne = new Button("Posa els límits segons l'INE");
        fromIne.setOnAction(e -> {
            IneLimits.Proposal proposal = BudgetOps.applyIneLimits(ctx.profile(), ine);
            proposal.limits().forEach((c, v) -> fields.get(c).setText(Format.input(v)));
            String msg = "S'han omplert els límits amb la previsió de l'INE per a llars com la teva, descomptant el que ja "
                    + "cobreix la reserva d'anuals.";
            if (proposal.scaled()) {
                msg += " La previsió (" + Format.eur(proposal.ineTotal()) + ") no cap en el que tens disponible cada mes ("
                        + Format.eur(proposal.available()) + " = ingressos − estalvi automàtic − reserva − guardioles), així que s'han "
                        + "ajustat tots al " + proposal.scalePercent() + " %.";
            }
            Ui.showError(notice, msg + " Revisa'ls i prem «Desa els límits».");
        });
        HBox buttons = new HBox(8, fromIne, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(12, total, spacer, buttons);
        footer.setAlignment(Pos.CENTER_LEFT);

        return Ui.page(Ui.card(Ui.sectionHead("Límits per categoria"),
                Ui.wrapped("Quant vols gastar cada mes en cada categoria, incloent-hi els pagaments fixos d'aquella categoria. "
                        + "Els pagaments puntuals se sumen automàticament al límit els mesos que toquen, i els anuals surten de la "
                        + "reserva.", "muted"),
                Ui.wrapped(reference(ine, cfg), "small"),
                t.node(), notice, footer, savings, error));
    }

    /** "tipus 486,57 · membres 461,85 · ingressos 404,86" */
    private static String ineParts(IneReference.Expectation e) {
        StringBuilder sb = new StringBuilder("tipus " + Format.eur(e.byType()));
        if (e.bySize() != null) sb.append(" · membres ").append(Format.eur(e.bySize()));
        if (e.byIncome() != null) sb.append(" · ingressos ").append(Format.eur(e.byIncome()));
        return sb.toString();
    }

    private static String reference(IneReference ine, BudgetConfig cfg) {
        String members = cfg.members() == null ? "membres no indicats (afegeix-los a «Configuració»)"
                : cfg.members() >= Catalog.MAX_MEMBERS ? "5 o més membres"
                : cfg.members() == 1 ? "1 membre" : cfg.members() + " membres";
        String income = ine.incomeBand(cfg.income()).map(b -> "ingressos " + b.replace("Hasta", "fins a")
                .replace("De ", "de ").replace(" euros o más", " euros o més")).orElse("ingressos no indicats");
        return "Previsió INE: mitjana del que gasten al mes les llars semblants a la teva segons l'Encuesta de "
                + "Presupuestos Familiares " + ine.year() + " (" + Catalog.HOUSEHOLDS.get(cfg.household()) + " · "
                + members + " · " + income + ").";
    }
}
