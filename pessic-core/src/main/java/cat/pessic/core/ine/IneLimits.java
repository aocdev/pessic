package cat.pessic.core.ine;

import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.BudgetOps;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Proposes monthly category limits from the INE expectation for a profile.
 *
 * <ol>
 *   <li>Start from INE's monthly expectation per category ({@link IneReference}).</li>
 *   <li>Subtract what the profile's annual reserve already covers for that category (annual / 12):
 *       INE's averages include yearly spending such as insurance or tuition.</li>
 *   <li>If the total does not fit in what is available each month (income − automatic savings −
 *       annual reserve), scale every limit down by the same factor.</li>
 * </ol>
 */
public final class IneLimits {

    /**
     * @param limits    the proposed limit per category
     * @param scale     1 when nothing had to be scaled, otherwise the factor applied (0..1)
     * @param ineTotal  sum of the INE-based limits before scaling
     * @param available income − automatic savings − annual reserve − savings goals
     */
    public record Proposal(Map<Category, BigDecimal> limits, BigDecimal scale, BigDecimal ineTotal,
                           BigDecimal available) {
        public boolean scaled() {
            return scale.compareTo(BigDecimal.ONE) < 0;
        }

        /** Scale as a whole percentage, e.g. 82. */
        public int scalePercent() {
            return scale.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        }

        public Profile applyTo(Profile p) {
            return BudgetOps.setLimits(p, limits);
        }
    }

    private IneLimits() {}

    public static Proposal propose(BudgetConfig cfg, IneReference ine) {
        return propose(cfg, ine, BigDecimal.ZERO);
    }

    /** @param goalsMonthly monthly contributions to savings goals, kept out of the limits too */
    public static Proposal propose(BudgetConfig cfg, IneReference ine, BigDecimal goalsMonthly) {
        Map<Category, IneReference.Expectation> expected = ine.expected(cfg);
        Map<Category, BigDecimal> target = new EnumMap<>(Category.class);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal reserve = BigDecimal.ZERO;
        for (Category c : Category.ALL) {
            BigDecimal annualMonthly = Money.divide(cfg.annuals().stream().filter(a -> a.category() == c)
                    .map(AnnualItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add), 12);
            reserve = reserve.add(annualMonthly);
            BigDecimal t = expected.get(c).monthly().subtract(annualMonthly).max(BigDecimal.ZERO);
            target.put(c, t);
            total = total.add(t);
        }
        BigDecimal available = cfg.income().subtract(cfg.savings()).subtract(reserve).subtract(goalsMonthly).max(BigDecimal.ZERO);
        BigDecimal scale = total.signum() == 0 || total.compareTo(available) <= 0 ? BigDecimal.ONE
                : available.divide(total, Money.CALC);
        Map<Category, BigDecimal> limits = new EnumMap<>(Category.class);
        target.forEach((c, t) -> limits.put(c, t.multiply(scale).setScale(2, RoundingMode.DOWN)));
        return new Proposal(Collections.unmodifiableMap(limits), scale, Money.round(total), Money.round(available));
    }
}
