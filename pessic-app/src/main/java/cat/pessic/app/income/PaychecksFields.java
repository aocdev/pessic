package cat.pessic.app.income;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.Ui;
import cat.pessic.core.income.Paychecks;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Pagues a l'any": 12, or 14 with two extra paychecks (amount and months). */
public final class PaychecksFields {

    final ComboBox<Integer> count = new ComboBox<>();
    public final TextField amount;
    final ComboBox<Integer> first = monthBox();
    final ComboBox<Integer> second = monthBox();
    private final HBox extras;

    public PaychecksFields(Paychecks p) {
        count.getItems().setAll(12, 14);
        count.setValue(p.count());
        count.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer n) {
                return n == null ? "" : n == 14 ? t("income.paychecks.fourteen") : t("income.paychecks.twelve");
            }
            @Override public Integer fromString(String s) { return null; }
        });
        amount = Ui.amountField(p.extraAmount());
        amount.setPromptText(t("income.paychecks.sameAsSalary"));
        first.setValue(p.months().get(0));
        second.setValue(p.months().size() > 1 ? p.months().get(1) : 12);
        extras = Ui.row(Ui.field(t("income.paychecks.extraAmount"), amount), Ui.field(t("income.paychecks.firstMonth"), first),
                Ui.field(t("income.paychecks.secondMonth"), second));
        Runnable sync = () -> {
            boolean on = count.getValue() != null && count.getValue() == 14;
            extras.setVisible(on);
            extras.setManaged(on);
        };
        count.valueProperty().addListener((o, a, b) -> sync.run());
        sync.run();
    }

    private static ComboBox<Integer> monthBox() {
        ComboBox<Integer> box = new ComboBox<>();
        box.getItems().setAll(IntStream.rangeClosed(1, 12).boxed().toList());
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer m) { return m == null ? "" : Format.monthName(m); }
            @Override public Integer fromString(String s) { return null; }
        });
        return box;
    }

    /** The setting, or an error message. */
    public EditDialog.Check<Paychecks> value() {
        if (count.getValue() == null || count.getValue() == 12) return EditDialog.Check.ok(Paychecks.TWELVE);
        Optional<BigDecimal> a = amount.getText().isBlank() ? Optional.empty()
                : Ui.amount(amount).filter(v -> v.signum() > 0);
        if (!amount.getText().isBlank() && a.isEmpty()) return EditDialog.Check.fail(t("income.paychecks.errorAmount"));
        if (first.getValue() == null || second.getValue() == null || first.getValue().equals(second.getValue()))
            return EditDialog.Check.fail(t("income.paychecks.errorMonths"));
        return EditDialog.Check.ok(new Paychecks(14, a.orElse(null), List.of(first.getValue(), second.getValue())));
    }

    public Region node(String help) {
        return new VBox(8, Ui.row(Ui.field(t("income.paychecks.perYear"), count)), extras, Ui.wrapped(help, "small"));
    }
}
