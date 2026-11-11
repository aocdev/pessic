package cat.pessic.core.ine;

import java.util.List;

/** Household types as INE publishes them in the EPF (same order as table 73807). */
public final class Catalog {

    public static final List<String> HOUSEHOLDS = List.of(
            "Persona sola menor de 65 anys",
            "Persona sola de 65 anys o més",
            "Parella sense fills",
            "Parella amb 1 fill",
            "Parella amb 2 fills",
            "Parella amb 3 o més fills",
            "Un adult amb fills",
            "Un altre tipus de llar",
            "Mitjana de totes les llars");

    /** Index of "Mitjana de totes les llars". */
    public static final int ALL_HOUSEHOLDS = HOUSEHOLDS.size() - 1;

    /** Members are grouped as INE does: 1, 2, 3, 4 and "5 o més". */
    public static final int MAX_MEMBERS = 5;

    private Catalog() {}
}
