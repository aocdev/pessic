package cat.pessic.core.budget;

import static cat.pessic.core.common.Money.round;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.ScheduledPayment;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The budgeting rules of the app. Stateless apart from the profile settings. */
public final class BudgetCalculator {

    private static final BigDecimal PACE_TOLERANCE = new BigDecimal("1.1");
    private static final BigDecimal PACE_MIN_SPENT = new BigDecimal("20");
    private static final BigDecimal WEEK_WARNING = new BigDecimal("0.85");
    private static final BigDecimal ENVELOPE_WARNING = new BigDecimal("0.15");

    private final BudgetConfig config;
    /** Planned monthly contributions to savings goals; set aside like the annual reserve. */
    private final BigDecimal goalsMonthly;

    public BudgetCalculator(BudgetConfig config) {
        this(config, BigDecimal.ZERO);
    }

    public BudgetCalculator(BudgetConfig config, BigDecimal goalsMonthly) {
        this.config = config;
        this.goalsMonthly = goalsMonthly == null ? BigDecimal.ZERO : goalsMonthly;
    }

    /** For a whole profile: includes the monthly contributions of its active goals. */
    public static BudgetCalculator of(Profile p) {
        return new BudgetCalculator(p.config(), sum(p.ledger().activeGoals().stream().map(Pot::monthly).toList()));
    }

    public BudgetConfig config() {
        return config;
    }

    public BigDecimal goalsMonthly() {
        return goalsMonthly;
    }

    /** The cycle's summary for a whole profile, including its extra incomes and refunds. */
    public CycleSummary summarize(YearMonth key, Profile p, LocalDate today) {
        return summarize(key, p.movements(), p.incomes(), today);
    }

    public PayCycle cycle(YearMonth key) {
        return PayCycle.of(key, config.payday());
    }

    public PayCycle cycleContaining(LocalDate date) {
        return PayCycle.containing(date, config.payday());
    }

    /**
     * Fixed payments that apply to a cycle, placed on their date. A payment whose day is
     * before payday falls in the second calendar month of the cycle; days past the end of a
     * short month are moved to its last day.
     */
    public List<ScheduledPayment> planFor(YearMonth key) {
        LocalDate start = cycle(key).start();
        return config.plan().stream()
                .filter(p -> p.appliesTo(key))
                .map(p -> {
                    YearMonth month = YearMonth.from(start);
                    if (p.day() < config.payday()) month = month.plusMonths(1);
                    LocalDate date = month.atDay(Math.min(p.day(), month.lengthOfMonth()));
                    return new ScheduledPayment(p, date, p.id() + "@" + key);
                })
                .sorted(Comparator.comparing(ScheduledPayment::date))
                .toList();
    }

    public BigDecimal monthlyReserve() {
        return Money.divide(sum(config.annuals().stream().map(AnnualItem::amount).toList()), 12);
    }

    /** Category limit plus any one-off ("extra") fixed payments of that category in the cycle. */
    public BigDecimal effectiveLimit(Category category, YearMonth key) {
        return config.limit(category).add(extraTotal(key, p -> p.payment().category() == category));
    }

    public BigDecimal totalLimits(YearMonth key) {
        return sum(Category.ALL.stream().map(c -> effectiveLimit(c, key)).toList());
    }

    public BigDecimal extraTotal(YearMonth key) {
        return extraTotal(key, p -> true);
    }

    private BigDecimal extraTotal(YearMonth key, Predicate<ScheduledPayment> filter) {
        return sum(planFor(key).stream().filter(p -> p.payment().extra()).filter(filter)
                .map(p -> p.payment().amount()).toList());
    }

    public CycleSummary summarize(YearMonth key, List<Movement> allMovements, LocalDate today) {
        return summarize(key, allMovements, List.of(), today);
    }

