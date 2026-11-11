package cat.pessic.core.budget;

import cat.pessic.core.common.Checks;
import cat.pessic.core.ine.IneLimits;
import cat.pessic.core.ine.IneReference;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** The budget itself: income, savings, payday, household and the limit of each category. */
public final class BudgetOps {
    private BudgetOps() {}

    public static Profile setLimits(Profile p, Map<Category, BigDecimal> limits) {
        Map<Category, BigDecimal> merged = new EnumMap<>(Category.class);
        merged.putAll(p.config().limits());
        limits.forEach((c, v) -> {
            Checks.present(c, "category");
            if (v.signum() < 0) throw new IllegalArgumentException("limit must be >= 0: " + c);
            merged.put(c, v);
        });
        return p.withConfig(p.config().withLimits(merged));
    }

    /**
     * Sets every limit from the INE expectation for the profile (see {@link IneLimits}).
     * The returned proposal says whether limits had to be scaled down to fit the income.
     */
    public static IneLimits.Proposal applyIneLimits(Profile p, IneReference ine) {
        return IneLimits.propose(p.config(), ine, BudgetCalculator.of(p).goalsMonthly());
    }

    public static Profile updateSettings(Profile p, String name, BigDecimal income, BigDecimal savings,
                                         int payday, int household, Integer members) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (income.signum() < 0 || savings.signum() < 0) throw new IllegalArgumentException("amounts must be >= 0");
        return p.withName(name.strip()).withConfig(p.config().withSettings(income, savings, payday, household, members));
    }
}
