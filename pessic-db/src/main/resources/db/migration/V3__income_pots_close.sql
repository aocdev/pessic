-- Extra incomes and refunds, savings pots (annual reserve and goals), closed months, and the
-- number of paychecks a year. Existing rows are not changed.

-- 12 or 14 paychecks a year; the extra ones arrive in extra_pay_months ('6,12').
ALTER TABLE profile ADD COLUMN paychecks INTEGER NOT NULL DEFAULT 12 CHECK (paychecks IN (12, 14));
-- Amount of each extra paycheck; NULL = the same as the monthly income.
ALTER TABLE profile ADD COLUMN extra_pay_cents INTEGER;
ALTER TABLE profile ADD COLUMN extra_pay_months TEXT NOT NULL DEFAULT '6,12';

-- An expense paid from a savings goal does not use the monthly budget.
ALTER TABLE movement ADD COLUMN goal_id TEXT;

CREATE TABLE pot (
    profile_id    TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id            TEXT    NOT NULL,
    kind          TEXT    NOT NULL CHECK (kind IN ('reserve', 'goal')),
    name          TEXT    NOT NULL,
    target_cents  INTEGER,
    target_ym     TEXT,
    monthly_cents INTEGER NOT NULL DEFAULT 0,
    opening_cents INTEGER NOT NULL DEFAULT 0,
    since         TEXT,
    archived      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE pot_entry (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    pot_id       TEXT    NOT NULL,
    date         TEXT    NOT NULL,
    amount_cents INTEGER NOT NULL,
    kind         TEXT    NOT NULL CHECK (kind IN ('contribution', 'leftover', 'withdrawal', 'adjust')),
    note         TEXT    NOT NULL DEFAULT '',
    cycle_ym     TEXT,
    PRIMARY KEY (profile_id, id),
    FOREIGN KEY (profile_id, pot_id) REFERENCES pot (profile_id, id) ON DELETE CASCADE
);

CREATE TABLE income (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    date         TEXT    NOT NULL,
    concept      TEXT    NOT NULL DEFAULT '',
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    kind         TEXT    NOT NULL CHECK (kind IN ('extra_pay', 'bonus', 'refund', 'carry', 'other')),
    destination  TEXT    NOT NULL CHECK (destination IN ('budget', 'reserve', 'goal', 'savings', 'category')),
    category     TEXT REFERENCES category (code),
    pot_id       TEXT,
    scheduled_id TEXT,
    PRIMARY KEY (profile_id, id),
    CHECK ((destination = 'category') = (category IS NOT NULL))
);
CREATE INDEX income_by_date ON income (profile_id, date);

CREATE TABLE cycle_close (
    profile_id     TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    cycle_ym       TEXT    NOT NULL,
    closed_on      TEXT    NOT NULL,
    income_cents   INTEGER NOT NULL,
    extra_cents    INTEGER NOT NULL,
    budget_cents   INTEGER NOT NULL,
    spent_cents    INTEGER NOT NULL,
    to_pots_cents  INTEGER NOT NULL,
    leftover_cents INTEGER NOT NULL,
    leftover_to    TEXT    NOT NULL CHECK (leftover_to IN ('savings', 'goal', 'reserve', 'carry')),
    PRIMARY KEY (profile_id, cycle_ym)
);

CREATE TABLE cycle_close_category (
    profile_id  TEXT    NOT NULL,
    cycle_ym    TEXT    NOT NULL,
    category    TEXT    NOT NULL REFERENCES category (code),
    limit_cents INTEGER NOT NULL,
    spent_cents INTEGER NOT NULL,
    PRIMARY KEY (profile_id, cycle_ym, category),
    FOREIGN KEY (profile_id, cycle_ym) REFERENCES cycle_close (profile_id, cycle_ym) ON DELETE CASCADE
);
