-- Amounts are stored as integer cents. Year-months as 'YYYY-MM', dates as 'YYYY-MM-DD'.

CREATE TABLE app_setting (
    key   TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE category (
    name       TEXT PRIMARY KEY,
    sort_order INTEGER NOT NULL
);

CREATE TABLE profile (
    id            TEXT PRIMARY KEY,
    name          TEXT    NOT NULL,
    income_cents  INTEGER NOT NULL,
    savings_cents INTEGER NOT NULL DEFAULT 0,
    payday        INTEGER NOT NULL CHECK (payday BETWEEN 1 AND 28),
    household     INTEGER NOT NULL DEFAULT 8,
    created_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE category_limit (
    profile_id  TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    category    TEXT    NOT NULL REFERENCES category (name),
    limit_cents INTEGER NOT NULL,
    PRIMARY KEY (profile_id, category)
);

CREATE TABLE fixed_payment (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    concept      TEXT    NOT NULL,
    category     TEXT    NOT NULL REFERENCES category (name),
    amount_cents INTEGER NOT NULL,
    day          INTEGER NOT NULL CHECK (day BETWEEN 1 AND 31),
    from_ym      TEXT,
    to_ym        TEXT,
    is_extra     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, id)
);

CREATE TABLE annual_item (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    month        INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    concept      TEXT    NOT NULL,
    category     TEXT    NOT NULL REFERENCES category (name),
    amount_cents INTEGER NOT NULL,
    PRIMARY KEY (profile_id, id)
);

-- scheduled_id is '<fixed_payment.id>@<cycle YYYY-MM>' when the movement pays a fixed payment.
CREATE TABLE movement (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    date         TEXT    NOT NULL,
    concept      TEXT    NOT NULL,
    category     TEXT    NOT NULL REFERENCES category (name),
    amount_cents INTEGER NOT NULL,
    from_reserve INTEGER NOT NULL DEFAULT 0,
    scheduled_id TEXT,
    PRIMARY KEY (profile_id, id)
);

CREATE INDEX movement_by_date ON movement (profile_id, date);
