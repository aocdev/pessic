package cat.pessic.app.history;

import cat.pessic.app.AppContext;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.app.monthclose.MonthCloseDialog;
import cat.pessic.core.budget.Category;
import cat.pessic.core.history.History;
import cat.pessic.core.ine.IneReference;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.monthclose.MonthClose;
import java.math.BigDecimal;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Històric": the last 12 months, what came in, what was spent and what was saved, and each category over time. */
public final class HistoryView {
    private HistoryView() {}

    static final int MONTHS = 12;
    private static Category selected = Category.FOOD;

    public static Region build(AppContext ctx) {
        List<History.Row> rows = History.last(ctx.profile(), ctx.today(), MONTHS);
        return Ui.page(overview(ctx, rows), byCategory(ctx, rows), table(ctx, rows));
    }

    private static String shortMonth(History.Row r) {
        String m = Format.monthName(r.cycle().getMonthValue());
        return m.substring(0, Math.min(3, m.length())).toLowerCase() + " " + String.valueOf(r.cycle().getYear()).substring(2);
    }

    private static Node overview(AppContext ctx, List<History.Row> rows) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("€");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setCategoryGap(14);
        chart.setBarGap(2);
        chart.setPrefHeight(300);
        XYChart.Series<String, Number> in = new XYChart.Series<>();
        in.setName("Ingressos");
        XYChart.Series<String, Number> spent = new XYChart.Series<>();
        spent.setName("Gastat del mes");
        XYChart.Series<String, Number> set = new XYChart.Series<>();
        set.setName("Apartat (reserva, guardioles)");
        for (History.Row r : rows) {
            String k = shortMonth(r);
            in.getData().add(new XYChart.Data<>(k, r.income().add(r.extra())));
            spent.getData().add(new XYChart.Data<>(k, r.spent()));
            set.getData().add(new XYChart.Data<>(k, r.toPots()));
        }
        chart.getData().addAll(List.of(in, spent, set));
        chart.getStyleClass().add("history-chart");

        long closed = rows.stream().filter(History.Row::closed).count();
        BigDecimal saved = rows.stream().filter(History.Row::closed).map(History.Row::saved).reduce(BigDecimal.ZERO, BigDecimal::add);
        // the running month shows what came in and what was spent so far, but nothing set aside yet

