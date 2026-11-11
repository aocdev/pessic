package cat.pessic.db.savings;

import static cat.pessic.db.Jdbc.date;
import static cat.pessic.db.Jdbc.nullableLong;
import static cat.pessic.db.Jdbc.text;
import static cat.pessic.db.Jdbc.ym;

import cat.pessic.core.common.Money;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.db.Jdbc;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The reserve, the savings goals and the money put in or taken out (tables {@code pot} and {@code pot_entry}). */
public final class SavingsStore {
    private final Jdbc db;

    public SavingsStore(Jdbc db) {
        this.db = db;
    }

    public List<Pot> pots(String profileId) {
        return db.byProfile("SELECT id, kind, name, target_cents, target_ym, monthly_cents, opening_cents, since, archived "
                        + "FROM pot WHERE profile_id = ? ORDER BY kind DESC, name", profileId,
                rs -> {
                    Long target = nullableLong(rs, 4);
                    return new Pot(rs.getString(1), Pot.Kind.ofCode(rs.getString(2)), rs.getString(3),
                            target == null ? null : Money.fromCents(target), ym(rs.getString(5)),
                            Money.fromCents(rs.getLong(6)), Money.fromCents(rs.getLong(7)), date(rs.getString(8)),
                            rs.getBoolean(9));
                });
    }

    public List<PotEntry> entries(String profileId) {
        return db.byProfile("SELECT id, pot_id, date, amount_cents, kind, note, cycle_ym FROM pot_entry "
                        + "WHERE profile_id = ? ORDER BY date, id", profileId,
                rs -> new PotEntry(rs.getString(1), rs.getString(2), LocalDate.parse(rs.getString(3)),
                        Money.fromCents(rs.getLong(4)), PotEntry.Kind.ofCode(rs.getString(5)), rs.getString(6),
                        ym(rs.getString(7))));
    }

    public void delete(String profileId) {
        db.deleteProfileRows(profileId, "pot_entry", "pot"); // entries reference their pot
    }

    public void insert(String profileId, Ledger l) {
        // the reserve is stored as soon as anything refers to it
        List<Pot> pots = new ArrayList<>(l.pots());
        boolean needsReserve = l.reserve().openingSet()
                || l.entries().stream().anyMatch(e -> Pot.RESERVE_ID.equals(e.potId()));
        if (needsReserve && pots.stream().noneMatch(Pot::isReserve)) pots.add(l.reserve());
        db.batch("INSERT INTO pot(profile_id, id, kind, name, target_cents, target_ym, monthly_cents, opening_cents, "
                + "since, archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", pots, (ps, x) -> {
            ps.setString(1, profileId);
            ps.setString(2, x.id());
            ps.setString(3, x.kind().code());
            ps.setString(4, x.name());
            if (x.target() == null) ps.setNull(5, java.sql.Types.INTEGER);
            else ps.setLong(5, Money.toCents(x.target()));
            ps.setString(6, text(x.targetMonth()));
            ps.setLong(7, Money.toCents(x.monthly()));
            ps.setLong(8, Money.toCents(x.opening()));
            ps.setString(9, text(x.since()));
            ps.setBoolean(10, x.archived());
        });
        db.batch("INSERT INTO pot_entry(profile_id, id, pot_id, date, amount_cents, kind, note, cycle_ym) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", l.entries(), (ps, e) -> {
            ps.setString(1, profileId);
            ps.setString(2, e.id());
            ps.setString(3, e.potId());
            ps.setString(4, e.date().toString());
            ps.setLong(5, Money.toCents(e.amount()));
            ps.setString(6, e.kind().code());
            ps.setString(7, e.note());
            ps.setString(8, text(e.cycle()));
        });
    }
}
