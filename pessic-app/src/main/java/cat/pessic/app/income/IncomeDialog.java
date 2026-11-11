package cat.pessic.app.income;

import static cat.pessic.core.common.I18n.t;
import cat.pessic.app.AppContext;
import cat.pessic.app.common.EditDialog;
import cat.pessic.app.common.Format;
import cat.pessic.app.common.Ui;
import cat.pessic.core.budget.CycleSummary;
import cat.pessic.core.income.Income;
import cat.pessic.core.income.IncomeOps;
import java.time.LocalDate;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Records an expected extra paycheck when it arrives ("Rebuda"), asking where the money goes. */
public final class IncomeDialog {
    private IncomeDialog() {}

    public static void paycheck(AppContext ctx, CycleSummary.ExpectedPaycheck expected) {
        var cycle = ctx.summary().cycle();
        var date = Ui.datePicker(cycle.contains(ctx.today()) ? ctx.today() : cycle.start());
        TextField concept = new TextField(t("income.paycheck.concept", Format.monthName(expected.month().getMonthValue()).toLowerCase()));
        TextField amount = Ui.amountField(expected.amount());
        DestinationPicker dest = new DestinationPicker(ctx.profile().ledger().activeGoals(), null, null);
        VBox form = new VBox(12, Ui.row(Ui.field(t("common.date"), date), Ui.field(t("common.concept"), concept), Ui.field(t("common.amountEur"), amount)),
                dest.node(),
                Ui.wrapped(t("income.paycheck.hint"), "small"));
        record Spec(LocalDate date, String concept, java.math.BigDecimal amount) {}
        EditDialog.<Spec>show(ctx.window(), t("income.paycheck.title"), form, false, "", () -> {
            LocalDate d = date.getConverter().fromString(date.getEditor().getText());
            if (d == null) return EditDialog.Check.fail(t("common.errorPickDate"));
            var imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            if (imp.isEmpty()) return EditDialog.Check.fail(t("income.paycheck.errorAmount"));
            String bad = dest.check();
            if (bad != null) return EditDialog.Check.fail(bad);
            return EditDialog.Check.ok(new Spec(d, concept.getText().strip(), imp.get()));
        }).ifPresent(r -> {
            if (r instanceof EditDialog.Save<Spec>(Spec s)) {
                ctx.update(p -> IncomeOps.addIncome(p, s.date(), s.concept(), s.amount(), Income.Kind.EXTRA_PAY,
                        dest.value(), null, dest.goalId(), expected.scheduledId()));
            }
        });
    }
}
