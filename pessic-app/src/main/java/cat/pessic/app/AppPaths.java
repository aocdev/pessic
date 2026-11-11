package cat.pessic.app;

import java.nio.file.Path;

/**
 * Where the database lives, following each OS's convention.
 * Override with {@code -Dpessic.db=/path} or the {@code PESSIC_DB} environment variable.
 */
public final class AppPaths {
    private AppPaths() {}

    public static Path database() {
        String override = System.getProperty("pessic.db");
        if (override == null || override.isBlank()) override = System.getenv("PESSIC_DB");
        if (override != null && !override.isBlank()) return Path.of(override);
        String os = System.getProperty("os.name", "").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        Path dir;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            dir = (appData != null ? Path.of(appData) : home.resolve("AppData/Roaming")).resolve("Pessic");
        } else if (os.contains("mac")) {
            dir = home.resolve("Library/Application Support/Pessic");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            dir = (xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".local/share")).resolve("pessic");
        }
        return dir.resolve("pessic.db");
    }
}
