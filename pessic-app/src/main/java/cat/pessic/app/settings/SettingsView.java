package cat.pessic.app.settings;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.AppPaths;
import cat.pessic.app.account.AccountUi;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.app.income.PaychecksFields;
import cat.pessic.core.account.AccountOps;
import cat.pessic.core.account.Cash;
import cat.pessic.core.budget.BudgetOps;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.ine.Catalog;
import cat.pessic.db.backup.Backups;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/** "Configuració": profile settings, data location, import and profile deletion. */
public final class SettingsView {
    private SettingsView() {}

    public static Region build(AppContext ctx) {
        var p = ctx.profile();
        var cfg = p.config();
        TextField name = new TextField(p.name());
        TextField income = Ui.amountField(cfg.income());
        TextField savings = Ui.amountField(cfg.savings());
        Spinner<Integer> payday = new Spinner<>(1, 28, cfg.payday());
        payday.setEditable(true);
        ComboBox<String> household = new ComboBox<>();
        household.getItems().setAll(Catalog.HOUSEHOLDS);
        household.getSelectionModel().select(cfg.household());
        ComboBox<Integer> members = Ui.membersBox(cfg.members());
        Ui.linkHouseholdToMembers(household, members);
        PaychecksFields pay = new PaychecksFields(cfg.paychecks());
        Label error = Ui.errorLabel();

        Button save = Ui.primary(t("settings.save"));
        save.setOnAction(e -> {
            if (name.getText().isBlank()) { Ui.showError(error, t("settings.errorName")); return; }
            var inc = Ui.amount(income).filter(v -> v.signum() >= 0);
            if (inc.isEmpty()) { Ui.showError(error, t("settings.errorIncome")); return; }
            var sav = savings.getText().isBlank() ? java.util.Optional.of(java.math.BigDecimal.ZERO)
                    : Ui.amount(savings).filter(v -> v.signum() >= 0);
            if (sav.isEmpty()) { Ui.showError(error, t("settings.errorSavings")); return; }
            int day;
            try { day = Integer.parseInt(payday.getEditor().getText().strip()); } catch (NumberFormatException ex) { day = payday.getValue(); }
            if (day < 1 || day > 28) { Ui.showError(error, t("settings.errorPayday")); return; }
            var paychecks = pay.value();
            if (paychecks.error() != null) { Ui.showError(error, paychecks.error()); return; }
            Ui.showError(error, null);
            int d = day;
            if (ctx.update(pr -> IncomeOps.updatePaychecks(BudgetOps.updateSettings(pr, name.getText(), inc.get(), sav.get(), d,
                    household.getSelectionModel().getSelectedIndex(), members.getValue()), paychecks.value()))) {
                ctx.goToCycle(ctx.calc().cycleContaining(ctx.today()).key());
                Ui.info(ctx.window(), t("settings.saved"));
            }
        });
        HBox saveRow = new HBox(save);
        saveRow.setAlignment(Pos.CENTER_RIGHT);

        var settings = Ui.card(Ui.sectionHead(t("settings.profile.title")),
                Ui.row(Ui.field(t("settings.profile.name"), name)),
                Ui.row(Ui.field(t("common.netMonthlyIncome"), income), Ui.field(t("settings.profile.automaticSavings"), savings),
                        Ui.field(t("common.payday"), payday)),
                Ui.row(Ui.field(t("common.householdType"), household), Ui.field(t("common.householdMembers"), members)),
                Ui.wrapped(t("settings.profile.householdHint"), "small"),
                pay.node(t("settings.profile.paychecksHint")),
                error, saveRow);

        Button exportJson = Ui.primary(t("settings.data.exportJson"));
        exportJson.setOnAction(e -> ctx.exportJson());
        Button importBtn = new Button(t("settings.data.importJson"));
        importBtn.setOnAction(e -> ctx.importBackup());
        Button exportCsv = new Button(t("settings.data.exportCsv"));
        exportCsv.setOnAction(e -> ctx.exportMovementsCsv(false));
        var data = Ui.card(Ui.sectionHead(t("settings.data.title")),
                Ui.wrapped(t("settings.data.location"), "muted"),
                Ui.label(AppPaths.database().toString(), "num", "small"),
                Ui.wrapped(t("settings.data.hint"), "small"),
                new HBox(8, exportJson, importBtn, exportCsv));

        SimpleTable copies = new SimpleTable(0, t("settings.backups.copy"), ">" + t("settings.backups.size"), "");
        for (var c : ctx.backups()) {
            Button restore = new Button(t("settings.backups.restore"));
            restore.setOnAction(e -> {
                if (Ui.choose(ctx.window(), t("settings.backups.restoreTitle"), t("settings.backups.restoreQuestion", Format.date(c.date())),
                        0, t("settings.backups.restore"), t("common.cancel")) == 0) ctx.restoreBackup(c);
            });
            copies.add(Ui.label((c.beforeRestore() ? t("settings.backups.beforeRestore") : "") + Format.date(c.date())
                            + "  (" + c.file().getFileName() + ")"),
                    Ui.label(String.format(java.util.Locale.ROOT, "%.1f kB", c.size() / 1024.0), "num", "muted"), restore);
        }
        Button backupNow = new Button(t("settings.backups.makeNow"));
        backupNow.setOnAction(e -> ctx.backupNow());
        var backupCard = Ui.card(Ui.sectionHead(t("settings.backups.title"), backupNow),
                Ui.wrapped(t("settings.backups.hint", Backups.KEEP), "muted"),
                Ui.label(ctx.backupFolder().toString(), "num", "small"),
                ctx.backups().isEmpty() ? Ui.wrapped(t("settings.backups.none"), "muted") : copies.node());

        var account = p.ledger().account();
        Button restart = new Button(account == null ? t("settings.account.giveBalance") : t("settings.account.restart"));
        restart.setOnAction(e -> restartBalance(ctx));
        var accountCard = Ui.card(Ui.sectionHead(t("settings.account.title"), restart),
                Ui.wrapped(account == null
                        ? t("settings.account.noBalance")
                        : t("settings.account.tracking", Format.date(account.since()), Format.eur(account.opening()), Format.eur(Cash.estimate(p, ctx.today()))), "muted"),
                Ui.wrapped(t("settings.account.hint"), "small"));

        Button delete = new Button(t("settings.danger.deleteProfile"));
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> {
            if (Ui.confirm(ctx.window(), t("settings.danger.deleteTitle"), t("settings.danger.deleteQuestion", p.name()), t("settings.danger.deleteConfirm"))) ctx.deleteProfile();
        });
        var danger = Ui.card(Ui.sectionHead(t("settings.danger.title")), new HBox(delete));

        return Ui.page(settings, accountCard, data, backupCard, danger);
    }

    private static void restartBalance(AppContext ctx) {
        boolean first = ctx.profile().ledger().account() == null;
        TextField amount = Ui.amountField(null);
        javafx.scene.layout.VBox form = new javafx.scene.layout.VBox(12,
                Ui.row(Ui.field(t("common.bankBalanceToday"), amount)),
                Ui.wrapped(first ? t("settings.account.giveHint")
                        : t("settings.account.restartHint"), "small"));
        EditDialog.<java.math.BigDecimal>show(ctx.window(), first ? t("settings.account.balanceTitle") : t("settings.account.restart"), form,
                false, "", () -> {
                    var v = Ui.amount(amount);
                    return v.isPresent() ? EditDialog.Check.ok(v.get()) : EditDialog.Check.fail(t("settings.account.errorBalance"));
                }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<java.math.BigDecimal>(java.math.BigDecimal v)
                    && ctx.update(q -> AccountOps.setAccountBalance(q, v, ctx.today())) && first) {
                AccountUi.offerFunding(ctx);
            }
        });
    }
}
