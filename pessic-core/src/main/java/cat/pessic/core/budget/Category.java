package cat.pessic.core.budget;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The 13 spending groups of COICOP 2018, the classification INE uses in the Encuesta de
 * Presupuestos Familiares (EPF) from 2024 on. The two-digit {@link #code} is stable and is what
 * the database and backups store; names are for display only.
 */
public enum Category {
    FOOD("01", "Aliments i begudes no alcohòliques",
            "Alimentos y bebidas no alcohólicas",
            "Menjar i begudes sense alcohol per consumir a casa.",
            "súper, fruita, pa, carn, aigua, cafè, sucs",
            "Alimentació"),
    ALCOHOL_TOBACCO("02", "Begudes alcohòliques i tabac",
            "Bebidas alcohólicas, tabaco y estupefacientes",
            "Begudes alcohòliques i tabac comprats per consumir a casa.",
            "vi, cervesa, licors, tabac",
            "Alcohol i tabac"),
    CLOTHING("03", "Vestit i calçat",
            "Vestido y calzado",
            "Roba, calçat i les seves reparacions.",
            "roba, sabates, roba esportiva, arranjaments",
            "Vestit i calçat"),
    HOUSING("04", "Habitatge, aigua, electricitat, gas i altres combustibles",
            "Vivienda, agua, electricidad, gas y otros combustibles",
            "Lloguer, subministraments de casa i reparacions de l'habitatge.",
            "lloguer, aigua, llum, gas, calefacció, comunitat, escombraries, petites obres",
            "Habitatge, aigua, llum i gas"),
    FURNISHINGS("05", "Mobles, equipament i manteniment de la llar",
            "Muebles, artículos del hogar y artículos para el mantenimiento corriente del hogar",
            "Mobles i aparells de la casa i el seu manteniment corrent.",
            "mobles, electrodomèstics, parament, eines, productes de neteja, servei domèstic",
            "Mobles i llar"),
    HEALTH("06", "Salut",
            "Sanidad",
            "Despeses de salut que pagues tu.",
            "medicaments, ulleres, dentista, metge o fisioterapeuta privat, nutricionista",
            "Sanitat"),
    TRANSPORT("07", "Transport",
            "Transporte",
            "Vehicles, el seu ús i el transport públic.",
            "préstec o compra del cotxe, benzina, revisions, pàrquing, peatges, bus, tren, taxi, vols",
            "Transport"),
    INFO_COMMUNICATION("08", "Informació i comunicacions",
            "Información y comunicaciones",
            "Telèfon, internet, aparells i serveis digitals.",
            "mòbil, fibra, ordinador, tauleta, plataformes de streaming i altres subscripcions digitals",
            "Comunicacions"),
    RECREATION("09", "Activitats recreatives, esport i cultura",
            "Actividades recreativas, deporte y cultura",
            "Oci, esport, cultura, mascotes i viatges organitzats.",
            "gimnàs, espectacles, llibres, música, jocs, mascotes, jardineria, paquets turístics",
            "Oci, esport i cultura"),
    EDUCATION("10", "Serveis d'educació",
            "Servicios de educación",
            "Ensenyament reglat i cursos.",
            "escola, universitat, matrícules, idiomes, cursos",
            "Educació"),
    RESTAURANTS("11", "Restaurants i allotjament",
            "Restaurantes y servicios de alojamiento",
            "Menjar i beure fora de casa i allotjament.",
            "bars, cafeteries, restaurants, menjar per emportar, menjador, hotels",
            "Restaurants i hotels"),
    INSURANCE_FINANCE("12", "Assegurances i serveis financers",
            "Seguros y servicios financieros",
            "Assegurances i despeses bancàries.",
            "assegurança de la llar, de salut, de vida o del cotxe, comissions bancàries",
            "Assegurances i serveis financers"),
    PERSONAL_CARE("13", "Cura personal, protecció social i altres",
            "Cuidado personal, protección social, y bienes y servicios diversos",
            "Cura personal i tot allò que no encaixa en cap altre grup.",
            "perruqueria, higiene i cosmètica, joies, rellotges, cura d'infants o de gent gran, gestories",
            "Cura personal i altres");

    /** All categories in INE order (01..13). */
    public static final List<Category> ALL = List.of(values());

    private final String code;
    private final String displayName;
    private final String ineName;
    private final String description;
    private final String examples;
    private final String legacyName;

    Category(String code, String displayName, String ineName, String description, String examples, String legacyName) {
        this.code = code;
        this.displayName = displayName;
        this.ineName = ineName;
        this.description = description;
        this.examples = examples;
        this.legacyName = legacyName;
    }

    /** Two-digit COICOP 2018 division code, e.g. "08". */
    public String code() { return code; }

    /** Catalan name shown in the app. */
    public String displayName() { return displayName; }

    /** Exact Spanish name used by INE (matches the EPF tables). */
    public String ineName() { return ineName; }

    public String description() { return description; }

    public String examples() { return examples; }

    /** Name used by the old web app and by Pessic before format v2. */
    public String legacyName() { return legacyName; }

    /** "08 · Informació i comunicacions" */
    public String label() { return code + " · " + displayName; }

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
                || c.displayName.equals(value)).findFirst();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
