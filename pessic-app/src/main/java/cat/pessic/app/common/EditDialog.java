package cat.pessic.app.common;

import static cat.pessic.core.common.I18n.t;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Modal dialog with Desa / Cancel·la and, when editing, Esborra. The validator returns the
 * value to save, or an error message to show without closing.
 */
public final class EditDialog<T> {

    public sealed interface Result<T> permits Save, Delete {}
    public record Save<T>(T value) implements Result<T> {}
    public record Delete<T>() implements Result<T> {}

    /** Either a value or an error message. */
    public record Check<T>(T value, String error) {
        public static <T> Check<T> ok(T v) { return new Check<>(v, null); }
        public static <T> Check<T> fail(String e) { return new Check<>(null, e); }
    }

    public static <T> Optional<Result<T>> show(Window owner, String title, Region form, boolean canDelete,
                                        String deleteText, Supplier<Check<T>> validator) {
        return show(owner, title, form, canDelete, deleteText, t("common.save"), validator);
    }

    /** @param saveText label of the confirm button ("Desa" by default) */
    public static <T> Optional<Result<T>> show(Window owner, String title, Region form, boolean canDelete,
                                        String deleteText, String saveText, Supplier<Check<T>> validator) {
        Dialog<Result<T>> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle(title);
        d.setHeaderText(null);
        ButtonType save = new ButtonType(saveText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(t("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType delete = new ButtonType(deleteText, ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(save, cancel);
        if (canDelete) d.getDialogPane().getButtonTypes().add(delete);
        Label error = Ui.errorLabel();
        VBox content = new VBox(12, Ui.label(title, "h2"), form, error);
        content.setPrefWidth(Math.max(560, form.getPrefWidth()));
        // long forms scroll instead of growing past the screen
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("dialog-scroll");
        var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        d.getDialogPane().setContent(scroll);
        d.getDialogPane().getStylesheets().add(Ui.stylesheet());
        // measure with the styles applied, then cap at the screen height
        d.getDialogPane().applyCss();
        double width = Math.max(560, form.getPrefWidth());
        scroll.setPrefViewportWidth(width);
        scroll.setPrefViewportHeight(Math.min(content.prefHeight(width) + 8, screen.getHeight() - 220));
        d.setResizable(true);
        ((Button) d.getDialogPane().lookupButton(save)).getStyleClass().add("primary");
        if (canDelete) ((Button) d.getDialogPane().lookupButton(delete)).getStyleClass().add("danger");

        @SuppressWarnings("unchecked") Check<T>[] last = new Check[1];
        d.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, ev -> {
            last[0] = validator.get();
            if (last[0].error() != null) {
                Ui.showError(error, last[0].error());
                d.getDialogPane().getScene().getWindow().sizeToScene();
                ev.consume();
            }
        });
        d.setResultConverter(b -> b == save ? new Save<>(last[0].value()) : b == delete ? new Delete<>() : null);
        return d.showAndWait();
    }

    private EditDialog() {}
}
