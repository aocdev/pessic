package cat.pessic.app.common;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.AmountParser;
import java.math.BigDecimal;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Small UI building blocks shared by all views. */
public final class Ui {
    /** Content never gets wider than this; on bigger windows it stays centred. */
    /** The app's stylesheet, for scenes and dialogs. */
    public static String stylesheet() {
        return Ui.class.getResource("/cat/pessic/app/app.css").toExternalForm();
    }

    public static final double MAX_WIDTH = 1120;

    private Ui() {}

    /** A scrollable page whose content is centred horizontally and grows up to {@link #MAX_WIDTH}. */
    public static ScrollPane page(Node... children) {
        VBox content = new VBox(18, children);
        content.setPadding(new Insets(20, 24, 32, 24));
        content.setMaxWidth(MAX_WIDTH);
        return centred(content);
    }

    static ScrollPane centred(Region content) {
        VBox wrapper = new VBox(content);
        wrapper.setAlignment(Pos.TOP_CENTER);
        wrapper.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        // Always reserve the scrollbar so short and long pages centre identically under the header.
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        return scroll;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    /** A section title with an optional control (usually a button) on the right. */
    public static HBox sectionHead(String title, Node... right) {
        Label h = new Label(title);
        h.getStyleClass().add("h2");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(10, h, spacer);
        box.getChildren().addAll(right);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    public static Label wrapped(String text, String... styles) {
        Label l = label(text, styles);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE); // otherwise layouts may squeeze it to one line with "…"
        return l;
    }

    public static Label badge(String text, String style) {
        return label(text, "status", style);
    }

    public static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("primary");
        return b;
    }

    public static Button link(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("link-button");
        b.setOnAction(e -> action.run());
        return b;
    }

    /** A labelled form field; the control stretches to the column width. */
    public static VBox field(String label, Region control) {
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, label(label, "field-label"), control);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    public static HBox row(Node... children) {
        HBox box = new HBox(12, children);
        box.setAlignment(Pos.BOTTOM_LEFT);
        return box;
    }

    /** Category chooser showing "08 · Informació i comunicacions", with the description as tooltip. */
    public static ComboBox<Category> categoryBox(Category selected) {
        ComboBox<Category> box = new ComboBox<>();
        box.getItems().setAll(Category.ALL);
        box.setValue(selected != null ? selected : Category.FOOD);
        box.setVisibleRowCount(13);
        box.setCellFactory(l -> new CategoryCell(true));
        box.setButtonCell(new CategoryCell(false));
        return box;
    }

