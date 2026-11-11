package cat.pessic.app.settings;

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

        Button save = Ui.primary("Desa la configuració");
        save.setOnAction(e -> {
            if (name.getText().isBlank()) { Ui.showError(error, "El perfil necessita un nom."); return; }
            var inc = Ui.amount(income).filter(v -> v.signum() >= 0);
            if (inc.isEmpty()) { Ui.showError(error, "Els ingressos no s'entenen. Escriu-los com 2.400,00."); return; }
            var sav = savings.getText().isBlank() ? java.util.Optional.of(java.math.BigDecimal.ZERO)
                    : Ui.amount(savings).filter(v -> v.signum() >= 0);
            if (sav.isEmpty()) { Ui.showError(error, "L'estalvi automàtic no s'entén."); return; }
            int day;
            try { day = Integer.parseInt(payday.getEditor().getText().strip()); } catch (NumberFormatException ex) { day = payday.getValue(); }
            if (day < 1 || day > 28) { Ui.showError(error, "El dia que cobres ha de ser entre 1 i 28."); return; }
            var paychecks = pay.value();
            if (paychecks.error() != null) { Ui.showError(error, paychecks.error()); return; }
            Ui.showError(error, null);
            int d = day;
            if (ctx.update(pr -> IncomeOps.updatePaychecks(BudgetOps.updateSettings(pr, name.getText(), inc.get(), sav.get(), d,
                    household.getSelectionModel().getSelectedIndex(), members.getValue()), paychecks.value()))) {
                ctx.goToCycle(ctx.calc().cycleContaining(ctx.today()).key());
                Ui.info(ctx.window(), "Configuració desada.");
            }
        });
        HBox saveRow = new HBox(save);
        saveRow.setAlignment(Pos.CENTER_RIGHT);

        var settings = Ui.card(Ui.sectionHead("Configuració del perfil"),
                Ui.row(Ui.field("Nom del perfil", name)),
                Ui.row(Ui.field("Ingressos nets al mes (€)", income), Ui.field("Estalvi automàtic el dia de cobrar (€)", savings),
                        Ui.field("Dia que cobres", payday)),
                Ui.row(Ui.field("Tipus de llar", household), Ui.field("Membres de la llar", members)),
                Ui.wrapped("El tipus de llar, els membres i els ingressos serveixen per comparar-te amb les llars semblants "
                        + "segons l'INE (pestanya «Límits»).", "small"),
                pay.node("Amb 14 pagues, Pessic t'avisa els mesos que toca cobrar la paga extra i et pregunta on la vols posar. "
                        + "Si canvies de feina, actualitza-ho aquí: només afecta d'ara endavant."),
                error, saveRow);

        Button exportJson = Ui.primary("Exporta una còpia (.json)…");
        exportJson.setOnAction(e -> ctx.exportJson());
        Button importBtn = new Button("Importa una còpia (.json)…");
        importBtn.setOnAction(e -> ctx.importBackup());
        Button exportCsv = new Button("Exporta tots els moviments (.csv)…");
        exportCsv.setOnAction(e -> ctx.exportMovementsCsv(false));
        var data = Ui.card(Ui.sectionHead("Dades i còpies de seguretat"),
                Ui.wrapped("Les dades es desen en aquest ordinador, a:", "muted"),
                Ui.label(AppPaths.database().toString(), "num", "small"),
                Ui.wrapped("Fes una còpia de tant en tant i guarda-la al núvol o en un altre disc. La còpia (.json) inclou "
                        + "tots els perfils i es carrega amb «Importa una còpia»; també pots importar les còpies de l'antiga "
                        + "aplicació web. El CSV s'obre amb Excel o LibreOffice.", "small"),
                new HBox(8, exportJson, importBtn, exportCsv));

        SimpleTable copies = new SimpleTable(0, "Còpia", ">Mida", "");
        for (var c : ctx.backups()) {
            Button restore = new Button("Restaura");
            restore.setOnAction(e -> {
                if (Ui.choose(ctx.window(), "Restaura una còpia", "Totes les dades (tots els perfils) tornaran a com estaven el "
                        + Format.date(c.date()) + ". Abans es guardarà una còpia de les dades d'ara a la mateixa carpeta.",
                        0, "Restaura", "Cancel·la") == 0) ctx.restoreBackup(c);
            });
            copies.add(Ui.label((c.beforeRestore() ? "Abans de restaurar · " : "") + Format.date(c.date())
                            + "  (" + c.file().getFileName() + ")"),
                    Ui.label(String.format(java.util.Locale.ROOT, "%.1f kB", c.size() / 1024.0), "num", "muted"), restore);
        }
        Button backupNow = new Button("Fes-ne una ara");
        backupNow.setOnAction(e -> ctx.backupNow());
        var backupCard = Ui.card(Ui.sectionHead("Còpies automàtiques", backupNow),
                Ui.wrapped("Cada dia que obres Pessic se'n fa una còpia automàtica i es guarden les " + Backups.KEEP
                        + " més recents a:", "muted"),
                Ui.label(ctx.backupFolder().toString(), "num", "small"),
                ctx.backups().isEmpty() ? Ui.wrapped("Encara no hi ha cap còpia.", "muted") : copies.node());

        var account = p.ledger().account();
        Button restart = new Button(account == null ? "Dona el saldo" : "Torna a començar el saldo");
        restart.setOnAction(e -> restartBalance(ctx));
        var accountCard = Ui.card(Ui.sectionHead("Compte on cobres la nòmina", restart),
                Ui.wrapped(account == null
                        ? "Encara no has dit quant tens al compte. Amb el saldo, Pessic calcula quant hi tens cada dia i quant "
                        + "et quedarà abans de tornar a cobrar."
                        : "Pessic segueix el saldo des del " + Format.date(account.since()) + " (hi tenies "
                        + Format.eur(account.opening()) + "). Avui calcula que hi tens "
                        + Format.eur(Cash.estimate(p, ctx.today())) + ". Si canvies de banc o el càlcul "
                        + "s'ha desquadrat molt, torna a començar des del saldo d'avui.", "muted"),
                Ui.wrapped("Només cal el compte on cobres. Els comptes d'estalvi o d'inversió no: el que hi envies és una "
                        + "sortida més (l'estalvi automàtic, un pagament fix o una aportació a una guardiola).", "small"));

        Button delete = new Button("Esborra aquest perfil");
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> {
            if (Ui.confirm(ctx.window(), "Esborra el perfil", "Es perdran tots els moviments, pagaments i límits de «"
                    + p.name() + "». No es pot desfer.", "Sí, esborra'l")) ctx.deleteProfile();
        });
        var danger = Ui.card(Ui.sectionHead("Zona perillosa"), new HBox(delete));

        return Ui.page(settings, accountCard, data, backupCard, danger);
    }

    private static void restartBalance(AppContext ctx) {
        boolean first = ctx.profile().ledger().account() == null;
        TextField amount = Ui.amountField(null);
        javafx.scene.layout.VBox form = new javafx.scene.layout.VBox(12,
                Ui.row(Ui.field("Saldo que diu el banc avui (€)", amount)),
                Ui.wrapped(first ? "A partir d'avui Pessic en seguirà el saldo."
                        : "Pessic oblidarà els ajustos anteriors i tornarà a calcular el saldo a partir d'avui.", "small"));
        EditDialog.<java.math.BigDecimal>show(ctx.window(), first ? "Saldo del compte" : "Torna a començar el saldo", form,
                false, "", () -> {
                    var v = Ui.amount(amount);
                    return v.isPresent() ? EditDialog.Check.ok(v.get()) : EditDialog.Check.fail("Escriu el saldo.");
                }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<java.math.BigDecimal>(java.math.BigDecimal v)
                    && ctx.update(q -> AccountOps.setAccountBalance(q, v, ctx.today())) && first) {
                AccountUi.offerFunding(ctx);
            }
        });
    }
}
