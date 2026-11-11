package cat.pessic.db.backup;

import cat.pessic.core.account.Account;
import cat.pessic.core.account.AccountEntry;
import cat.pessic.core.budget.BudgetConfig;
import cat.pessic.core.budget.Category;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.Paychecks;
import cat.pessic.core.ine.Catalog;
import cat.pessic.core.monthclose.CycleClose;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.FixedPayment;
import cat.pessic.core.profile.Ledger;
import cat.pessic.core.profile.Profile;
import cat.pessic.core.savings.Pot;
import cat.pessic.core.savings.PotEntry;
import cat.pessic.db.profile.ProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON backups and turns them into profiles. Two formats are accepted:
 * <ul>
 *   <li>v1: exported by the old web app ("Descarrega una còpia") or by Pessic before COICOP 2018
 *       codes; categories are identified by their old Catalan names.</li>
 *   <li>v2: exported by Pessic; categories are COICOP 2018 codes ("08") and the profile may have
 *       {@code "membres"}.</li>
 *   <li>v3: v2 plus paychecks, extra incomes, savings pots and closed months.</li>
 *   <li>v4: exported by Pessic ({@link BackupExporter}); v3 plus the day-to-day account.</li>
 * </ul>
 * Both share the shape {@code {"v":..,"active":"<id>","profiles":{"<id>":{"nom":..,"cfg":{..},"moves":[..]}}}}.
 */
public final class BackupImporter {

    public record Backup(List<Profile> profiles, String activeProfileId) {}

    private final ObjectMapper mapper = new ObjectMapper();