    /**
     * @param allIncomes extra incomes and refunds; those dated in the cycle count: money sent to the
     *                   month's budget raises it, refunds lower what was spent in their category
     */
    public CycleSummary summarize(YearMonth key, List<Movement> allMovements, List<Income> allIncomes,
                                  LocalDate today) {
        PayCycle cycle = cycle(key);
        List<Movement> inCycle = allMovements.stream().filter(m -> cycle.contains(m.date())).toList();
        List<Income> incomes = allIncomes.stream().filter(i -> cycle.contains(i.date()))
                .sorted(Comparator.comparing(Income::date)).toList();
        // a refund works as a negative expense of its category
        List<Movement> monthly = new ArrayList<>(inCycle.stream().filter(m -> !m.outsideBudget()).toList());
        incomes.stream().filter(Income::isRefund).forEach(i -> monthly.add(new Movement(i.id(), i.date(),
                i.concept(), i.category(), i.amount().negate(), false, null)));
        List<ScheduledPayment> plan = planFor(key);
        Set<String> paid = inCycle.stream().filter(Movement::isFixed).map(Movement::scheduledId)
                .collect(Collectors.toSet());

        BigDecimal extraBudget = sum(incomes.stream().filter(i -> i.destination() == Income.Destination.BUDGET)
                .map(Income::amount).toList());
        BigDecimal newMoney = sum(incomes.stream().filter(Income::isNewMoney).map(Income::amount).toList());
        BigDecimal refunds = sum(incomes.stream().filter(Income::isRefund).map(Income::amount).toList());
        BigDecimal limits = totalLimits(key);
        BigDecimal budget = limits.add(extraBudget);
        BigDecimal reserve = monthlyReserve();
        BigDecimal spent = sumAmounts(monthly);
        BigDecimal pendingFixed = sum(plan.stream().filter(p -> !paid.contains(p.scheduledId()))
                .map(p -> p.payment().amount()).toList());
        BigDecimal fixedTotal = sum(plan.stream().map(p -> p.payment().amount()).toList());
        BigDecimal variableBudget = budget.subtract(fixedTotal).max(BigDecimal.ZERO);
        List<Movement> variable = monthly.stream().filter(m -> !m.isFixed()).toList();
        BigDecimal variableSpent = sumAmounts(variable);
        BigDecimal left = budget.subtract(spent).subtract(pendingFixed);

        int days = cycle.days();
        boolean isNow = cycle.contains(today);
        int daysLeft = isNow ? (int) ChronoUnit.DAYS.between(today, cycle.end()) + 1
                : today.isBefore(cycle.start()) ? days : 0;
        BigDecimal elapsed = isNow
                ? Money.divide(BigDecimal.valueOf(ChronoUnit.DAYS.between(cycle.start(), today) + 1), days)
                : today.isAfter(cycle.end()) ? BigDecimal.ONE : BigDecimal.ZERO;

        CycleSummary.Status status;
        if (Money.isNegative(left)) {
            status = CycleSummary.Status.OVER_BUDGET;
        } else if (variableSpent.compareTo(variableBudget.multiply(elapsed).multiply(PACE_TOLERANCE)) > 0
                && variableSpent.compareTo(PACE_MIN_SPENT) > 0) {
            status = CycleSummary.Status.AHEAD_OF_PACE;
        } else {
            status = CycleSummary.Status.ON_TRACK;
        }

        return new CycleSummary(
                cycle,
                config.income(),
                round(budget),
                round(extraTotal(key)),
                round(reserve),
                round(config.income().subtract(limits).subtract(reserve).subtract(goalsMonthly)),
                round(spent),
                round(pendingFixed),
                round(fixedTotal),
                round(variableBudget),
                round(variableSpent),
                round(left),
                daysLeft,
                daysLeft > 0 ? round(Money.divide(left, daysLeft)) : null,
                status,
                envelopes(key, monthly, plan, paid, allMovements),
                weeks(cycle, variableBudget, variable, today),
                planLines(plan, paid, reserve, today),
                annualDue(key, allMovements),
                round(goalsMonthly),
                round(extraBudget),
                round(newMoney),
                round(refunds),
                incomes,
                expectedPaycheck(key, allIncomes));
    }

    /** The extra paycheck expected in this cycle (14 payments a year), and whether it was recorded. */
    public CycleSummary.ExpectedPaycheck expectedPaycheck(YearMonth key, List<Income> allIncomes) {
        var month = config.paychecks().extraIn(key, config.payday());
        if (month.isEmpty()) return null;
        String id = Paychecks.scheduledId(month.get());
        Income recorded = allIncomes.stream().filter(i -> id.equals(i.scheduledId())).findFirst().orElse(null);
        return new CycleSummary.ExpectedPaycheck(month.get(), id, config.paychecks().amount(config.income()),
                recorded);
    }

    /** Id that links an annual payment to the movement recording it, one per year. */
    public static String annualScheduledId(AnnualItem a, int year) {
        return a.id() + "@" + year;
    }

