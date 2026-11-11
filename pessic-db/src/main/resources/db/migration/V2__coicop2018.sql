-- Categories become stable COICOP 2018 codes ('01'..'13') instead of display names.
-- Display names live in the app (cat.pessic.core.Category) and are re-seeded on every start.
-- Rows with an unknown category name get code 'XX', which fails the foreign key check, so the
-- whole migration is rolled back rather than losing data.

CREATE TEMP TABLE legacy_category (name TEXT PRIMARY KEY, code TEXT NOT NULL);
INSERT INTO legacy_category (name, code) VALUES
    ('Alimentació', '01'),
    ('Alcohol i tabac', '02'),
    ('Vestit i calçat', '03'),
    ('Habitatge, aigua, llum i gas', '04'),
    ('Mobles i llar', '05'),
    ('Sanitat', '06'),
    ('Transport', '07'),
    ('Comunicacions', '08'),
    ('Oci, esport i cultura', '09'),
    ('Educació', '10'),
    ('Restaurants i hotels', '11'),
    ('Assegurances i serveis financers', '12'),
    ('Cura personal i altres', '13');

CREATE TABLE category_v2 (
    code       TEXT PRIMARY KEY,
    name       TEXT    NOT NULL,
    sort_order INTEGER NOT NULL
);
INSERT INTO category_v2 (code, name, sort_order) SELECT code, name, CAST(code AS INTEGER) FROM legacy_category;

CREATE TABLE category_limit_v2 (
    profile_id  TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    category    TEXT    NOT NULL REFERENCES category (code),
    limit_cents INTEGER NOT NULL,
    PRIMARY KEY (profile_id, category)
);
INSERT INTO category_limit_v2 (profile_id, category, limit_cents)
SELECT t.profile_id, COALESCE(l.code, 'XX'), t.limit_cents
FROM category_limit t LEFT JOIN legacy_category l ON l.name = t.category;

CREATE TABLE fixed_payment_v2 (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    concept      TEXT    NOT NULL,
    category     TEXT    NOT NULL REFERENCES category (code),
    amount_cents INTEGER NOT NULL,
    day          INTEGER NOT NULL CHECK (day BETWEEN 1 AND 31),
    from_ym      TEXT,
    to_ym        TEXT,
    is_extra     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_id, id)
);
INSERT INTO fixed_payment_v2 (profile_id, id, concept, category, amount_cents, day, from_ym, to_ym, is_extra)
SELECT t.profile_id, t.id, t.concept, COALESCE(l.code, 'XX'), t.amount_cents, t.day, t.from_ym, t.to_ym, t.is_extra
FROM fixed_payment t LEFT JOIN legacy_category l ON l.name = t.category;

CREATE TABLE annual_item_v2 (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    month        INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    concept      TEXT    NOT NULL,
    category     TEXT    NOT NULL REFERENCES category (code),
    amount_cents INTEGER NOT NULL,
    PRIMARY KEY (profile_id, id)
);
INSERT INTO annual_item_v2 (profile_id, id, month, concept, category, amount_cents)
SELECT t.profile_id, t.id, t.month, t.concept, COALESCE(l.code, 'XX'), t.amount_cents
FROM annual_item t LEFT JOIN legacy_category l ON l.name = t.category;

CREATE TABLE movement_v2 (
    profile_id   TEXT    NOT NULL REFERENCES profile (id) ON DELETE CASCADE,
    id           TEXT    NOT NULL,
    date         TEXT    NOT NULL,
    concept      TEXT    NOT NULL,
    category     TEXT    NOT NULL REFERENCES category (code),
    amount_cents INTEGER NOT NULL,
    from_reserve INTEGER NOT NULL DEFAULT 0,
    scheduled_id TEXT,
    PRIMARY KEY (profile_id, id)
);
INSERT INTO movement_v2 (profile_id, id, date, concept, category, amount_cents, from_reserve, scheduled_id)
SELECT t.profile_id, t.id, t.date, t.concept, COALESCE(l.code, 'XX'), t.amount_cents, t.from_reserve, t.scheduled_id
FROM movement t LEFT JOIN legacy_category l ON l.name = t.category;

DROP TABLE category_limit;
DROP TABLE fixed_payment;
DROP TABLE annual_item;
DROP TABLE movement;
DROP TABLE category;
ALTER TABLE category_v2 RENAME TO category;
ALTER TABLE category_limit_v2 RENAME TO category_limit;
ALTER TABLE fixed_payment_v2 RENAME TO fixed_payment;
ALTER TABLE annual_item_v2 RENAME TO annual_item;
ALTER TABLE movement_v2 RENAME TO movement;
CREATE INDEX movement_by_date ON movement (profile_id, date);
DROP TABLE legacy_category;

-- Household size, used for the INE reference (NULL = not given).
ALTER TABLE profile ADD COLUMN members INTEGER;
