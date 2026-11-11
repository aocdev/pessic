package cat.pessic.app.dashboard;

import javafx.geometry.Insets;
import javafx.scene.layout.Region;

/**
 * Horizontal bar for one category: fixed payments already paid, variable spending, fixed payments
 * still pending and, striped, annual payments due this month (paid from the reserve, so they don't
 * use the monthly limit). A thin vertical line marks what INE expects households like yours to
 * spend per month. Scales to the space it is given.
 */
public final class EnvelopeBar extends Region {
    private static final double HEIGHT = 12;

    private final Region track = segment("env-track");
    private final Region paid = segment("seg-fixed");
    private final Region variable = segment("seg-var");
    private final Region pending = segment("seg-pending");
    private final Region annual = segment("seg-annual");
    private final Region ine = segment("ine-marker");
    private final double[] values;
    private final double ineValue;
    private final double scale;

    EnvelopeBar(double fixedPaid, double variableSpent, double fixedPending, double annualDue, double limit,
                double ineMonthly, boolean over) {
        values = new double[]{fixedPaid, variableSpent, fixedPending, annualDue};
        ineValue = ineMonthly;
        double used = fixedPaid + variableSpent + fixedPending + annualDue;
        scale = Math.max(Math.max(limit, used), Math.max(ineMonthly, 1)) * 1.04; // room for the marker
        getStyleClass().add("env-bar");
        if (over) getStyleClass().add("over");
        getChildren().addAll(track, paid, variable, pending, annual, ine);
        setMinWidth(120);
        setPrefWidth(320);
        setMaxWidth(Double.MAX_VALUE);
        setMinHeight(HEIGHT + 6);
        setPrefHeight(HEIGHT + 6);
        setMaxHeight(HEIGHT + 6);
        if (limit > 0) {
            track.getStyleClass().add("has-limit");
            this.limitWidthFraction = limit / scale;
        }
    }

    private double limitWidthFraction = 0;

    private static Region segment(String style) {
        Region r = new Region();
        r.getStyleClass().add(style);
        return r;
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double w = getWidth() - in.getLeft() - in.getRight();
        double top = in.getTop() + 3;
        double x0 = in.getLeft();
        // the track covers the monthly limit; without a limit only a thin baseline is drawn
        if (limitWidthFraction > 0) track.resizeRelocate(x0, top, w * limitWidthFraction, HEIGHT);
        else track.resizeRelocate(x0, top + HEIGHT / 2 - 1, w, 2);
        double x = x0;
        Region[] segs = {paid, variable, pending, annual};
        for (int i = 0; i < segs.length; i++) {
            double sw = Math.max(0, w * values[i] / scale);
            segs[i].resizeRelocate(x, top, sw, HEIGHT);
            x += sw;
        }
        boolean showIne = ineValue > 0;
        ine.setVisible(showIne);
        if (showIne) ine.resizeRelocate(x0 + w * ineValue / scale - 1, in.getTop(), 2, HEIGHT + 6);
    }
}
