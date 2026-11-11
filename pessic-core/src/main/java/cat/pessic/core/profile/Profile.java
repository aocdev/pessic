package cat.pessic.core.profile;

import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import java.util.List;

/** One person's (or household's) budget: settings, recorded expenses and the {@link Ledger}. */
public record Profile(String id, String name, BudgetConfig config, List<Movement> movements, Ledger ledger) {
    public Profile {
        movements = List.copyOf(movements);
        if (ledger == null) ledger = Ledger.EMPTY;
    }

    /** Without incomes, pots or closed months. */
    public Profile(String id, String name, BudgetConfig config, List<Movement> movements) {
        this(id, name, config, movements, Ledger.EMPTY);
    }

    public Profile withName(String newName) {
        return new Profile(id, newName, config, movements, ledger);
    }

    public Profile withConfig(BudgetConfig newConfig) {
        return new Profile(id, name, newConfig, movements, ledger);
    }

    public Profile withMovements(List<Movement> newMovements) {
        return new Profile(id, name, config, newMovements, ledger);
    }

    public Profile withLedger(Ledger newLedger) {
        return new Profile(id, name, config, movements, newLedger);
    }

    public List<Income> incomes() {
        return ledger.incomes();
    }
}
