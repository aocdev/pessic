package cat.pessic.app.income;

import cat.pessic.app.common.Ui;
import cat.pessic.core.income.Income;
import cat.pessic.core.savings.Pot;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "On van aquests diners?": where an extra income goes. Nothing is preselected, so the user decides
 * every time (this month's budget, the annual reserve, a goal or savings).
 */
public final class DestinationPicker {

    final ToggleGroup group = new ToggleGroup();
    final ComboBox<Pot> goal = new ComboBox<>();
    private final RadioButton toGoal;

    public DestinationPicker(List<Pot> goals, Income.Destination preselected, String goalId) {
        goal.getItems().setAll(goals);
        goal.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Pot p) { return p == null ? "" : p.name(); }
            @Override public Pot fromString(String s) { return null; }
        });
        goal.setPromptText(goals.isEmpty() ? "No en tens cap" : "Tria-la");
        goals.stream().filter(g -> g.id().equals(goalId)).findFirst().ifPresent(goal::setValue);
        if (goal.getValue() == null && goals.size() == 1) goal.setValue(goals.get(0));
        toGoal = radio("Una guardiola", Income.Destination.GOAL);
        toGoal.setDisable(goals.isEmpty());
        goal.disableProperty().bind(toGoal.selectedProperty().not());
        radio("El pressupost d'aquest mes", Income.Destination.BUDGET);
        radio("La reserva d'anuals", Income.Destination.RESERVE);
        radio("Estalvi (no el toco)", Income.Destination.SAVINGS);
        if (preselected != null) {
            group.getToggles().stream().filter(t -> t.getUserData() == preselected).findFirst()
                    .ifPresent(t -> t.setSelected(true));
        }
    }

    private RadioButton radio(String text, Income.Destination d) {
        RadioButton r = new RadioButton(text);
        r.setUserData(d);
        r.setToggleGroup(group);
        return r;
    }

    public Income.Destination value() {
        return group.getSelectedToggle() == null ? null : (Income.Destination) group.getSelectedToggle().getUserData();
    }

    public String goalId() {
        return value() == Income.Destination.GOAL && goal.getValue() != null ? goal.getValue().id() : null;
    }

    /** An error message, or null when the choice is complete. */
    public String check() {
        if (value() == null) return "Tria on van aquests diners.";
        if (value() == Income.Destination.GOAL && goal.getValue() == null) return "Tria la guardiola.";
        return null;
    }

    public Region node() {
        RadioButton budget = (RadioButton) find(Income.Destination.BUDGET);
        RadioButton reserve = (RadioButton) find(Income.Destination.RESERVE);
        RadioButton savings = (RadioButton) find(Income.Destination.SAVINGS);
        goal.setPrefWidth(180);
        HBox goalBox = new HBox(6, toGoal, goal);
        goalBox.setAlignment(Pos.CENTER_LEFT);
        FlowPane options = new FlowPane(18, 8, budget, reserve, goalBox, savings);
        options.setAlignment(Pos.CENTER_LEFT);
        options.setPrefWrapLength(600);
        return new VBox(6, Ui.label("On van aquests diners?", "field-label"), options);
    }

    private javafx.scene.control.Toggle find(Income.Destination d) {
        return group.getToggles().stream().filter(t -> t.getUserData() == d).findFirst().orElseThrow();
    }
}
