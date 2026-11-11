package cat.pessic.app.common;

import java.math.BigDecimal;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * A light read-mostly table on a GridPane: header row, zebra-free rows with a bottom rule,
 * one stretching column, right-aligned numeric columns. Lighter than TableView for short lists
 * that mix text, badges and buttons.
 */
public final class SimpleTable {
    private final GridPane grid = new GridPane();
    private final boolean[] right;
    private int rows = 0;

    /** Headers starting with '>' are right-aligned. {@code growColumn} takes the spare width. */
    public SimpleTable(int growColumn, String... headers) {
        right = new boolean[headers.length];
        grid.getStyleClass().add("simple-table");
        grid.setMaxWidth(Double.MAX_VALUE);
        for (int i = 0; i < headers.length; i++) {
            right[i] = headers[i].startsWith(">");
            ColumnConstraints cc = new ColumnConstraints();
            if (i == growColumn) {
                cc.setHgrow(Priority.ALWAYS);
                cc.setMinWidth(160);
            } else {
                // never squeeze amounts, badges or headers into "…": the stretching column gives way instead
                cc.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }
            grid.getColumnConstraints().add(cc);
            grid.add(cell(new Label(right[i] ? headers[i].substring(1) : headers[i]), i, "th"), i, 0);
        }
    }

    public void add(Node... cells) {
        addStyled(null, cells);
    }

    public void addStyled(String rowStyle, Node... cells) {
        rows++;
        for (int i = 0; i < cells.length; i++) {
            HBox c = cell(cells[i] == null ? new Label("") : cells[i], i, "td");
            if (rowStyle != null) c.getStyleClass().add(rowStyle);
            grid.add(c, i, rows);
        }
    }

    /** One cell spanning the whole row, for empty-state messages. */
    public void addMessage(String text) {
        rows++;
        HBox c = cell(Ui.wrapped(text, "muted"), 0, "td");
        grid.add(c, 0, rows, right.length, 1);
    }

    public boolean isEmpty() {
        return rows == 0;
    }

    public GridPane node() {
        return grid;
    }

    public static Label text(String s, String... styles) {
        return Ui.label(s, styles);
    }

    public static Label money(BigDecimal v, String... styles) {
        Label l = Ui.label(Format.eur(v), styles);
        l.getStyleClass().add("num");
        return l;
    }

    private HBox cell(Node content, int col, String style) {
        HBox box = new HBox(6, content);
        box.getStyleClass().add(style);
        box.setAlignment(right[col] ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