    /** Annual payments whose month is the cycle's month, with whether they were already paid that year. */
    public List<CycleSummary.AnnualLine> annualDue(YearMonth key, List<Movement> allMovements) {
        Set<String> recorded = allMovements.stream().map(Movement::scheduledId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return config.annuals().stream()
                .filter(a -> a.month() == key.getMonthValue())
                .sorted(Comparator.comparing(AnnualItem::concept))
                .map(a -> {
                    String id = annualScheduledId(a, key.getYear());
                    return new CycleSummary.AnnualLine(a, id, recorded.contains(id));
                })
                .toList();
    }

    private List<CycleSummary.Envelope> envelopes(YearMonth key, List<Movement> monthly,
                                                  List<ScheduledPayment> plan, Set<String> paid,
                                                  List<Movement> allMovements) {
        List<CycleSummary.AnnualLine> due = annualDue(key, allMovements);
        List<CycleSummary.Envelope> result = new ArrayList<>();
        for (Category c : Category.ALL) {
            BigDecimal limit = effectiveLimit(c, key);
            BigDecimal fixedPaid = sumAmounts(monthly.stream().filter(m -> m.category() == c && m.isFixed()).toList());
            BigDecimal fixedPending = sum(plan.stream()
                    .filter(p -> p.payment().category() == c && !paid.contains(p.scheduledId()))
                    .map(p -> p.payment().amount()).toList());
            BigDecimal varSpent = sumAmounts(monthly.stream().filter(m -> m.category() == c && !m.isFixed()).toList());
            BigDecimal used = fixedPaid.add(varSpent);
            BigDecimal remaining = limit.subtract(used).subtract(fixedPending);
            boolean over = used.add(fixedPending).compareTo(limit.add(new BigDecimal("0.005"))) > 0;
            BigDecimal annualYearly = sum(config.annuals().stream().filter(a -> a.category() == c)
                    .map(AnnualItem::amount).toList());
            BigDecimal annualDue = sum(due.stream().filter(l -> l.item().category() == c)
                    .map(l -> l.item().amount()).toList());
            BigDecimal annualPaid = sum(due.stream().filter(l -> l.item().category() == c && l.paid())
                    .map(l -> l.item().amount()).toList());
            result.add(new CycleSummary.Envelope(c, round(limit), round(config.limit(c)), round(fixedPaid),
                    round(fixedPending), round(varSpent), round(remaining), over, round(annualYearly),
                    round(Money.divide(annualYearly, 12)), round(annualDue), round(annualPaid)));
        }
        return result;
    }

    /** True when an envelope has less than 15 % of its limit left (but is not over). */
    public static boolean isLow(CycleSummary.Envelope e) {
        return !e.over() && e.limit().signum() > 0
                && e.remaining().compareTo(e.limit().multiply(ENVELOPE_WARNING)) < 0;
    }

    /**
     * Splits the variable budget into 7-day weeks. Whatever a past or current week leaves
     * unspent (or overspends) carries into the next one.
     */
    private List<CycleSummary.Week> weeks(PayCycle cycle, BigDecimal variableBudget,
                                          List<Movement> variable, LocalDate today) {
        int days = cycle.days();
        int count = (days + 6) / 7;
        BigDecimal perDay = Money.divide(variableBudget, days);
        BigDecimal carry = BigDecimal.ZERO;
        List<CycleSummary.Week> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate ws = cycle.start().plusDays(7L * i);
            LocalDate we = ws.plusDays(6);
            if (we.isAfter(cycle.end())) we = cycle.end();
            long nd = ChronoUnit.DAYS.between(ws, we) + 1;
            BigDecimal limit = perDay.multiply(BigDecimal.valueOf(nd)).add(carry);
            LocalDate from = ws, to = we;
            BigDecimal spent = sumAmounts(variable.stream()
                    .filter(m -> !m.date().isBefore(from) && !m.date().isAfter(to)).toList());
            BigDecimal remaining = limit.subtract(spent);
            boolean past = we.isBefore(today);
            boolean current = !today.isBefore(ws) && !today.isAfter(we);
            if (past || current) carry = remaining;
            CycleSummary.WeekState state;
            if (!(past || current)) state = CycleSummary.WeekState.PENDING;
            else if (remaining.signum() < 0) state = CycleSummary.WeekState.OVER;
            else if (spent.compareTo(limit.multiply(WEEK_WARNING)) > 0) state = CycleSummary.WeekState.WARNING;
            else state = CycleSummary.WeekState.OK;
            result.add(new CycleSummary.Week(i + 1, ws, we, round(limit), round(spent), round(remaining), state, current));
        }
        return result;
    }

    private List<CycleSummary.PlanLine> planLines(List<ScheduledPayment> plan, Set<String> paid,
                                                  BigDecimal reserve, LocalDate today) {
        BigDecimal balance = config.income().subtract(config.savings()).subtract(reserve).subtract(goalsMonthly);
        List<CycleSummary.PlanLine> lines = new ArrayList<>();
        for (ScheduledPayment p : plan) {
            balance = balance.subtract(p.payment().amount());
            boolean done = paid.contains(p.scheduledId());
            lines.add(new CycleSummary.PlanLine(p, done, !done && p.date().isBefore(today), round(balance)));
        }
        return lines;
    }

    private static BigDecimal sumAmounts(List<Movement> movements) {
        return sum(movements.stream().map(Movement::amount).toList());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
