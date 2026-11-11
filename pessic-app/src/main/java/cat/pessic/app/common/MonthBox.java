package cat.pessic.app.common;

import java.time.YearMonth;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/** Optional month chooser ("—" means no limit) covering three years back and five ahead. */
public final class MonthBox {
    private MonthBox() {}

    public static ComboBox<YearMonth> create(YearMonth selected, YearMonth around) {
        ComboBox<YearMonth> box = new ComboBox<>();
        box.getItems().add(null);
        YearMonth first = around.minusYears(3);
        for (int i = 0; i <= 8 * 12; i++) box.getItems().add(first.plusMonths(i));
        if (selected != null && !box.getItems().contains(selected)) box.getItems().add(1, selected);
        box.setValue(selected);
        box.setVisibleRowCount(12);
        box.setCellFactory(l -> new Cell());
        box.setButtonCell(new Cell());
        return box;
    }

    private static final class Cell extends ListCell<YearMonth> {
        @Override
        protected void updateItem(YearMonth item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item == null ? "—" : Format.month(item));
        }
    }
}
