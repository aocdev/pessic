package cat.pessic.app.payment;

import cat.pessic.app.AppContext;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.SimpleTable;
import cat.pessic.app.common.Ui;
import cat.pessic.core.budget.BudgetCalculator;
import cat.pessic.core.budget.Category;
import cat.pessic.core.common.Ids;
import cat.pessic.core.common.Money;
import cat.pessic.core.payment.AnnualItem;
import cat.pessic.core.payment.PaymentOps;
import cat.pessic.core.savings.Pots;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** "Anuals": once-a-year payments and the monthly amount to set aside for them. */
public final class AnnualsView {
    private AnnualsView() {}

    public static Region build(AppContext ctx) {
        List<AnnualItem> items = ctx.profile().config().annuals().stream()
                .sorted(Comparator.comparingInt(AnnualItem::month).thenComparing(AnnualItem::concept)).toList();
        BigDecimal reserve = ctx.summary().monthlyReserve();
        BigDecimal total = items.stream().map(AnnualItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        SimpleTable t = new SimpleTable(1, "Mes", "Pagament", "Categoria", ">Import", "");
        for (AnnualItem a : items) {
            t.add(SimpleTable.text(Format.monthName(a.month())), Ui.link(a.concept(), () -> edit(ctx, a)),
                    Ui.categoryText(a.category(), "muted"), SimpleTable.money(a.amount()),
                    Ui.rowActions(() -> edit(ctx, a), () -> remove(ctx, a)));
        }
        if (items.isEmpty()) {
            t.addMessage("Afegeix aquí els pagaments d'un cop l'any (assegurances, IBI, matrícules, subscripcions anuals…). "
                    + "Pessic calcula quant has d'apartar cada mes.");
        }
        t.addStyled("row-total", null, SimpleTable.text("Total anual"),
                SimpleTable.text(Format.eur(reserve) + " al mes", "muted"), SimpleTable.money(total), null);

        int month = ctx.cycle().getMonthValue();
        List<AnnualItem> due = items.stream().filter(a -> a.month() == month).toList();
        String sub = "Aparta " + Format.eur(reserve) + " cada mes en un compte a part. "
                + (due.isEmpty() ? "Aquest mes no toca cap pagament anual."
                : "Aquest mes toca: " + String.join(", ", due.stream()
                        .map(a -> a.concept() + " (" + Format.eur(a.amount()) + ")").toList()) + ".");

        Button add = Ui.primary("Afegeix un pagament anual");
        add.setOnAction(e -> edit(ctx, null));
        return Ui.page(Ui.card(Ui.sectionHead("Reserva d'anuals", add), Ui.wrapped(sub, "muted"), t.node(),
                reserveNote(ctx),
                Ui.wrapped("Quan arribi el pagament, marca'l com a «Pagat amb la reserva» a «Pagaments fixos»: "
                        + "surt de la reserva i no compta al límit del mes.", "small")));
    }

    /** Real balance of the reserve and the first month it would not be enough, if any. */
    private static javafx.scene.Node reserveNote(AppContext ctx) {
        var p = ctx.profile();
        var reserve = p.ledger().reserve();
        if (!reserve.openingSet()) {
            return Ui.wrapped("Digues quant tens ara a la reserva a «Estalvi» i Pessic t'avisarà si algun mes no n'hi ha prou.",
                    "small");
        }
        String balance = "Saldo de la reserva avui: " + Format.eur(Pots.balance(reserve, p)) + ".";
        var sf = Pots.reserveShortfall(p, ctx.today());
        return sf.isPresent()
                ? Ui.wrapped(balance + " Al " + Format.month(sf.get().cycle()).toLowerCase() + " hi faltaran "
                        + Format.eur(sf.get().missing()) + ".", "text-bad")
                : Ui.wrapped(balance + " N'hi ha prou per als anuals dels pròxims 12 mesos.", "small");
    }

    static void edit(AppContext ctx, AnnualItem existing) {
        TextField concept = new TextField(existing == null ? "" : existing.concept());
        concept.setPromptText("Assegurança de la casa, IBI…");
        ComboBox<Category> category = Ui.categoryBox(existing == null ? Category.INSURANCE_FINANCE : existing.category());
        TextField amount = Ui.amountField(existing == null ? null : existing.amount());
        ComboBox<Integer> month = new ComboBox<>();
        month.getItems().setAll(IntStream.rangeClosed(1, 12).boxed().toList());
        month.setValue(existing == null ? ctx.cycle().getMonthValue() : existing.month());
        month.setVisibleRowCount(12);
        month.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer m) { return m == null ? "" : Format.monthName(m); }
            @Override public Integer fromString(String s) { return null; }
        });

        VBox form = new VBox(12, Ui.row(Ui.field("Concepte", concept)),
                Ui.row(Ui.field("Categoria", category), Ui.field("Import (€)", amount), Ui.field("Mes", month)),
                Ui.categoryHelp(category));
        String title = existing == null ? "Nou pagament anual" : "Edita el pagament anual";
        EditDialog.<AnnualItem>show(ctx.window(), title, form, existing != null, "Esborra aquest pagament", () -> {
            String c = concept.getText().strip();
            if (c.isEmpty()) return EditDialog.Check.fail("Escriu un concepte.");
            var imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            if (imp.isEmpty()) return EditDialog.Check.fail("Escriu un import més gran que zero.");
            return EditDialog.Check.ok(new AnnualItem(existing == null ? Ids.next("a") : existing.id(),
                    month.getValue(), c, category.getValue(), imp.get()));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<AnnualItem>(AnnualItem value)) ctx.update(p -> PaymentOps.saveAnnual(p, value));
            else if (existing != null) remove(ctx, existing);
        });
    }

    /** Deleting an annual payment lowers the monthly reserve; say by how much before doing it. */
    static void remove(AppContext ctx, AnnualItem a) {
        BigDecimal before = ctx.summary().monthlyReserve();
        BigDecimal after = Money.round(new BudgetCalculator(
                PaymentOps.deleteAnnual(ctx.profile(), a.id()).config()).monthlyReserve());
        String text = "Ja no pagaràs «" + a.concept() + "» (" + Format.eur(a.amount()) + " al "
                + Format.monthName(a.month()).toLowerCase() + ")? La reserva que aparts cada mes passarà de "
                + Format.eur(before) + " a " + Format.eur(after) + ".";
        if (Ui.choose(ctx.window(), "Esborra «" + a.concept() + "»", text, 0, "Esborra'l") == 0) {
            ctx.update(p -> PaymentOps.deleteAnnual(p, a.id()));
        }
    }
}
