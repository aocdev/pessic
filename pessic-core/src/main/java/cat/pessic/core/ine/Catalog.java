package cat.pessic.core.ine;

import static cat.pessic.core.common.I18n.t;
import java.util.List;

/** Household types as INE publishes them in the EPF (same order as table 73807). */
public final class Catalog {

    public static final List<String> HOUSEHOLDS = java.util.stream.IntStream.range(0, 9)
            .mapToObj(i -> t("ine.household." + i)).toList();

    /** Index of "Mitjana de totes les llars". */
    public static final int ALL_HOUSEHOLDS = HOUSEHOLDS.size() - 1;

    /** Members are grouped as INE does: 1, 2, 3, 4 and "5 o més". */
    public static final int MAX_MEMBERS = 5;

    private Catalog() {}
}
