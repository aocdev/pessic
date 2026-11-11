package cat.pessic.core.expense;

import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Checks;
import cat.pessic.core.common.Ids;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Recording and deleting expenses. Each operation returns the new profile, which the caller saves. */
public final class ExpenseOps {
    private ExpenseOps() {}

    public static Profile addMovement(Profile p, LocalDate date, String concept, Category category,
                                      BigDecimal amount, boolean fromReserve) {
        return addMovement(p, date, concept, category, amount, fromReserve, null);
    }

    /** @param goalId paid from this savings goal, so it does not use the monthly budget; or {@code null} */
    public static Profile addMovement(Profile p, LocalDate date, String concept, Category category,
                                      BigDecimal amount, boolean fromReserve, String goalId) {
        Checks.positive(amount);
        Checks.present(category, "category");
        if (goalId != null) SavingsOps.requireGoal(p, goalId);
        List<Movement> moves = new ArrayList<>(p.movements());
        moves.add(new Movement(Ids.next("m"), date, concept.strip(), category, amount, fromReserve, null, goalId));
        return p.withMovements(moves);
    }

    public static Profile deleteMovement(Profile p, String movementId) {
        return p.withMovements(p.movements().stream().filter(m -> !m.id().equals(movementId)).toList());
    }
}
