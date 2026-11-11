package cat.pessic.app.income;

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
        TextField concept = new TextField("Paga extra de " + Format.monthName(expected.month().getMonthValue()).toLowerCase());
        TextField amount = Ui.amountField(expected.amount());
        DestinationPicker dest = new DestinationPicker(ctx.profile().ledger().activeGoals(), null, null);
        VBox form = new VBox(12, Ui.row(Ui.field("Data", date), Ui.field("Concepte", concept), Ui.field("Import (€)", amount)),
                dest.node(),
                Ui.wrapped("Si la poses al pressupost d'aquest mes, la pots gastar ara. Si la guardes a la reserva o en una "
                        + "guardiola, queda apartada per al que vindrà.", "small"));
        record Spec(LocalDate date, String concept, java.math.BigDecimal amount) {}
        EditDialog.<Spec>show(ctx.window(), "Has cobrat la paga extra", form, false, "", () -> {
            LocalDate d = date.getConverter().fromString(date.getEditor().getText());
            if (d == null) return EditDialog.Check.fail("Tria una data.");
            var imp = Ui.amount(amount).filter(v -> v.signum() > 0);
            if (imp.isEmpty()) return EditDialog.Check.fail("Escriu un import més gran que zero.");
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
