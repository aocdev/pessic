package cat.pessic.core.account;

import cat.pessic.core.common.Ids;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.core.savings.SavingsOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The day-to-day account, where the salary arrives: its balance and the corrections to it. */
public final class AccountOps {
    private AccountOps() {}

    /**
     * The day-to-day account held {@code amount} at the end of {@code date}. Starts (or restarts)
     * the estimated balance from there; earlier corrections no longer count.
     */
    public static Profile setAccountBalance(Profile p, BigDecimal amount, LocalDate date) {
        Ledger l = p.ledger();
        return p.withLedger(l.withAccount(new Account(amount, date))
                .withAccountEntries(l.accountEntries().stream().filter(e -> e.date().isAfter(date)).toList()));
    }

    /** Forgets the account balance: Pessic stops estimating it until it is given again. */
    public static Profile clearAccount(Profile p) {
        return p.withLedger(p.ledger().withAccount(null).withAccountEntries(List.of()));
    }

    /**
     * The bank says the account holds {@code actual} at the end of {@code date}: records the difference
     * from the estimate (usually expenses that were not recorded).
     *
     * @param cycle the cycle being closed when the check is part of a month close, or {@code null}
     */
    public static Profile adjustAccount(Profile p, BigDecimal actual, LocalDate date, YearMonth cycle) {
        Account a = p.ledger().account();
        if (a == null || !date.isAfter(a.since())) return setAccountBalance(p, actual, date);
        BigDecimal diff = actual.subtract(Cash.estimate(p, date));
        if (diff.signum() == 0) return p;
        List<AccountEntry> list = new ArrayList<>(p.ledger().accountEntries());
        list.add(new AccountEntry(Ids.next("c"), date, diff, "Ajust amb el saldo del banc", cycle));
        return p.withLedger(p.ledger().withAccountEntries(list));
    }

    /**
     * Moves money that is already in the account into the reserve and goals (when the balance is first
     * given): each pot receives a contribution and the account balance falls by the total.
     *
     * @param amounts amount per pot id; zero or missing ones are skipped
     */
    public static Profile fundPots(Profile p, Map<String, BigDecimal> amounts, LocalDate date) {
        Account a = p.ledger().account();
        if (a == null) throw new IllegalArgumentException("give the account balance first");
        BigDecimal total = BigDecimal.ZERO;
        Profile q = p;
        for (var en : amounts.entrySet()) {
            BigDecimal v = en.getValue();
            if (v == null || v.signum() == 0) continue;
            if (v.signum() < 0) throw new IllegalArgumentException("amount must be >= 0");
            if (Pot.RESERVE_ID.equals(en.getKey()) && !q.ledger().reserve().openingSet()) {
                q = SavingsOps.setReserveBalance(q, BigDecimal.ZERO, date);
            }
            q = SavingsOps.addPotEntry(q, en.getKey(), date, v, PotEntry.Kind.CONTRIBUTION, "Diners que ja tenia al compte");
            total = total.add(v);
        }
        if (total.signum() == 0) return p;
        // dated on the opening day, so the entries do not count again in the estimate: lower the opening instead
        Account now = q.ledger().account();
        if (date.isAfter(now.since())) return q; // later than the opening: counted as outflows by Cash
        return q.withLedger(q.ledger().withAccount(new Account(now.opening().subtract(total), now.since())));
    }
}
