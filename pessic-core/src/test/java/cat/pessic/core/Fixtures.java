package cat.pessic.core;

import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Money;
import cat.pessic.core.expense.ExpenseOps;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.IncomeOps;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.profile.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** A small profile (2000 € income, payday 28) and helpers shared by the ledger tests. */
public final class Fixtures {
    private Fixtures() {}

    public static final YearMonth NOV = YearMonth.of(2026, 11); // payday 28: 28 Oct - 27 Nov

    public static final YearMonth DEC = YearMonth.of(2026, 12);

    public static final AnnualItem CLAUDE = new AnnualItem("cl", 12, "Claude", Category.INFO_COMMUNICATION, eur("217.80"));

    public static BigDecimal eur(String v) {
        return Money.of(v);
    }

    public static Profile profile(List<AnnualItem> annuals) {
        var cfg = new BudgetConfig(eur("2000"), eur("300"), 28, 2, null,
                Map.of(Category.FOOD, eur("300"), Category.TRANSPORT, eur("200"), Category.RECREATION, eur("100")),
                List.of(), annuals);
        return new Profile("p", "Marta", cfg, List.of());
    }

    public static Profile spend(Profile p, String date, Category c, String amount) {
        return ExpenseOps.addMovement(p, LocalDate.parse(date), "Despesa", c, eur(amount), false);
    }

    public static Profile income(Profile p, String date, String amount, Income.Kind kind, Income.Destination dest,
                                 Category c, String potId) {
        return IncomeOps.addIncome(p, LocalDate.parse(date), "", eur(amount), kind, dest, c, potId, null);
    }
}
