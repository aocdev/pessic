package cat.pessic.app;

/**
 * Plain main class. JavaFX refuses to start from a fat/classpath jar whose main class extends
 * {@code Application}, so this indirection lets {@code java -jar} and jpackage work.
 */
public final class Launcher {
    public static void main(String[] args) {
        PessicApp.main(args);
    }
}
