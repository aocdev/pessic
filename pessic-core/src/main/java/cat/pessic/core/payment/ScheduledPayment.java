package cat.pessic.core.payment;

import java.time.LocalDate;

/** A fixed payment placed on a concrete date inside a cycle. */
public record ScheduledPayment(FixedPayment payment, LocalDate date, String scheduledId) {}
