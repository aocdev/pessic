package cat.pessic.app.payment;

import static cat.pessic.core.common.I18n.t;
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

        SimpleTable t = new SimpleTable(1, t("common.month"), t("common.payment"), t("common.category"), ">" + t("common.amount"), "");
        for (AnnualItem a : items) {
            t.add(SimpleTable.text(Format.monthName(a.month())), Ui.link(a.concept(), () -> edit(ctx, a)),
                    Ui.categoryText(a.category(), "muted"), SimpleTable.money(a.amount()),
                    Ui.rowActions(() -> edit(ctx, a), () -> remove(ctx, a)));
        }
        if (items.isEmpty()) {
            t.addMessage(t("payment.annuals.hint"));
        }
        t.addStyled("row-total", null, SimpleTable.text(t("payment.annuals.yearTotal")),
                SimpleTable.text(t("payment.annuals.perMonth", Format.eur(reserve)), "muted"), SimpleTable.money(total), null);

        int month = ctx.cycle().getMonthValue();
        List<AnnualItem> due = items.stream().filter(a -> a.month() == month).toList();
        String sub = t("payment.annuals.setAsideHint", Format.eur(reserve), (due.isEmpty() ? t("payment.annuals.noneThisMonth")
                : t("payment.annuals.dueThisMonth", String.join(", ", due.stream()
                        .map(a -> a.concept() + " (" + Format.eur(a.amount()) + ")").toList()))));

        Button add = Ui.primary(t("payment.annuals.add"));
        add.setOnAction(e -> edit(ctx, null));
        return Ui.page(Ui.card(Ui.sectionHead(t("common.annualReserve"), add), Ui.wrapped(sub, "muted"), t.node(),
                reserveNote(ctx),
                Ui.wrapped(t("payment.annuals.reserveHint"), "small")));
    }

    /** Real balance of the reserve and the first month it would not be enough, if any. */
    private static javafx.scene.Node reserveNote(AppContext ctx) {
        var p = ctx.profile();
        var reserve = p.ledger().reserve();
        if (!reserve.openingSet()) {
            return Ui.wrapped(t("payment.annuals.giveReserveBalance"),
                    "small");
        }
        String balance = t("payment.annuals.reserveBalance", Format.eur(Pots.balance(reserve, p)));
        var sf = Pots.reserveShortfall(p, ctx.today());
        return sf.isPresent()
                ? Ui.wrapped(t("payment.annuals.reserveShortfall", balance, Format.month(sf.get().cycle()).toLowerCase(), Format.eur(sf.get().missing())), "text-bad")
                : Ui.wrapped(t("payment.annuals.reserveEnough", balance), "small");
    }

    static void edit(AppContext ctx, AnnualItem existing) {
        TextField concept = new TextField(existing == null ? "" : existing.concept());
        concept.setPromptText(t("payment.annuals.conceptPrompt"));
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

        VBox form = new VBox(12, Ui.row(Ui.field(t("common.concept"), concept)),
                Ui.row(Ui.field(t("common.category"), category), Ui.field(t("common.amountEur"), amount), Ui.field(t("common.month"), month)),
                Ui.categoryHelp(category));
        String title = existing == null ? t("payment.annuals.newTitle") : t("payment.annuals.editTitle");
        EditDialog.<AnnualItem>show(ctx.window(), title, form, existing != null, t("common.deletePayment"), () -> {
            String c = concept.getText().strip();
            if (c.isEmpty()) return EditDialog.Check.fail(t("common.errorConcept"));
            var imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            if (imp.isEmpty()) return EditDialog.Check.fail(t("payment.annuals.errorAmount"));
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
        String text = t("payment.annuals.deleteQuestion", a.concept(), Format.eur(a.amount()), Format.monthName(a.month()).toLowerCase(), Format.eur(before), Format.eur(after));
        if (Ui.choose(ctx.window(), t("payment.annuals.deleteTitle", a.concept()), text, 0, t("payment.annuals.deleteConfirm")) == 0) {
            ctx.update(p -> PaymentOps.deleteAnnual(p, a.id()));
        }
    }
}
