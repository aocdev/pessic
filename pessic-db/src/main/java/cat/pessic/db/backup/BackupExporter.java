package cat.pessic.db.backup;

import cat.pessic.core.budget.Category;
import cat.pessic.core.expense.Movement;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.FixedPayment;
import cat.pessic.core.profile.Profile;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;

/**
 * Writes Pessic's backup format v4: the old web app's structure with COICOP 2018 category codes
 * ("08"), the household size ({@code "membres"}), paychecks ({@code "pagues"}), extra incomes and
 * refunds ({@code "ingressos"}), savings pots ({@code "guardioles"} with their {@code "aportacions"})
 * closed months ({@code "tancaments"}) and the day-to-day account ({@code "compte"}). Read back by {@link BackupImporter}. The old web app
 * cannot load it.
 */
public final class BackupExporter {

    public static final int FORMAT_VERSION = 4;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

    public void write(OutputStream out, List<Profile> profiles, String activeProfileId) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", "pessic");
        root.put("v", FORMAT_VERSION);
        if (activeProfileId != null) root.put("active", activeProfileId);
        else root.putNull("active");
        ObjectNode all = root.putObject("profiles");
        for (Profile p : profiles) all.set(p.id(), profile(p));
        mapper.writeValue(out, root);
    }

    private ObjectNode profile(Profile p) {
        ObjectNode n = mapper.createObjectNode();
        n.put("nom", p.name());
        ObjectNode cfg = n.putObject("cfg");
        var c = p.config();
        cfg.put("ingressos", c.income());
        cfg.put("estalvi", c.savings());
        cfg.put("dia", c.payday());
        cfg.put("llar", c.household());
        if (c.members() != null) cfg.put("membres", c.members());
        ObjectNode pay = cfg.putObject("pagues");
        pay.put("n", c.paychecks().count());
        if (c.paychecks().extraAmount() != null) pay.put("imp", c.paychecks().extraAmount());
        ArrayNode months = pay.putArray("mesos");
        c.paychecks().months().forEach(months::add);
        ObjectNode limits = cfg.putObject("limits");
        for (Category cat : Category.ALL) limits.put(cat.code(), c.limit(cat));
        ArrayNode plan = cfg.putArray("plan");
        for (FixedPayment f : c.plan()) {
            ObjectNode o = plan.addObject();
            o.put("id", f.id());
            o.put("dia", f.day());
            o.put("c", f.concept());
            o.put("cat", f.category().code());
            o.put("imp", f.amount());
            if (f.from() != null) o.put("des", f.from().toString());
            if (f.to() != null) o.put("fins", f.to().toString());
            if (f.extra()) o.put("extra", true);
        }
        ArrayNode annuals = cfg.putArray("anuals");
        for (AnnualItem a : c.annuals()) {
            ObjectNode o = annuals.addObject();
            o.put("id", a.id());
            o.put("mes", a.month());
            o.put("c", a.concept());
            o.put("cat", a.category().code());
            o.put("imp", a.amount());
        }
        ArrayNode moves = n.putArray("moves");
        p.movements().stream().sorted(Comparator.comparing(Movement::date).thenComparing(Movement::id)).forEach(m -> {
            ObjectNode o = moves.addObject();
            o.put("id", m.id());
            o.put("data", m.date().toString());
            o.put("c", m.concept());
            o.put("cat", m.category().code());
            o.put("imp", m.amount());
            o.put("reserva", m.fromReserve());
            if (m.scheduledId() != null) o.put("pid", m.scheduledId());
            if (m.goalId() != null) o.put("guardiola", m.goalId());
        });
        var l = p.ledger();
        ArrayNode incomes = n.putArray("ingressos");
        for (var i : l.incomes()) {
            ObjectNode o = incomes.addObject();
            o.put("id", i.id());
            o.put("data", i.date().toString());
            o.put("c", i.concept());
            o.put("imp", i.amount());
            o.put("tipus", i.kind().code());
            o.put("desti", i.destination().code());
            if (i.category() != null) o.put("cat", i.category().code());
            if (i.potId() != null) o.put("guardiola", i.potId());
            if (i.scheduledId() != null) o.put("pid", i.scheduledId());
        }
        ArrayNode pots = n.putArray("guardioles");
        for (var x : l.pots()) {
            ObjectNode o = pots.addObject();
            o.put("id", x.id());
            o.put("tipus", x.kind().code());
            o.put("nom", x.name());
            if (x.target() != null) o.put("objectiu", x.target());
            if (x.targetMonth() != null) o.put("per", x.targetMonth().toString());
            o.put("mensual", x.monthly());
            o.put("inicial", x.opening());
            if (x.since() != null) o.put("des", x.since().toString());
            if (x.archived()) o.put("arxivada", true);
        }
        ArrayNode entries = n.putArray("aportacions");
        for (var e : l.entries()) {
            ObjectNode o = entries.addObject();
            o.put("id", e.id());
            o.put("guardiola", e.potId());
            o.put("data", e.date().toString());
            o.put("imp", e.amount());
            o.put("tipus", e.kind().code());
            if (!e.note().isEmpty()) o.put("nota", e.note());
            if (e.cycle() != null) o.put("cicle", e.cycle().toString());
        }
        ArrayNode closes = n.putArray("tancaments");
        for (var c2 : l.closes()) {
            ObjectNode o = closes.addObject();
            o.put("cicle", c2.cycle().toString());
            o.put("data", c2.closedOn().toString());
            o.put("ingressos", c2.income());
            o.put("extra", c2.extra());
            o.put("pressupost", c2.budget());
            o.put("gastat", c2.spent());
            o.put("guardioles", c2.toPots());
            o.put("sobrant", c2.leftover());
            o.put("sobrantA", c2.leftoverTo().code());
            ObjectNode cats = o.putObject("categories");
            c2.categories().forEach((cat, line) -> {
                ObjectNode x = cats.putObject(cat.code());
                x.put("limit", line.limit());
                x.put("gastat", line.spent());
            });
        }
        if (l.account() != null || !l.accountEntries().isEmpty()) {
            ObjectNode acc = n.putObject("compte");
            if (l.account() != null) {
                acc.put("saldo", l.account().opening());
                acc.put("data", l.account().since().toString());
            }
            ArrayNode adj = acc.putArray("ajustos");
            for (var e : l.accountEntries()) {
                ObjectNode o = adj.addObject();
                o.put("id", e.id());
                o.put("data", e.date().toString());
                o.put("imp", e.amount());
                if (!e.note().isEmpty()) o.put("nota", e.note());
                if (e.cycle() != null) o.put("cicle", e.cycle().toString());
            }
        }
        return n;
    }
}
