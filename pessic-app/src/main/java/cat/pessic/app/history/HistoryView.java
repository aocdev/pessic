package cat.pessic.app.history;

import static cat.pessic.core.common.I18n.t;
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
        in.setName(t("common.income"));
        XYChart.Series<String, Number> spent = new XYChart.Series<>();
        spent.setName(t("history.chart.spent"));
        XYChart.Series<String, Number> set = new XYChart.Series<>();
        set.setName(t("history.chart.setAside"));
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

        Button export = new Button(t("common.exportCsv"));
        export.setOnAction(e -> ctx.exportHistoryCsv(rows));
        String title = rows.size() == 1 ? t("history.thisMonth") : t("history.lastMonths", rows.size());
        return Ui.card(Ui.sectionHead(title, export), chart,
                Ui.wrapped(closed == 0
                        ? t("history.noneClosed")
                        : t("history.closedSummary", closed, Format.eur(saved)),
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
            spent.setName(t("common.spent"));
            XYChart.Series<String, Number> limit = new XYChart.Series<>();
            limit.setName(t("common.limit"));
            XYChart.Series<String, Number> ineLine = new XYChart.Series<>();
            ineLine.setName(t("common.ine"));
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
        return Ui.card(Ui.sectionHead(t("history.byCategory.title"), pick), holder,
                Ui.wrapped(t("history.byCategory.hint", Format.eur(ine), selected.displayName().toLowerCase()),
                        "small"));
    }

    private static Node table(AppContext ctx, List<History.Row> rows) {
        SimpleTable t = new SimpleTable(0, t("common.month"), ">" + t("common.income"), ">" + t("common.budget"), ">" + t("common.spent"), ">" + t("history.setAside"), ">" + t("common.savings"), ">%",
                ">" + t("history.bankDifference"), "");
        for (int i = rows.size() - 1; i >= 0; i--) {
            History.Row r = rows.get(i);
            javafx.scene.layout.HBox month = new javafx.scene.layout.HBox(6, SimpleTable.text(Format.month(r.cycle())));
            if (r.current()) month.getChildren().add(Ui.badge(t("history.running"), "st-n"));
            else if (!r.closed()) month.getChildren().add(Ui.badge(t("history.estimated"), "st-warn"));
            Node action = null;
            if (r.closed()) {
                action = Ui.link(t("history.reopen"), () -> {
                    if (Ui.choose(ctx.window(), t("history.reopenTitle", Format.month(r.cycle()).toLowerCase()),
                            t("history.reopenHint"), -1, t("history.reopenConfirm")) == 0) {
                        ctx.update(p -> MonthClose.reopen(p, r.cycle()));
                    }
                });
            } else if (!r.current() && MonthClose.pending(ctx.profile(), ctx.today()).stream().findFirst()
                    .filter(r.cycle()::equals).isPresent()) {
                action = Ui.link(t("history.closeIt"), () -> MonthCloseDialog.show(ctx, r.cycle()));
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
        return Ui.card(Ui.sectionHead(t("history.title")), t.node(),
                Ui.wrapped(t("history.hint"), "small"));
    }
}