        Button export = new Button("Exporta (.csv)…");
        export.setOnAction(e -> ctx.exportHistoryCsv(rows));
        String title = rows.size() == 1 ? "Aquest mes" : "Els últims " + rows.size() + " mesos";
        return Ui.card(Ui.sectionHead(title, export), chart,
                Ui.wrapped(closed == 0
                        ? "Encara no has tancat cap mes. Els mesos sense tancar es calculen amb els límits d'ara i surten "
                        + "marcats com a «estimat». Quan acabi un mes, tanca'l des de «Resum»."
                        : "Mesos tancats: " + closed + ". Hi has estalviat " + Format.eur(saved) + " (sense comptar el que "
                        + "has apartat a la reserva i a les guardioles). Els mesos sense tancar són una estimació amb els límits d'ara.",
                        "small"));
    }

    private static Node byCategory(AppContext ctx, List<History.Row> rows) {
        ComboBox<Category> pick = Ui.categoryBox(selected);
        pick.setPrefWidth(320);
        VBox holder = new VBox();
        BigDecimal ine = IneReference.bundled().expected(ctx.profile().config()).get(selected).monthly();
        Runnable draw = () -> {
            selected = pick.getValue();
            BigDecimal ineMonthly = IneReference.bundled().expected(ctx.profile().config()).get(selected).monthly();
            CategoryAxis x = new CategoryAxis();
            NumberAxis y = new NumberAxis();
            y.setLabel("€");
            LineChart<String, Number> chart = new LineChart<>(x, y);
            chart.setAnimated(false);
            chart.setPrefHeight(260);
            chart.setCreateSymbols(true);
            XYChart.Series<String, Number> spent = new XYChart.Series<>();
            spent.setName("Gastat");
            XYChart.Series<String, Number> limit = new XYChart.Series<>();
            limit.setName("Límit");
            XYChart.Series<String, Number> ineLine = new XYChart.Series<>();
            ineLine.setName("INE");
            for (History.Row r : rows) {
                String k = shortMonth(r);
                CycleClose.CategoryLine l = r.categories().get(selected);
                spent.getData().add(new XYChart.Data<>(k, l == null ? 0 : l.spent()));
                limit.getData().add(new XYChart.Data<>(k, l == null ? 0 : l.limit()));
                ineLine.getData().add(new XYChart.Data<>(k, ineMonthly));
            }
            chart.getData().addAll(List.of(spent, limit, ineLine));
            chart.getStyleClass().add("category-chart");
            holder.getChildren().setAll(chart);
        };
        pick.valueProperty().addListener((o, a, b) -> draw.run());
        draw.run();
        return Ui.card(Ui.sectionHead("Per categoria", pick), holder,
                Ui.wrapped("Compara el que has gastat cada mes amb el límit que tenies i amb el que gasten les llars com la "
                        + "teva segons l'INE (" + Format.eur(ine) + " al mes per a " + selected.displayName().toLowerCase() + ").",
                        "small"));
    }

    private static Node table(AppContext ctx, List<History.Row> rows) {
        SimpleTable t = new SimpleTable(0, "Mes", ">Ingressos", ">Pressupost", ">Gastat", ">Apartat", ">Estalvi", ">%",
                ">Dif. banc", "");
        for (int i = rows.size() - 1; i >= 0; i--) {
            History.Row r = rows.get(i);
            javafx.scene.layout.HBox month = new javafx.scene.layout.HBox(6, SimpleTable.text(Format.month(r.cycle())));
            if (r.current()) month.getChildren().add(Ui.badge("en curs", "st-n"));
            else if (!r.closed()) month.getChildren().add(Ui.badge("estimat", "st-warn"));
            Node action = null;
            if (r.closed()) {
                action = Ui.link("Reobre", () -> {
                    if (Ui.choose(ctx.window(), "Reobre " + Format.month(r.cycle()).toLowerCase(),
                            "Es desfaran les aportacions que es van fer en tancar-lo i el sobrant que es va passar al mes "
                                    + "següent o a una guardiola. Després el podràs tornar a tancar.", -1, "Reobre'l") == 0) {
                        ctx.update(p -> MonthClose.reopen(p, r.cycle()));
                    }
                });
            } else if (!r.current() && MonthClose.pending(ctx.profile(), ctx.today()).stream().findFirst()
                    .filter(r.cycle()::equals).isPresent()) {
                action = Ui.link("Tanca'l", () -> MonthCloseDialog.show(ctx, r.cycle()));
            }
            BigDecimal saved = r.saved();
            // a month still running has not saved anything yet
            Node savedCell = r.current() && !r.closed() ? SimpleTable.text("—", "muted")
                    : saved.signum() < 0 ? Ui.badge(Format.eur(saved), "st-bad") : SimpleTable.money(saved, "inc");
            Node rate = r.current() && !r.closed() ? SimpleTable.text("—", "muted")
                    : SimpleTable.text(r.savingsRate().toPlainString() + " %", "num", "muted");
            t.addStyled(r.closed() ? null : "row-estimate", month, SimpleTable.money(r.income().add(r.extra())),
                    SimpleTable.money(r.budget(), "muted"), SimpleTable.money(r.spent()),
                    SimpleTable.money(r.toPots(), "muted"), savedCell, rate,
                    r.bankDifference() == null ? SimpleTable.text("—", "muted")
                            : r.bankDifference().signum() < 0 ? Ui.badge(Format.eur(r.bankDifference()), "st-warn")
                            : SimpleTable.money(r.bankDifference(), "muted"),
                    action);
        }
        return Ui.card(Ui.sectionHead("Mes a mes"), t.node(),
                Ui.wrapped("«Apartat» és el que va a la reserva, a les guardioles o passa al mes següent. «Estalvi» és el "
                        + "que queda després. El % és la part dels ingressos que no es va gastar del mes. «Dif. banc» és la diferència entre "
                        + "el saldo del banc i el que calculava Pessic quan vas tancar el mes: si és negativa, sol ser despesa "
                        + "sense apuntar.", "small"));
    }
}
