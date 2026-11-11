package cat.pessic.core.profile;

import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Ids;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.ine.IneLimits;
import cat.pessic.core.ine.IneReference;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Creates a profile from the first-run form. */
public final class NewProfile {

    /** Household preselected in the form: "Parella sense fills". */
    public static final int DEFAULT_HOUSEHOLD = 2;

    private NewProfile() {}

    /**
     * @param members      people in the household, or {@code null} if not given
     * @param useIneLimits start limits from the INE expectation for this household ({@link IneLimits});
     *                     otherwise every limit starts at zero
     */
    public static Profile create(String name, BigDecimal income, int payday, int household, Integer members,
                                 boolean useIneLimits) {
        return create(name, income, payday, household, members, Paychecks.TWELVE, useIneLimits);
    }

    /** @param paychecks 12 or 14 payments a year */
    public static Profile create(String name, BigDecimal income, int payday, int household, Integer members,
                                 Paychecks paychecks, boolean useIneLimits) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (income == null || income.signum() < 0) throw new IllegalArgumentException("income must be >= 0");
        Map<Category, BigDecimal> zero = new EnumMap<>(Category.class);
        Category.ALL.forEach(c -> zero.put(c, BigDecimal.ZERO));
        BudgetConfig config = new BudgetConfig(income, BigDecimal.ZERO, payday, household, members, zero,
                List.of(), List.of(), paychecks);
        Profile p = new Profile(Ids.next("p"), name.strip(), config, List.of());
        return useIneLimits ? IneLimits.propose(config, IneReference.bundled()).applyTo(p) : p;
    }
}