    public Backup read(InputStream in) throws IOException {
        JsonNode root = mapper.readTree(in);
        if (root == null || !root.path("profiles").isObject()) {
            throw new IOException("Not a backup of the Pressupost web app: missing \"profiles\"");
        }
        List<Profile> profiles = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.get("profiles").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            profiles.add(profile(e.getKey(), e.getValue()));
        }
        String active = root.path("active").isTextual() ? root.get("active").asText() : null;
        return new Backup(profiles, active);
    }

    /** Reads the backup and stores every profile, replacing profiles with the same id. */
    public Backup importInto(InputStream in, ProfileRepository repo) throws IOException {
        Backup backup = read(in);
        backup.profiles().forEach(repo::save);
        String active = backup.activeProfileId();
        if (active == null && !backup.profiles().isEmpty()) active = backup.profiles().get(0).id();
        if (active != null) repo.setActiveProfile(active);
        return backup;
    }

    private Profile profile(String id, JsonNode p) throws IOException {
        JsonNode cfg = p.path("cfg");
        Map<Category, BigDecimal> limits = new java.util.EnumMap<>(Category.class);
        cfg.path("limits").fields().forEachRemaining(e ->
                Category.parse(e.getKey()).ifPresent(c -> limits.put(c, amount(e.getValue()))));

        List<FixedPayment> plan = new ArrayList<>();
        for (JsonNode f : cfg.path("plan")) {
            plan.add(new FixedPayment(f.path("id").asText(), f.path("dia").asInt(1), f.path("c").asText(""),
                    category(f), amount(f.path("imp")), ym(f.path("des")), ym(f.path("fins")),
                    f.path("extra").asBoolean(false)));
        }
        List<AnnualItem> annuals = new ArrayList<>();
        for (JsonNode a : cfg.path("anuals")) {
            annuals.add(new AnnualItem(a.path("id").asText(), a.path("mes").asInt(1), a.path("c").asText(""),
                    category(a), amount(a.path("imp"))));
        }
        List<Movement> moves = new ArrayList<>();
        for (JsonNode m : p.path("moves")) {
            moves.add(new Movement(m.path("id").asText(), LocalDate.parse(m.path("data").asText()),
                    m.path("c").asText(""), category(m), amount(m.path("imp")),
                    m.path("reserva").asBoolean(false),
                    text(m, "pid"), text(m, "guardiola")));
        }
        int household = cfg.path("llar").asInt(Catalog.ALL_HOUSEHOLDS);
        Integer members = cfg.hasNonNull("membres") ? cfg.get("membres").asInt() : null;
        Paychecks paychecks = Paychecks.TWELVE;
        JsonNode pay = cfg.path("pagues");
        if (pay.isObject()) {
            List<Integer> months = new ArrayList<>();
            pay.path("mesos").forEach(x -> months.add(x.asInt()));
            paychecks = new Paychecks(pay.path("n").asInt(12), pay.hasNonNull("imp") ? amount(pay.get("imp")) : null,
                    months.isEmpty() ? null : months);
        }
        BudgetConfig config = new BudgetConfig(amount(cfg.path("ingressos")), amount(cfg.path("estalvi")),
                cfg.path("dia").asInt(1), household, members, limits, plan, annuals, paychecks);
        return new Profile(id, p.path("nom").asText(id), config, moves, ledger(p));
    }

    /** Incomes, pots and closed months (format v3); empty for older backups. */
    private Ledger ledger(JsonNode p) throws IOException {
        List<Income> incomes = new ArrayList<>();
        for (JsonNode i : p.path("ingressos")) {
            incomes.add(new Income(i.path("id").asText(), LocalDate.parse(i.path("data").asText()), i.path("c").asText(""),
                    amount(i.path("imp")), Income.Kind.ofCode(i.path("tipus").asText("other")),
                    Income.Destination.ofCode(i.path("desti").asText("savings")),
                    i.hasNonNull("cat") ? category(i) : null, text(i, "guardiola"), text(i, "pid")));
        }
        List<Pot> pots = new ArrayList<>();
        for (JsonNode x : p.path("guardioles")) {
            pots.add(new Pot(x.path("id").asText(), Pot.Kind.ofCode(x.path("tipus").asText("goal")), x.path("nom").asText(""),
                    x.hasNonNull("objectiu") ? amount(x.get("objectiu")) : null, ym(x.path("per")),
                    amount(x.path("mensual")), amount(x.path("inicial")),
                    x.hasNonNull("des") ? LocalDate.parse(x.get("des").asText()) : null, x.path("arxivada").asBoolean(false)));
        }
        List<PotEntry> entries = new ArrayList<>();
        for (JsonNode e : p.path("aportacions")) {
            entries.add(new PotEntry(e.path("id").asText(), e.path("guardiola").asText(), LocalDate.parse(e.path("data").asText()),
                    amount(e.path("imp")), PotEntry.Kind.ofCode(e.path("tipus").asText("adjust")), e.path("nota").asText(""),
                    ym(e.path("cicle"))));
        }
        List<CycleClose> closes = new ArrayList<>();
        for (JsonNode c : p.path("tancaments")) {
            Map<Category, CycleClose.CategoryLine> cats = new java.util.EnumMap<>(Category.class);
            var it = c.path("categories").fields();
            while (it.hasNext()) {
                var e = it.next();
                Category.parse(e.getKey()).ifPresent(cat -> cats.put(cat,
                        new CycleClose.CategoryLine(amount(e.getValue().path("limit")), amount(e.getValue().path("gastat")))));
            }
            closes.add(new CycleClose(YearMonth.parse(c.path("cicle").asText()), LocalDate.parse(c.path("data").asText()),
                    amount(c.path("ingressos")), amount(c.path("extra")), amount(c.path("pressupost")),
                    amount(c.path("gastat")), amount(c.path("guardioles")), amount(c.path("sobrant")),
                    CycleClose.Leftover.ofCode(c.path("sobrantA").asText("savings")), cats));
        }
        JsonNode acc = p.path("compte");
        Account account = acc.hasNonNull("saldo") && acc.hasNonNull("data")
                ? new Account(amount(acc.get("saldo")), LocalDate.parse(acc.get("data").asText())) : null;
        List<AccountEntry> adjustments = new ArrayList<>();
        for (JsonNode e : acc.path("ajustos")) {
            adjustments.add(new AccountEntry(e.path("id").asText(), LocalDate.parse(e.path("data").asText()),
                    amount(e.path("imp")), e.path("nota").asText(""), ym(e.path("cicle"))));
        }
        return new Ledger(incomes, pots, entries, closes, account, adjustments);
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) && !n.get(field).asText().isEmpty() ? n.get(field).asText() : null;
    }

    private static Category category(JsonNode n) throws IOException {
        String c = n.path("cat").asText();
        return Category.parse(c).orElseThrow(() -> new IOException("Unknown category \"" + c + "\" in " + n));
    }

    private static BigDecimal amount(JsonNode n) {
        return n.isNumber() ? n.decimalValue() : BigDecimal.ZERO;
    }

    private static YearMonth ym(JsonNode n) {
        return n.isTextual() && !n.asText().isEmpty() ? YearMonth.parse(n.asText()) : null;
    }
}
