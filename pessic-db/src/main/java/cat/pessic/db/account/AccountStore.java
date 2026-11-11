package cat.pessic.db.account;

import static cat.pessic.db.Jdbc.text;
import static cat.pessic.db.Jdbc.ym;

import cat.pessic.core.account.Account;
import cat.pessic.core.account.AccountEntry;
import cat.pessic.core.common.Money;
import cat.pessic.db.Jdbc;
import java.time.LocalDate;
import java.util.List;

/** The day-to-day account's balance and its corrections (tables {@code account} and {@code account_entry}). */
public final class AccountStore {
    private final Jdbc db;

    public AccountStore(Jdbc db) {
        this.db = db;
    }

    /** The balance the user gave, or {@code null} if they never did. */
    public Account account(String profileId) {
        return db.byProfile("SELECT opening_cents, since FROM account WHERE profile_id = ?", profileId,
                        rs -> new Account(Money.fromCents(rs.getLong(1)), LocalDate.parse(rs.getString(2))))
                .stream().findFirst().orElse(null);
    }

    public List<AccountEntry> entries(String profileId) {
        return db.byProfile("SELECT id, date, amount_cents, note, cycle_ym FROM account_entry WHERE profile_id = ? "
                        + "ORDER BY date, id", profileId,
                rs -> new AccountEntry(rs.getString(1), LocalDate.parse(rs.getString(2)), Money.fromCents(rs.getLong(3)),
                        rs.getString(4), ym(rs.getString(5))));
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "account_entry", "account");
    }

    public void insert(String profileId, Account account, List<AccountEntry> entries) {
        if (account != null) {
            db.update("INSERT INTO account(profile_id, opening_cents, since) VALUES (?, ?, ?)", ps -> {
                ps.setString(1, profileId);
                ps.setLong(2, Money.toCents(account.opening()));
                ps.setString(3, account.since().toString());
            });
        }
        db.batch("INSERT INTO account_entry(profile_id, id, date, amount_cents, note, cycle_ym) VALUES (?, ?, ?, ?, ?, ?)",
                entries, (ps, e) -> {
                    ps.setString(1, profileId);
                    ps.setString(2, e.id());
                    ps.setString(3, e.date().toString());
                    ps.setLong(4, Money.toCents(e.amount()));
                    ps.setString(5, e.note());
                    ps.setString(6, text(e.cycle()));
                });
    }
}
