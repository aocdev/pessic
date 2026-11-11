package cat.pessic.app;

import static cat.pessic.core.common.I18n.plural;
import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.budget.LimitsView;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.Ui;
import cat.pessic.app.dashboard.DashboardView;
import cat.pessic.app.expense.MovementsView;
import cat.pessic.app.history.HistoryView;
import cat.pessic.app.payment.AnnualsView;
import cat.pessic.app.payment.PlanView;
import cat.pessic.app.profile.ProfileForm;
import cat.pessic.app.savings.SavingsView;
import cat.pessic.app.settings.SettingsView;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.export.CsvExport;
import cat.pessic.core.history.History;
import cat.pessic.core.profile.Profile;
import cat.pessic.db.DataAccessException;
import cat.pessic.db.Database;
import cat.pessic.db.backup.BackupExporter;
import cat.pessic.db.backup.BackupImporter;
import cat.pessic.db.backup.Backups;
import cat.pessic.db.profile.ProfileRepository;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Application shell: header with cycle navigation and profile picker, section navigation, current view. */
public class PessicApp extends Application implements AppContext {

    private enum Section {
        SUMMARY(t("app.nav.summary"), DashboardView::build),
        MOVEMENTS(t("common.movements"), MovementsView::build),
        PLAN(t("app.nav.fixedPayments"), PlanView::build),
        ANNUALS(t("app.nav.annuals"), AnnualsView::build),
        SAVINGS(t("common.savings"), SavingsView::build),
        LIMITS(t("app.nav.limits"), LimitsView::build),
        HISTORY(t("app.nav.history"), HistoryView::build),
        SETTINGS(t("app.nav.settings"), SettingsView::build);

        final String title;
        final Function<AppContext, Region> view;

        Section(String title, Function<AppContext, Region> view) {
            this.title = title;
            this.view = view;
        }
    }

    private Database db;
    private ProfileRepository repo;
    private Backups backups;
    private Stage stage;
    private Profile profile;
    private YearMonth cycle;
    private Section section = Section.SUMMARY;
    private BudgetCalculator calc;
    private CycleSummary summary;
    private ScrollPane currentScroll;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Locale.setDefault(Format.CA); // Catalan month and day names in date pickers
        this.stage = stage;
        db = Database.open(AppPaths.database());
        repo = new ProfileRepository(db);
        backups = new Backups(AppPaths.database());
        try {
            backups.daily(db, today());
        } catch (RuntimeException ex) {
            // never block the app because a copy failed; Configuració shows the copies that exist
            System.err.println("Pessic: daily backup failed: " + ex.getMessage());
        }

