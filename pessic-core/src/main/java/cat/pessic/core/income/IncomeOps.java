package cat.pessic.core.income;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Ids;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Money that comes in besides the monthly salary, and how many paychecks a year there are. */
public final class IncomeOps {
    private IncomeOps() {}

    /**
     * Records money that came in. For an expected extra paycheck pass its {@code scheduledId}
     * ({@link Paychecks#scheduledId}); recording it twice does nothing.
     */
    public static Profile addIncome(Profile p, LocalDate date, String concept, BigDecimal amount, Income.Kind kind,
                                    Income.Destination destination, Category category, String potId,
                                    String scheduledId) {
        if (scheduledId != null && p.incomes().stream().anyMatch(i -> scheduledId.equals(i.scheduledId()))) return p;
        Income income = new Income(Ids.next("i"), date, concept, amount, kind, destination, category, potId, scheduledId);
        if (destination == Income.Destination.GOAL) SavingsOps.requireGoal(p, income.potId());
        Profile q = destination == Income.Destination.RESERVE ? SavingsOps.ensureReserve(p, date) : p;
        List<Income> list = new ArrayList<>(q.incomes());
        list.add(income);
        return q.withLedger(q.ledger().withIncomes(list));
    }

    public static Profile removeIncome(Profile p, String id) {
        return p.withLedger(p.ledger().withIncomes(p.incomes().stream().filter(i -> !i.id().equals(id)).toList()));
    }

    public static Profile updatePaychecks(Profile p, Paychecks paychecks) {
        return p.withConfig(p.config().withPaychecks(paychecks));
    }
}
