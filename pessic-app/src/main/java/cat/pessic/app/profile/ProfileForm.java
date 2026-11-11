package cat.pessic.app.profile;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.common.Ui;
import cat.pessic.app.income.PaychecksFields;
import cat.pessic.core.account.AccountOps;
import cat.pessic.core.common.AmountParser;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.ine.Catalog;
import cat.pessic.core.profile.NewProfile;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "Crea el teu perfil": the first-run form (name, net income, payday, household type,
 * optional INE starting limits), mirroring the web app's welcome screen.
 */
public final class ProfileForm {

    private final TextField name = new TextField();
    private final TextField income = new TextField();
    private final Spinner<Integer> payday = new Spinner<>(1, 28, 1);
    private final ComboBox<String> household = new ComboBox<>();
    private final ComboBox<Integer> members = Ui.membersBox(Ui.membersImpliedBy(NewProfile.DEFAULT_HOUSEHOLD));
    private final PaychecksFields paychecks = new PaychecksFields(Paychecks.TWELVE);
    private final Label paychecksError = error();
    private final TextField balance = new TextField();
    private final Label balanceError = error();
    private final java.time.LocalDate today;

    /** @param today the day the account balance (if given) refers to */
    public ProfileForm(java.time.LocalDate today) {
        this.today = today;
    }
    private final CheckBox useIne = new CheckBox(
            t("profile.form.useIneLimits"));
    private final Label nameError = error();
    private final Label incomeError = error();

    /**
     * @param firstRun  true when there are no profiles yet: shows the intro text and hides "Cancel·la"
     * @param onCreate  receives the new profile when the form is valid
     * @param onImport  opens the import-a-backup flow
     * @param onCancel  closes the form (only used when not first run)
     */
    public Region build(boolean firstRun, Consumer<Profile> onCreate, Runnable onImport, Runnable onCancel) {
        VBox page = new VBox(18);
        page.setPadding(new Insets(40, 24, 40, 24));
        page.setAlignment(Pos.TOP_CENTER);
        page.setMaxWidth(640);

        if (firstRun) {
            Label eyebrow = new Label("PESSIC");
            eyebrow.getStyleClass().add("eyebrow");
            Label title = new Label(t("profile.form.tagline"));
            title.getStyleClass().add("h1");
            Label intro = new Label(t("profile.form.intro"));
            intro.setWrapText(true);
            intro.getStyleClass().add("muted");
            VBox head = new VBox(6, eyebrow, title, intro);
            page.getChildren().add(head);
        }

        page.getChildren().add(card(firstRun, onCreate, onCancel));

        Hyperlink importLink = new Hyperlink(t("profile.form.importBackup"));
        importLink.setOnAction(e -> onImport.run());
        page.getChildren().add(importLink);

        HBox center = new HBox(page);
        center.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(page, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        Platform.runLater(name::requestFocus);
        return scroll;
    }

    private VBox card(boolean firstRun, Consumer<Profile> onCreate, Runnable onCancel) {
        Label h = new Label(firstRun ? t("profile.form.firstTitle") : t("profile.form.newTitle"));
        h.getStyleClass().add("h2");

        name.setPromptText(t("profile.form.namePrompt"));
        income.setPromptText("2.400,00");
        payday.setEditable(true);
        payday.setPrefWidth(110);
        household.getItems().setAll(Catalog.HOUSEHOLDS);
        household.getSelectionModel().select(NewProfile.DEFAULT_HOUSEHOLD);
        household.setMaxWidth(Double.MAX_VALUE);
        name.textProperty().addListener((o, a, b) -> show(nameError, null));
        income.textProperty().addListener((o, a, b) -> show(incomeError, null));
        Ui.linkHouseholdToMembers(household, members);
        useIne.setSelected(true);
        useIne.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(60);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(40);
        grid.getColumnConstraints().addAll(c1, c2);

        grid.add(field(t("common.name"), name), 0, 0, 2, 1);
        grid.add(nameError, 0, 1, 2, 1);
        grid.add(field(t("common.netMonthlyIncome"), income), 0, 2);
        grid.add(field(t("common.payday"), payday), 1, 2);
        grid.add(incomeError, 0, 3, 2, 1);
        grid.add(field(t("common.householdType"), household), 0, 4);
        grid.add(field(t("common.householdMembers"), members), 1, 4);
        javafx.scene.layout.Region pay = paychecks.node(t("profile.form.paychecksHint"));
        GridPane.setMargin(pay, new Insets(12, 0, 0, 0));
        grid.add(pay, 0, 5, 2, 1);
        grid.add(paychecksError, 0, 6, 2, 1);
        balance.setPromptText(t("profile.form.balancePrompt"));
        balance.textProperty().addListener((o, a, b) -> show(balanceError, null));
        VBox balanceField = field(t("profile.form.balance"), balance);
        Label balanceHelp = new Label(t("profile.form.balanceHint"));
        balanceHelp.getStyleClass().add("small");
        balanceHelp.setWrapText(true);
        grid.add(balanceField, 0, 7);
        grid.add(balanceHelp, 0, 8, 2, 1);
        grid.add(balanceError, 0, 9, 2, 1);
        GridPane.setMargin(useIne, new Insets(12, 0, 0, 0));
        grid.add(useIne, 0, 10, 2, 1);

        Label paydayHelp = new Label(t("profile.form.cycleHint"));
        paydayHelp.getStyleClass().add("small");
        paydayHelp.setWrapText(true);
        GridPane.setMargin(paydayHelp, new Insets(8, 0, 0, 0));
        grid.add(paydayHelp, 0, 11, 2, 1);

        Button start = new Button(firstRun ? t("profile.form.start") : t("profile.form.create"));
        start.getStyleClass().add("primary");
        start.setDefaultButton(true);
        start.setOnAction(e -> submit().ifPresent(onCreate));
        HBox buttons = new HBox(8, start);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (!firstRun) {
            Button cancel = new Button(t("common.cancel"));
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> onCancel.run());
            buttons.getChildren().add(0, cancel);
        }

        VBox card = new VBox(14, h, grid, buttons);
        card.getStyleClass().add("card");
        return card;
    }

