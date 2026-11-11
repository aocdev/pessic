package cat.pessic.core.profile;

import cat.pessic.core.account.Account;
import cat.pessic.core.account.AccountEntry;
import cat.pessic.core.income.Income;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Everything about a profile's money besides its settings and expenses: extra incomes and refunds,
 * savings pots (the annual reserve and goals) with their entries, closed months, and the balance of
 * the day-to-day account with its adjustments.
 *
 * @param account        the day-to-day account, or {@code null} until the user gives its balance
 * @param accountEntries corrections of the account's estimated balance
 */
public record Ledger(List<Income> incomes, List<Pot> pots, List<PotEntry> entries, List<CycleClose> closes,
                     Account account, List<AccountEntry> accountEntries) {

    public static final Ledger EMPTY = new Ledger(List.of(), List.of(), List.of(), List.of(), null, List.of());

    public Ledger {
        incomes = List.copyOf(incomes);
        pots = List.copyOf(pots);
        entries = List.copyOf(entries);
        closes = List.copyOf(closes);
        accountEntries = List.copyOf(accountEntries);
    }

    public Ledger withIncomes(List<Income> v) { return new Ledger(v, pots, entries, closes, account, accountEntries); }

    public Ledger withPots(List<Pot> v) { return new Ledger(incomes, v, entries, closes, account, accountEntries); }

    public Ledger withEntries(List<PotEntry> v) { return new Ledger(incomes, pots, v, closes, account, accountEntries); }

    public Ledger withCloses(List<CycleClose> v) { return new Ledger(incomes, pots, entries, v, account, accountEntries); }

    public Ledger withAccount(Account v) { return new Ledger(incomes, pots, entries, closes, v, accountEntries); }

    public Ledger withAccountEntries(List<AccountEntry> v) {
        return new Ledger(incomes, pots, entries, closes, account, v);
    }

    /** The annual reserve; a fresh one (balance not set yet) if the profile has none stored. */
    public Pot reserve() {
        return pots.stream().filter(Pot::isReserve).findFirst().orElseGet(Pot::newReserve);
    }

    /** Goals, archived ones last. */
    public List<Pot> goals() {
        return pots.stream().filter(p -> !p.isReserve())
                .sorted(java.util.Comparator.comparing(Pot::archived)).toList();
    }

    public List<Pot> activeGoals() {
        return pots.stream().filter(p -> !p.isReserve() && !p.archived()).toList();
    }

    public Optional<Pot> pot(String id) {
        if (Pot.RESERVE_ID.equals(id)) return Optional.of(reserve());
        return pots.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public Optional<CycleClose> close(YearMonth cycle) {
        return closes.stream().filter(c -> c.cycle().equals(cycle)).findFirst();
    }

    public Optional<Account> accountOpt() {
        return Optional.ofNullable(account);
    }
}
