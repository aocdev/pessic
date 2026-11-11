package cat.pessic.core.budget;

import cat.pessic.core.common.Money;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.ine.Catalog;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.FixedPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Settings of one profile.
 *
 * @param payday    day of the month the income arrives (1..28); the budget cycle starts that day
 * @param household index into {@link Catalog#HOUSEHOLDS}
 * @param members   people in the household, or {@code null} if not given
 * @param limits    monthly limit per category
 * @param paychecks 12 or 14 payments a year (only used to plan extra paychecks)
 */
public record BudgetConfig(BigDecimal income, BigDecimal savings, int payday, int household, Integer members,
                           Map<Category, BigDecimal> limits, List<FixedPayment> plan, List<AnnualItem> annuals,
                           Paychecks paychecks) {
    public BudgetConfig {
        if (payday < 1 || payday > 28) throw new IllegalArgumentException("payday must be 1..28: " + payday);
        if (household < 0 || household >= Catalog.HOUSEHOLDS.size())
            throw new IllegalArgumentException("household out of range: " + household);
        if (members != null && (members < 1 || members > 20))
            throw new IllegalArgumentException("members must be 1..20: " + members);
        income = Money.round(income);
        savings = Money.round(savings == null ? BigDecimal.ZERO : savings);
        var rounded = new java.util.EnumMap<Category, BigDecimal>(Category.class);
        limits.forEach((c, v) -> rounded.put(c, Money.round(v)));
        limits = java.util.Collections.unmodifiableMap(rounded);
        plan = List.copyOf(plan);
        annuals = List.copyOf(annuals);
        if (paychecks == null) paychecks = Paychecks.TWELVE;
    }

    /** Paid in 12 payments. */
    public BudgetConfig(BigDecimal income, BigDecimal savings, int payday, int household, Integer members,
                        Map<Category, BigDecimal> limits, List<FixedPayment> plan, List<AnnualItem> annuals) {
        this(income, savings, payday, household, members, limits, plan, annuals, Paychecks.TWELVE);
    }

    /** Without household size (unknown). */
    public BudgetConfig(BigDecimal income, BigDecimal savings, int payday, int household,
                        Map<Category, BigDecimal> limits, List<FixedPayment> plan, List<AnnualItem> annuals) {
        this(income, savings, payday, household, null, limits, plan, annuals);
    }

    public BigDecimal limit(Category category) {
        return limits.getOrDefault(category, BigDecimal.ZERO);
    }

    public BudgetConfig withLimits(Map<Category, BigDecimal> newLimits) {
        return new BudgetConfig(income, savings, payday, household, members, newLimits, plan, annuals, paychecks);
    }

    public BudgetConfig withPlan(List<FixedPayment> newPlan) {
        return new BudgetConfig(income, savings, payday, household, members, limits, newPlan, annuals, paychecks);
    }

    public BudgetConfig withAnnuals(List<AnnualItem> newAnnuals) {
        return new BudgetConfig(income, savings, payday, household, members, limits, plan, newAnnuals, paychecks);
    }

    public BudgetConfig withPaychecks(Paychecks newPaychecks) {
        return new BudgetConfig(income, savings, payday, household, members, limits, plan, annuals, newPaychecks);
    }

    public BudgetConfig withSettings(BigDecimal newIncome, BigDecimal newSavings, int newPayday, int newHousehold,
                                     Integer newMembers) {
        return new BudgetConfig(newIncome, newSavings, newPayday, newHousehold, newMembers, limits, plan, annuals, paychecks);
    }
}
