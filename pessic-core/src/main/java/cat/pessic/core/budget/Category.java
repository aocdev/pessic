package cat.pessic.core.budget;

import static cat.pessic.core.common.I18n.t;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The 13 spending groups of COICOP 2018, the classification INE uses in the Encuesta de
 * Presupuestos Familiares (EPF) from 2024 on. The two-digit {@link #code} is stable and is what
 * the database and backups store; names are for display only.
 */
public enum Category {
    FOOD("01", "Alimentos y bebidas no alcohólicas", "Alimentació"),
    ALCOHOL_TOBACCO("02", "Bebidas alcohólicas, tabaco y estupefacientes", "Alcohol i tabac"),
    CLOTHING("03", "Vestido y calzado", "Vestit i calçat"),
    HOUSING("04", "Vivienda, agua, electricidad, gas y otros combustibles", "Habitatge, aigua, llum i gas"),
    FURNISHINGS("05", "Muebles, artículos del hogar y artículos para el mantenimiento corriente del hogar", "Mobles i llar"),
    HEALTH("06", "Sanidad", "Sanitat"),
    TRANSPORT("07", "Transporte", "Transport"),
    INFO_COMMUNICATION("08", "Información y comunicaciones", "Comunicacions"),
    RECREATION("09", "Actividades recreativas, deporte y cultura", "Oci, esport i cultura"),
    EDUCATION("10", "Servicios de educación", "Educació"),
    RESTAURANTS("11", "Restaurantes y servicios de alojamiento", "Restaurants i hotels"),
    INSURANCE_FINANCE("12", "Seguros y servicios financieros", "Assegurances i serveis financers"),
    PERSONAL_CARE("13", "Cuidado personal, protección social, y bienes y servicios diversos", "Cura personal i altres");

    /** All categories in INE order (01..13). */
    public static final List<Category> ALL = List.of(values());

    private final String code;
    private final String ineName;
    private final String legacyName;

    /** Name, description and examples shown to the user are in the strings file, under "category.<code>". */
    Category(String code, String ineName, String legacyName) {
        this.code = code;
        this.ineName = ineName;
        this.legacyName = legacyName;
    }

    /** Two-digit COICOP 2018 division code, e.g. "08". */
    public String code() { return code; }

    /** Catalan name shown in the app. */
    public String displayName() { return t("category." + code + ".name"); }

    /** Exact Spanish name used by INE (matches the EPF tables). */
    public String ineName() { return ineName; }

    public String description() { return t("category." + code + ".description"); }

    public String examples() { return t("category." + code + ".examples"); }

    /** Name used by the old web app and by Pessic before format v2. */
    public String legacyName() { return legacyName; }

    /** "08 · Informació i comunicacions" */
    public String label() { return code + " · " + displayName(); }

    public static Category ofCode(String code) {
        return ALL.stream().filter(c -> c.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown category code: " + code));
    }

    public static Optional<Category> fromLegacy(String name) {
        return ALL.stream().filter(c -> c.legacyName.equals(name)).findFirst();
    }

    public static Optional<Category> fromIneName(String name) {
        return Arrays.stream(values()).filter(c -> c.ineName.equals(name)).findFirst();
    }

    /** Accepts a code, a legacy name or the current display name. */
    public static Optional<Category> parse(String value) {
        if (value == null) return Optional.empty();
        return ALL.stream().filter(c -> c.code.equals(value) || c.legacyName.equals(value)
                || c.displayName().equals(value)).findFirst();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