    /**
     * A date picker that accepts d/M/yyyy (also with "-" or ".") and never throws on bad input:
     * unreadable text keeps the previous date instead of failing in the JavaFX event thread.
     */
    public static javafx.scene.control.DatePicker datePicker(java.time.LocalDate initial) {
        var picker = new javafx.scene.control.DatePicker(initial);
        var fmt = java.time.format.DateTimeFormatter.ofPattern(t("format.dateInput"));
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(java.time.LocalDate d) {
                return d == null ? "" : d.format(fmt);
            }
            @Override public java.time.LocalDate fromString(String text) {
                if (text == null || text.isBlank()) return picker.getValue();
                try {
                    return java.time.LocalDate.parse(text.strip().replace('-', '/').replace('.', '/'), fmt);
                } catch (java.time.format.DateTimeParseException e) {
                    return picker.getValue();
                }
            }
        });
        // put the (possibly unchanged) date back in the editor when focus leaves, so bad text disappears
        picker.getEditor().focusedProperty().addListener((o, was, is) -> {
            if (!is) picker.getEditor().setText(picker.getConverter().toString(picker.getValue()));
        });
        return picker;
    }

    /** Household size chooser: 1, 2, 3, 4, "5 o més" (INE's grouping). */
    public static ComboBox<Integer> membersBox(Integer selected) {
        ComboBox<Integer> box = new ComboBox<>();
        box.getItems().setAll(1, 2, 3, 4, 5);
        box.setValue(selected == null ? null : Math.min(5, selected));
        box.setPromptText("—");
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer n) {
                return n == null ? "" : n >= 5 ? t("common.ui.membersFiveOrMore") : n == 1 ? t("common.ui.membersOne") : t("common.ui.membersMany", n);
            }
            @Override public Integer fromString(String s) { return null; }
        });
        return box;
    }

    /** Members implied by a household type (index into Catalog.HOUSEHOLDS), or null when it varies. */
    public static Integer membersImpliedBy(int household) {
        return switch (household) {
            case 0, 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            default -> null;
        };
    }

    /** When the household type changes, fill in the members it implies. */
    public static void linkHouseholdToMembers(ComboBox<String> household, ComboBox<Integer> members) {
        household.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            Integer implied = membersImpliedBy(b.intValue());
            if (implied != null) members.setValue(implied);
        });
    }

    /** One line under a category chooser saying what belongs there; follows the selection. */
    public static Label categoryHelp(ComboBox<Category> box) {
        Label help = wrapped("", "small");
        Runnable update = () -> {
            Category c = box.getValue();
            help.setText(c == null ? "" : t("common.ui.categoryHelp", c.description(), c.examples()));
        };
        box.valueProperty().addListener((o, a, b) -> update.run());
        update.run();
        return help;
    }

    /** A category name for tables, with code and guidance in a tooltip. */
    public static Label categoryText(Category c, String... styles) {
        Label l = label(c.displayName(), styles);
        javafx.scene.control.Tooltip t = new javafx.scene.control.Tooltip(
                t("common.ui.categoryTooltipWithName", c.label(), c.description(), c.examples()));
        t.setWrapText(true);
        t.setMaxWidth(420);
        l.setTooltip(t);
        return l;
    }

    private static final class CategoryCell extends javafx.scene.control.ListCell<Category> {
        private final boolean withExamples;

        CategoryCell(boolean withExamples) {
            this.withExamples = withExamples;
        }

        @Override
        protected void updateItem(Category c, boolean empty) {
            super.updateItem(c, empty);
            if (empty || c == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(c.label());
            if (withExamples) {
                javafx.scene.control.Tooltip t = new javafx.scene.control.Tooltip(t("common.ui.categoryTooltip", c.description(), c.examples()));
                t.setWrapText(true);
                t.setMaxWidth(420);
                setTooltip(t);
            }
        }
    }

    public static TextField amountField(BigDecimal value) {
        TextField t = new TextField(value == null ? "" : Format.input(value));
        t.setPromptText("0,00");
        t.getStyleClass().add("num-input");
        return t;
    }

    public static Optional<BigDecimal> amount(TextField t) {
        return AmountParser.parse(t.getText());
    }

    public static Label errorLabel() {
        Label l = label("", "field-error");
        l.setWrapText(true);
        showError(l, null);
        return l;
    }

    public static void showError(Label l, String text) {
        l.setText(text == null ? "" : text);
        l.setVisible(text != null);
        l.setManaged(text != null);
    }

    public static boolean confirm(Window owner, String title, String text, String okText) {
        ButtonType ok = new ButtonType(okText, ButtonType.OK.getButtonData());
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, text, ok, new ButtonType(t("common.cancel"), ButtonType.CANCEL.getButtonData()));
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.getDialogPane().getStylesheets().add(stylesheet());
        ((Button) a.getDialogPane().lookupButton(ok)).getStyleClass().add("primary");
        return a.showAndWait().filter(b -> b == ok).isPresent();
    }

    /**
     * Asks the user to pick one of several actions. The first option is the default (Enter) and
     * styled as primary; {@code dangerIndex} (or -1) is styled as destructive. A "Cancel·la" button
     * is always added. Returns the chosen index, or -1 if cancelled.
     */
    public static int choose(Window owner, String title, String text, int dangerIndex, String... options) {
        ButtonType[] types = new ButtonType[options.length];
        for (int i = 0; i < options.length; i++) {
            types[i] = new ButtonType(options[i], i == 0 ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.OTHER);
        }
        ButtonType cancel = new ButtonType(t("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.NONE, "", types);
        a.getButtonTypes().add(cancel);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        Label body = wrapped(text);
        body.setPrefWidth(520);
        a.getDialogPane().setContent(new VBox(12, label(title, "h2"), body));
        a.getDialogPane().getStylesheets().add(stylesheet());
        for (int i = 0; i < types.length; i++) {
            Button b = (Button) a.getDialogPane().lookupButton(types[i]);
            if (i == 0 && i == dangerIndex) b.getStyleClass().add("danger-primary");
            else if (i == 0) b.getStyleClass().add("primary");
            else if (i == dangerIndex) b.getStyleClass().add("danger");
        }
        var result = a.showAndWait();
        for (int i = 0; i < types.length; i++) if (result.isPresent() && result.get() == types[i]) return i;
        return -1;
    }

    /** "Edita · Esborra" link buttons for a table row. */
    public static HBox rowActions(Runnable edit, Runnable delete) {
        Button e = link(t("common.edit"), edit);
        Button d = link(t("common.delete"), delete);
        d.getStyleClass().add("danger-link");
        HBox box = new HBox(12, e, d);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    public static void error(Window owner, String text) {
        Alert a = new Alert(Alert.AlertType.ERROR, text);
        a.initOwner(owner);
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void info(Window owner, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text);
        a.initOwner(owner);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
