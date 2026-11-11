-- The day-to-day account (where the salary arrives): the balance the user gave and the corrections
-- made when checking it against the bank. Existing rows are not changed.

CREATE TABLE account (
    profile_id    TEXT    PRIMARY KEY REFERENCES profile (id) ON DELETE CASCADE,
    opening_cents INTEGER NOT NULL,
    since         TEXT    NOT NULL
);

CREATE TABLE account_entry (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    date         TEXT    NOT NULL,
    amount_cents INTEGER NOT NULL,
    note         TEXT    NOT NULL DEFAULT '',
    cycle_ym     TEXT,
    PRIMARY KEY (profile_id, id)
);