        stage.setTitle("Pessic");
        var icon = PessicApp.class.getResourceAsStream("icon.png");
        if (icon != null) stage.getIcons().add(new Image(icon));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(new Scene(new BorderPane(), 1180, 820));
        stage.getScene().getStylesheets().add(Ui.stylesheet());
        loadActiveProfile();
        stage.show();
        db.lastBackup().ifPresent(b -> Platform.runLater(() -> Ui.info(stage,
                t("app.upgradedToCoicop", b))));
    }

    @Override
    public void stop() {
        if (db != null) db.close();
    }

    // ------------------------------------------------------------------ state

    private void loadActiveProfile() {
        profile = repo.activeProfileId().flatMap(repo::find)
                .or(() -> repo.list().stream().findFirst().flatMap(r -> repo.find(r.id())))
                .orElse(null);
        cycle = null;
        render();
    }

    private void render() {
        if (profile == null) {
            showProfileForm(true);
            return;
        }
        double scroll = currentScroll != null ? currentScroll.getVvalue() : 0;
        calc = BudgetCalculator.of(profile);
        if (cycle == null) cycle = calc.cycleContaining(today()).key();
        summary = calc.summarize(cycle, profile, today());

        Region view = section.view.apply(this);
        currentScroll = view instanceof ScrollPane sp ? sp : null;
        if (currentScroll != null && scroll > 0) Platform.runLater(() -> currentScroll.setVvalue(scroll));

        BorderPane root = new BorderPane(view);
        root.setTop(header());
        stage.getScene().setRoot(root);
    }

    private void showSection(Section s) {
        section = s;
        currentScroll = null;
        render();
    }

    private void showProfileForm(boolean firstRun) {
        currentScroll = null;
        stage.getScene().setRoot(new ProfileForm(today()).build(firstRun, this::createProfile, this::importBackup, this::render));
    }

    private void createProfile(Profile created) {
        try {
            repo.save(created);
            repo.setActiveProfile(created.id());
            section = Section.SUMMARY;
            loadActiveProfile();
        } catch (RuntimeException ex) {
            Ui.error(stage, t("app.errorSavingProfile", ex.getMessage()));
        }
    }

    // ------------------------------------------------------------------ AppContext

    @Override public Profile profile() { return profile; }
    @Override public YearMonth cycle() { return cycle; }
    /**
     * For trying the app on another day (month close, extra paychecks): {@code PESSIC_TODAY=2026-12-01}
     * or {@code -Dpessic.today=2026-12-01}. Use it with a test database ({@code PESSIC_DB}).
     */
    private static final LocalDate FIXED_TODAY = fixedToday();

    private static LocalDate fixedToday() {
        String v = System.getProperty("pessic.today");
        if (v == null || v.isBlank()) v = System.getenv("PESSIC_TODAY");
        return v == null || v.isBlank() ? null : LocalDate.parse(v.strip());
    }

    @Override public LocalDate today() { return FIXED_TODAY != null ? FIXED_TODAY : LocalDate.now(); }
    @Override public BudgetCalculator calc() { return calc; }
    @Override public CycleSummary summary() { return summary; }
    @Override public Window window() { return stage; }

    @Override
    public boolean update(UnaryOperator<Profile> change) {
        try {
            Profile next = change.apply(profile);
            if (next != profile) {
                repo.save(next);
                profile = next;
            }
            render();
            return true;
        } catch (IllegalArgumentException ex) {
            Ui.error(stage, t("app.errorSaving", ex.getMessage()));
        } catch (DataAccessException ex) {
            Ui.error(stage, t("app.errorDatabase", ex.getMessage()));
        }
        return false;
    }

    @Override
    public void goToCycle(YearMonth target) {
        cycle = target;
        render();
    }

    @Override
    public void importBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle(t("app.importWebAppBackup"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(t("app.webAppBackupFilter"), "*.json"));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try (InputStream in = Files.newInputStream(f.toPath())) {
            var backup = new BackupImporter().importInto(in, repo);
            section = Section.SUMMARY;
            loadActiveProfile();
            Ui.info(stage, t("app.importedProfiles", backup.profiles().size()));
            offerToRemoveEmptyProfiles(backup);
        } catch (Exception ex) {
            Ui.error(stage, t("app.errorImporting", ex.getMessage()));
        }
    }

    @Override
    public void exportJson() {
        File f = chooseSaveFile(t("app.exportBackup"), "pessic-copia-" + today() + ".json",
                t("app.backupFilter"), "*.json");
        if (f == null) return;
        String active = profile.id();
        List<Profile> all = new ArrayList<>();
        repo.list().forEach(r -> repo.find(r.id()).ifPresent(all::add));
        if (writeFile(f, out -> new BackupExporter().write(out, all, active))) {
            Ui.info(stage, t("app.backupSaved", plural("app.profileCount", all.size()), f));
        }
    }

    @Override
    public void exportMovementsCsv(boolean onlyCurrentCycle) {
        String name = "pessic-moviments-" + (onlyCurrentCycle ? cycle.toString() : "tots-" + today()) + ".csv";
        File f = chooseSaveFile(t("app.exportMovements"), name, t("app.csvFilter"), "*.csv");
        if (f == null) return;
        var moves = onlyCurrentCycle
                ? profile.movements().stream().filter(m -> summary.cycle().contains(m.date())).toList()
                : profile.movements();
        var incomes = onlyCurrentCycle
                ? profile.incomes().stream().filter(i -> summary.cycle().contains(i.date())).toList()
                : profile.incomes();
        if (writeFile(f, out -> CsvExport.movements(out, moves, incomes, profile.ledger().pots(), profile.config().payday()))) {
            Ui.info(stage, t("app.movementsExported", (moves.size() + incomes.size()), f));
        }
    }

    @Override
    public void exportEnvelopesCsv() {
        File f = chooseSaveFile(t("app.exportEnvelopes"), "pessic-sobres-" + cycle + ".csv",
                t("app.csvFilter"), "*.csv");
        if (f == null) return;
        if (writeFile(f, out -> CsvExport.envelopes(out, summary))) {
            Ui.info(stage, t("app.envelopesExported", Format.month(cycle), f));
        }
    }

    @Override
    public void exportHistoryCsv(List<History.Row> rows) {
        File f = chooseSaveFile(t("app.exportHistory"), "pessic-historic-" + today() + ".csv",
                t("app.csvFilter"), "*.csv");
        if (f == null) return;
        if (writeFile(f, out -> CsvExport.history(out, rows))) Ui.info(stage, t("app.historyExported", f));
    }

    private interface FileWriterAction { void write(OutputStream out) throws Exception; }

    private File chooseSaveFile(String title, String suggestedName, String filterName, String pattern) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialFileName(suggestedName);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterName, pattern));
        File home = new File(System.getProperty("user.home"));
        if (home.isDirectory()) fc.setInitialDirectory(home);
        File f = fc.showSaveDialog(stage);
        if (f == null) return null;
        String ext = pattern.substring(1); // ".json" / ".csv"
        return f.getName().toLowerCase().endsWith(ext) ? f : new File(f.getParentFile(), f.getName() + ext);
    }

    /** Writes to a temporary file first, so a failed export never leaves a half-written file behind. */
    private boolean writeFile(File target, FileWriterAction action) {
        Path dest = target.toPath();
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dest.toAbsolutePath().getParent(), ".pessic-", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                action.write(out);
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception ex) {
            Ui.error(stage, t("app.errorSavingFile", ex.getMessage()));
            return false;
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            }
        }
    }

    /** After an import, empty profiles that were not part of it are usually leftovers of the first-run form. */
    private void offerToRemoveEmptyProfiles(BackupImporter.Backup backup) {
        var imported = backup.profiles().stream().map(Profile::id).toList();
        var leftovers = repo.list().stream().filter(r -> r.empty() && !imported.contains(r.id())).toList();
        if (leftovers.isEmpty()) return;
        String names = String.join(", ", leftovers.stream().map(r -> "«" + r.name() + "»").toList());
        if (Ui.choose(stage, t("app.emptyProfilesTitle"), t("app.emptyProfilesQuestion", plural("app.emptyProfiles", leftovers.size()), names),
                -1, t("app.deleteThem"), t("app.keepThem")) == 0) {
            leftovers.forEach(r -> repo.delete(r.id()));
            loadActiveProfile();
        }
    }

    @Override public List<Backups.Copy> backups() { return backups.list(); }
    @Override public Path backupFolder() { return backups.folder(); }

    @Override
    public void backupNow() {
        try {
            Path f = backups.now(db, java.time.LocalDateTime.now());
            render();
            Ui.info(stage, t("app.backupMade", f));
        } catch (RuntimeException ex) {
            Ui.error(stage, t("app.errorBackup", ex.getMessage()));
        }
    }

    @Override
    public void restoreBackup(Backups.Copy copy) {
        try {
            db = backups.restore(db, copy.file());
        } catch (RuntimeException ex) {
            // restore() only closes the database once the copy has been checked; reopen it if it was closed
            try {
                db.schemaVersion();
            } catch (Exception closed) {
                db = Database.open(AppPaths.database());
            }
            repo = new ProfileRepository(db);
            Ui.error(stage, t("app.errorRestoring", ex.getMessage()));
            return;
        }
        repo = new ProfileRepository(db);
        section = Section.SUMMARY;
        loadActiveProfile();
        Ui.info(stage, t("app.restored", Format.date(copy.date())));
    }

    @Override
    public void deleteProfile() {
        repo.delete(profile.id());
        repo.list().stream().findFirst().ifPresent(r -> repo.setActiveProfile(r.id()));
        section = Section.SUMMARY;
        loadActiveProfile();
    }

    // ------------------------------------------------------------------ header

    private Node header() {
        Label brand = Ui.label("PESSIC", "eyebrow");

        ComboBox<ProfileRepository.ProfileRef> picker = new ComboBox<>();
        picker.getItems().setAll(repo.list());
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ProfileRepository.ProfileRef r) {
                return r == null ? "" : r.name() + (r.empty() ? t("app.emptySuffix") : "");
            }
            @Override public ProfileRepository.ProfileRef fromString(String s) { return null; }
        });
        picker.getItems().stream().filter(r -> r.id().equals(profile.id())).findFirst().ifPresent(picker::setValue);
        picker.setOnAction(e -> {
            var r = picker.getValue();
            if (r != null && !r.id().equals(profile.id())) {
                repo.setActiveProfile(r.id());
                loadActiveProfile();
            }
        });
        Button newProfile = new Button(t("app.newProfile"));
        newProfile.setOnAction(e -> showProfileForm(false));
        HBox top = new HBox(8, brand, spacer(), Ui.label(t("app.profile"), "muted"), picker, newProfile);
        top.setAlignment(Pos.CENTER_LEFT);

        Button prev = new Button("‹");
        prev.getStyleClass().add("nav-arrow");
        prev.setOnAction(e -> goToCycle(cycle.minusMonths(1)));
        Button next = new Button("›");
        next.getStyleClass().add("nav-arrow");
        next.setOnAction(e -> goToCycle(cycle.plusMonths(1)));
        Button now = Ui.link(t("app.today"), () -> goToCycle(calc.cycleContaining(today()).key()));
        Label title = Ui.label(Format.month(cycle), "h1");
        title.setMinWidth(230);
        title.setAlignment(Pos.CENTER);
        var c = summary.cycle();
        Label range = Ui.label(t("app.cycleRange", c.start().getDayOfMonth(), Format.ofMonth(c.start().getMonthValue()), c.end().getDayOfMonth(), Format.ofMonth(c.end().getMonthValue()), c.end().getYear()), "muted");
        HBox cycleNav = new HBox(8, prev, title, next, now, spacer(), range);
        cycleNav.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup group = new ToggleGroup();
        HBox nav = new HBox(4);
        nav.getStyleClass().add("nav");
        for (Section s : Section.values()) {
            ToggleButton b = new ToggleButton(s.title);
            b.setToggleGroup(group);
            b.getStyleClass().add("nav-item");
            b.setSelected(s == section);
            b.setOnAction(e -> {
                if (s == section) b.setSelected(true); // keep one selected
                else showSection(s);
            });
            nav.getChildren().add(b);
        }

        VBox inner = new VBox(12, top, cycleNav, nav);
        inner.setPadding(new Insets(16, 24, 0, 24));
        inner.setMaxWidth(Ui.MAX_WIDTH);
        VBox outer = new VBox(inner);
        outer.setAlignment(Pos.TOP_CENTER);
        outer.getStyleClass().add("app-header");
        return outer;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
