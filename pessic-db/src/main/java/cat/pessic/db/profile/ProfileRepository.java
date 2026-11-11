package cat.pessic.db.profile;

import static cat.pessic.db.Jdbc.nullableInt;
import static cat.pessic.db.Jdbc.nullableLong;

import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.common.Money;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.profile.Profile;
import cat.pessic.db.Database;
import cat.pessic.db.Jdbc;
import cat.pessic.db.account.AccountStore;
import cat.pessic.db.budget.BudgetStore;
import cat.pessic.db.expense.ExpenseStore;
import cat.pessic.db.income.IncomeStore;
import cat.pessic.db.monthclose.MonthCloseStore;
import cat.pessic.db.payment.PaymentStore;
import cat.pessic.db.savings.SavingsStore;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes whole profiles. The {@code profile} row (name and budget settings) is kept here;
 * every feature's rows are read and written by that feature's store. Saving replaces all of a
 * profile's rows inside one transaction.
 */
public final class ProfileRepository {

    private static final String ACTIVE_PROFILE = "active_profile";

    private final Jdbc db;
    private final BudgetStore budget;
    private final PaymentStore payments;
    private final ExpenseStore expenses;
    private final IncomeStore incomes;
    private final SavingsStore savings;
    private final MonthCloseStore closes;
    private final AccountStore account;

    public ProfileRepository(Database database) {
        this.db = new Jdbc(database);
        this.budget = new BudgetStore(db);
        this.payments = new PaymentStore(db);
        this.expenses = new ExpenseStore(db);
        this.incomes = new IncomeStore(db);
        this.savings = new SavingsStore(db);
        this.closes = new MonthCloseStore(db);
        this.account = new AccountStore(db);
    }

    /** @param empty no movements, fixed payments, annual payments or incomes yet */
    public record ProfileRef(String id, String name, boolean empty) {}

    public List<ProfileRef> list() {
        return db.query("SELECT p.id, p.name, "
                        + "NOT EXISTS (SELECT 1 FROM movement m WHERE m.profile_id = p.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM fixed_payment f WHERE f.profile_id = p.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM annual_item a WHERE a.profile_id = p.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM income i WHERE i.profile_id = p.id) "
                        + "FROM profile p ORDER BY p.created_at, p.name", ps -> {},
                rs -> new ProfileRef(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
    }

    public Optional<String> activeProfileId() {
        List<String> v = db.query("SELECT value FROM app_setting WHERE key = ?",
                ps -> ps.setString(1, ACTIVE_PROFILE), rs -> rs.getString(1));
        return v.stream().findFirst().filter(id -> find(id).isPresent());
    }

    public void setActiveProfile(String id) {
        db.update("INSERT INTO app_setting(key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value", ps -> {
            ps.setString(1, ACTIVE_PROFILE);
            ps.setString(2, id);
        });
    }

    public Optional<Profile> find(String id) {
        record Row(String name, long income, long savings, int payday, int household, Integer members, int paychecks,
                   Long extraPay, String extraMonths) {}
        Optional<Row> row = db.byProfile(
                "SELECT name, income_cents, savings_cents, payday, household, members, paychecks, extra_pay_cents, "
                        + "extra_pay_months FROM profile WHERE id = ?", id,
                rs -> new Row(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getInt(5),
                        nullableInt(rs, 6), rs.getInt(7), nullableLong(rs, 8), rs.getString(9))).stream().findFirst();
        if (row.isEmpty()) return Optional.empty();
        Row r = row.get();

        Paychecks paychecks = new Paychecks(r.paychecks(), r.extraPay() == null ? null : Money.fromCents(r.extraPay()),
                Paychecks.parseMonths(r.extraMonths()));
        BudgetConfig cfg = new BudgetConfig(Money.fromCents(r.income()), Money.fromCents(r.savings()), r.payday(),
                r.household(), r.members(), budget.limits(id), payments.fixed(id), payments.annuals(id), paychecks);
        Ledger ledger = new Ledger(incomes.load(id), savings.pots(id), savings.entries(id), closes.load(id),
                account.account(id), account.entries(id));
        return Optional.of(new Profile(id, r.name(), cfg, expenses.load(id), ledger));
    }

    public void save(Profile p) {
        db.inTransaction(() -> {
            saveProfileRow(p);
            String id = p.id();
            account.delete(id);
            savings.delete(id);
            closes.delete(id);
            incomes.delete(id);
            budget.delete(id);
            payments.delete(id);
            expenses.delete(id);

            BudgetConfig cfg = p.config();
            Ledger l = p.ledger();
            budget.insert(id, cfg.limits());
            payments.insert(id, cfg.plan(), cfg.annuals());
            expenses.insert(id, p.movements());
            incomes.insert(id, l.incomes());
            savings.insert(id, l);
            closes.insert(id, l.closes());
            account.insert(id, l.account(), l.accountEntries());
        });
    }

    public void delete(String id) {
        db.update("DELETE FROM profile WHERE id = ?", ps -> ps.setString(1, id));
    }

    private void saveProfileRow(Profile p) {
        BudgetConfig cfg = p.config();
        db.update("INSERT INTO profile(id, name, income_cents, savings_cents, payday, household, members, paychecks, "
                + "extra_pay_cents, extra_pay_months) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = excluded.name, "
                + "income_cents = excluded.income_cents, savings_cents = excluded.savings_cents, "
                + "payday = excluded.payday, household = excluded.household, members = excluded.members, "
                + "paychecks = excluded.paychecks, extra_pay_cents = excluded.extra_pay_cents, "
                + "extra_pay_months = excluded.extra_pay_months", ps -> {
            ps.setString(1, p.id());
            ps.setString(2, p.name());
            ps.setLong(3, Money.toCents(cfg.income()));
            ps.setLong(4, Money.toCents(cfg.savings()));
            ps.setInt(5, cfg.payday());
            ps.setInt(6, cfg.household());
            if (cfg.members() == null) ps.setNull(7, java.sql.Types.INTEGER);
            else ps.setInt(7, cfg.members());
            ps.setInt(8, cfg.paychecks().count());
            if (cfg.paychecks().extraAmount() == null) ps.setNull(9, java.sql.Types.INTEGER);
            else ps.setLong(9, Money.toCents(cfg.paychecks().extraAmount()));
            ps.setString(10, cfg.paychecks().monthsText());
        });
    }
}