    /** Validates the form; shows the errors inline and returns empty if anything is wrong. */
    private Optional<Profile> submit() {
        boolean ok = true;
        String n = name.getText() == null ? "" : name.getText().strip();
        if (n.isEmpty()) {
            show(nameError, t("profile.form.errorName"));
            ok = false;
        } else {
            show(nameError, null);
        }
        Optional<BigDecimal> inc = AmountParser.parse(income.getText()).filter(v -> v.signum() >= 0);
        if (inc.isEmpty()) {
            show(incomeError, t("profile.form.errorIncome"));
            ok = false;
        } else {
            show(incomeError, null);
        }
        var pay = paychecks.value();
        show(paychecksError, pay.error());
        if (pay.error() != null) ok = false;
        Optional<BigDecimal> bal = balance.getText() == null || balance.getText().isBlank() ? Optional.empty()
                : AmountParser.parse(balance.getText());
        boolean badBalance = balance.getText() != null && !balance.getText().isBlank() && bal.isEmpty();
        show(balanceError, badBalance ? t("profile.form.errorBalance") : null);
        if (badBalance) ok = false;
        if (!ok) {
            (n.isEmpty() ? name : inc.isEmpty() ? income : badBalance ? balance : paychecks.amount).requestFocus();
            return Optional.empty();
        }
        int day = Math.min(28, Math.max(1, paydayValue()));
        int llar = Math.max(0, household.getSelectionModel().getSelectedIndex());
        Profile created = NewProfile.create(n, inc.get(), day, llar, members.getValue(), pay.value(), useIne.isSelected());
        return Optional.of(bal.isPresent() ? AccountOps.setAccountBalance(created, bal.get(), today) : created);
    }

    /** Reads the spinner, accepting text typed but not yet committed with Enter. */
    private int paydayValue() {
        try {
            return Integer.parseInt(payday.getEditor().getText().strip());
        } catch (NumberFormatException e) {
            return payday.getValue();
        }
    }

    private static VBox field(String label, Region control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, l, control);
        GridPane.setMargin(box, new Insets(8, 0, 0, 0));
        return box;
    }

    private static Label error() {
        Label l = new Label();
        l.getStyleClass().add("field-error");
        l.setManaged(false);
        l.setVisible(false);
        return l;
    }

    private static void show(Label error, String text) {
        error.setText(text == null ? "" : text);
        error.setVisible(text != null);
        error.setManaged(text != null);
    }
}
