package cat.pessic.app;

import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.history.History;
import cat.pessic.core.profile.Profile;
import cat.pessic.db.backup.Backups;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.UnaryOperator;
import javafx.stage.Window;

/** What the views need from the application: current state and a way to change it. */
public interface AppContext {
    Profile profile();

    YearMonth cycle();

    LocalDate today();

    BudgetCalculator calc();

    CycleSummary summary();

    /** Applies a change to the active profile, saves it and redraws. Invalid changes show an error. */
    boolean update(UnaryOperator<Profile> change);

    void goToCycle(YearMonth cycle);

    /** Opens the import-a-backup flow. */
    void importBackup();

    /** Saves every profile as a backup in the web app's JSON format. */
    void exportJson();

    /** Saves movements as CSV: only the current cycle's, or all of them. */
    void exportMovementsCsv(boolean onlyCurrentCycle);

    /** Saves the current cycle's per-category summary as CSV. */
    void exportEnvelopesCsv();

    /** Saves the history table as CSV. */
    void exportHistoryCsv(java.util.List<History.Row> rows);

    /** Deletes the active profile (after the view has confirmed it). */
    void deleteProfile();

    /** Automatic and manual copies of the database (newest first). */
    java.util.List<Backups.Copy> backups();

    java.nio.file.Path backupFolder();

    /** Makes a copy now and redraws. */
    void backupNow();

    /** Replaces all data with a copy (after the view has confirmed it). */
    void restoreBackup(Backups.Copy copy);

    Window window();
}
