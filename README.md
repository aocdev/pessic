# Pessic

*Un pessic* is Catalan for a pinch, a small piece of something. That is the whole idea of the
app: you don't manage your money all at once, you manage it one pinch at a time, so each small
expense has its place in a monthly budget.

Pessic is the Java desktop successor of the *Pressupost mensual* web app (`../pressupost`).
It keeps data in an embedded SQLite database instead of the browser's `localStorage`, and compares
your budget with what similar households spend according to INE (Spain's statistics office).

**For users:** [MANUAL.md](MANUAL.md) (how to use Pessic, in Catalan) and [WHATS_NEW.md](WHATS_NEW.md)
(what's new). **For developers:** this file and [CHANGELOG.md](CHANGELOG.md).

## Modules

| Module            | Contents                                                                    |
|-------------------|-----------------------------------------------------------------------------|
| `pessic-core` | Domain model and budgeting rules (pay cycles, plan, envelopes, weekly limits). No dependencies. |
| `pessic-db`   | SQLite storage, schema migrations, import of the PWA's JSON backup.         |
| `pessic-app`  | JavaFX UI.                                                                  |

### Packages: by feature

Inside each module the code is grouped by what the app does, not by technical role. The same feature
names repeat across modules, so a feature's rules, storage and screen are easy to find:

| Feature        | `pessic-core` (`cat.pessic.core.…`)                         | `pessic-app` (`cat.pessic.app.…`)          |
|----------------|-------------------------------------------------------------|--------------------------------------------|
| `budget`       | `BudgetConfig`, `BudgetCalculator`, `CycleSummary`, `PayCycle`, `Category`, `BudgetOps` | `LimitsView`        |
| `expense`      | `Movement`, `MovementFilter`, `ExpenseOps`                  | `ExpenseForm`, `MovementsView`             |
| `payment`      | `FixedPayment`, `AnnualItem`, `ScheduledPayment`, `PaymentOps` | `PlanView`, `AnnualsView`               |
| `income`       | `Income`, `Paychecks`, `IncomeOps`                          | `IncomeDialog`, `DestinationPicker`, `PaychecksFields` |
| `savings`      | `Pot`, `PotEntry`, `Pots`, `SavingsOps`                     | `SavingsView`                              |
| `account`      | `Account`, `AccountEntry`, `Cash`, `AccountOps`             | `AccountUi`                                |
| `monthclose`   | `CycleClose`, `MonthClose`                                  | `MonthCloseDialog`                         |
| `history`      | `History`                                                   | `HistoryView`                              |
| `profile`      | `Profile`, `Ledger`, `NewProfile`                           | `ProfileForm`                              |
| `ine`          | `IneReference`, `IneLimits`, `Catalog`                      |                                            |
| `export`       | `CsvExport`                                                 |                                            |
| `dashboard`    |                                                             | `DashboardView`, `EnvelopeBar`             |
| `settings`     |                                                             | `SettingsView`                             |
| `common`       | `Money`, `AmountParser`, `Ids`, `Checks`, `Lists`           | `Ui`, `Format`, `EditDialog`, `SimpleTable`, `MonthBox` |

The app's shell (`PessicApp`, `AppContext`, `AppPaths`, `Launcher`) stays in `cat.pessic.app`. In
`pessic-db` the same feature names hold each feature's tables: `ExpenseStore`, `PaymentStore`, `BudgetStore`,
`IncomeStore`, `SavingsStore`, `MonthCloseStore` and `AccountStore` read and write their own rows, and
`profile.ProfileRepository` keeps the `profile` row and saves a whole profile through the stores in one
transaction. `cat.pessic.db` holds the database, migrations and the `Jdbc` helper; `cat.pessic.db.backup`
the daily copies and the JSON export/import.
Tests sit in the same package as the code they test.

## Build and run

Requires JDK 21 and Maven 3.9.

```bash
mvn install                                   # build + all tests
mvn -pl pessic-app javafx:run             # start the app
PESSIC_DB=/tmp/test.db mvn -pl pessic-app javafx:run   # use a different database
PESSIC_DB=/tmp/test.db PESSIC_TODAY=2026-12-01 mvn -pl pessic-app javafx:run   # pretend it is another day
```

The database lives at:

- Linux: `$XDG_DATA_HOME/pessic/pessic.db` (default `~/.local/share/pessic/`)
- macOS: `~/Library/Application Support/Pessic/pessic.db`
- Windows: `%APPDATA%\Pessic\pessic.db`

**Automatic backups**: every day the app is opened, a consistent copy (`VACUUM INTO`) is written to
`backups/pessic-YYYY-MM-DD.db` next to the database, keeping the newest 10 (`Backups`). Configuració
lists them and can restore one; the data being replaced is saved first as `pessic-before-restore-*.db`.

On first start, Pessic asks for a name, net monthly income, payday, household type and whether the
salary comes in 12 or 14 payments, and can
propose starting limits from the INE averages for that household. People who already use the web
app can import its backup (`.json`) from the same screen instead. More profiles can be added later
with **Perfil nou…**.

## Categories and INE reference

- **Categories are the 13 COICOP 2018 groups** that INE uses in the Encuesta de Presupuestos Familiares
  (EPF) from 2024 on (`Category`, codes `01`–`13`). The database and backups store the code; the Catalan
  names, a description and examples (shown as help in every category picker) live in the enum.
  For example, phone, internet and digital subscriptions go in **08 Informació i comunicacions**, while
  **04 Habitatge** is rent, water, energy and home repairs.
- **INE reference data** is bundled in `pessic-core/src/main/resources/ine/epf.tsv` (EPF 2025, average
  spending per household and year, by COICOP group), generated from INE's Tempus API by
  `python3 tools/ine/fetch_epf.py` (tables 73807 household type, 73806 household size, 73809 net monthly
  income). Run it again after INE publishes a new EPF (usually in June) and commit the file.
- **Expectation per category** (`IneReference`): the mean of the monthly figures for households of the
  same type, the same number of members and the same income band. Breakdowns that are unknown, or that
  INE does not publish for a category, are left out of the mean.
- **Proposed limits** (`IneLimits`): the expectation minus what the annual reserve already covers for
  that category; if the total does not fit in income − automatic savings − reserve, every limit is
  scaled down by the same factor.
- **All 13 envelopes are always shown**, with the INE expectation as a marker. Annual payments appear in
  their category (note + striped segment in the month they are due) and in the payment plan with
  "Pagat amb la reserva"; they never consume the monthly limit.

## Design notes

- **Money** is `BigDecimal` at scale 2 in the model and integer cents in the database.
  Divisions (annual reserve / 12, weekly limits) keep full precision and round only for display.
- **Pay cycle**: runs from payday to the day before the next payday. With payday after the
  15th, the cycle is named after the following month. See `PayCycle`.
- **Cycle membership is computed, not stored**: changing payday reassigns movements, as in the PWA.
- **Migrations**: `pessic-db/src/main/resources/db/migration/V<n>__*.sql`, registered in
  `Database.MIGRATIONS`, version tracked in `PRAGMA user_version`. Never edit a released migration.
  Before upgrading an existing file, Pessic copies it to `pessic.db.bak-v<old version>`. Foreign keys are
  checked before each migration commits; a violation rolls the whole migration back.

## Texts and translations

Every text the user sees lives in `pessic-core/src/main/resources/i18n/strings_ca.properties` (UTF-8),
read through `I18n` (`cat.pessic.core.common`): `t("payment.plan.add")`, or with values
`t("account.leftInAccount", Format.eur(x))` for `Et quedaran {0} al compte`. Placeholders are
replaced as plain text, so apostrophes need no escaping. `plural(key, n)` picks `key.one` or `key.other`.

- Keys start with the feature (`dashboard.`, `payment.plan.`, `savings.`…); texts used on several screens are
  under `common.`. Category names, enum labels, household types and month prepositions ("d'octubre") are
  built from codes: `category.08.name`, `income.kind.BONUS`, `ine.household.2`, `format.ofMonth.10`.
- `I18nTest` fails if the code asks for a missing key, if the file has an unused key, or if a call passes a
  different number of values than its text has placeholders.
- Only Catalan exists. A translation is a copy of the file (`strings_es.properties`) with the same keys
  plus a call to `I18n.use(locale)` at start-up. Not translated: CSS class names, file names, log lines,
  and developer-facing exception details (English), and notes already saved in a profile.

## Parity with the PWA

`tools/js-oracle.js` runs the original JavaScript formulas headless with Node, and its output
is stored as `pessic-db/src/test/resources/golden-*.psv`. `BackupImportTest` checks that the
Java port gives the same numbers for every cycle. To regenerate after changing the sample:

```bash
node tools/js-oracle.js pessic-db/src/test/resources/backup-sample.json 2026-11-09
```

`backup-sample.json` is an invented profile ("Marta"): no real personal data. Keep it that way.

One known difference: when two fixed payments fall on the same day, the PWA lists them in the order
they were created and Pessic alphabetically. Totals are the same; only the running balance between
those two lines differs, so the sample keeps same-day payments in alphabetical order.

## Screens

| Tab | What you do there |
|---|---|
| **Resum** | Banner to close an ended month; "Propers pagaments" (fixed payments overdue or due in 3 days, annual payments and extra paycheck of the month, reserve shortfall); key figures; money left per day; the "Apunta" form; envelopes; weekly limit; the month's rules |
| **Moviments** | "Apunta" an expense (paid from the month, the reserve or a goal), an extra income (asks where it goes: this month's budget, the reserve, a goal or savings) or a refund (back to its category); search by text (accents ignored), category, type and period, with totals |
| **Pagaments fixos** | This cycle's payment plan with running balance: mark as paid / undo; the expected extra paycheck ("Rebuda"); annual payments of the month ("Pagat amb la reserva"); add, edit or end fixed payments |
| **Anuals** | Once-a-year payments, the monthly amount to set aside and whether the reserve will be enough |
| **Estalvi** | Real balance of the annual reserve (starting balance, contributions, annual payments, adjustments), the first month it would fall short, coming annual payments; savings goals ("guardioles") with target, date, monthly amount and progress |
| **Límits** | Monthly limit per category next to the INE average, live total and projected savings (after reserve and goals) |
| **Històric** | Months since the first data (up to 12): income, spent, set aside and saved, as a chart and a table; one category against its limit and INE over time; reopen a closed month; CSV |
| **Configuració** | Name, income, automatic savings, payday, household, 12/14 paychecks; data location, JSON/CSV export and import; automatic backups and restore; delete profile |

### Money that is not salary or an expense

- **Extra income** (`Income`): extra paychecks, bonuses, money paid back. Its destination is chosen every
  time: the month's budget (raises what can be spent), the reserve or a goal (raises that pot), or savings.
- **Refunds** are incomes that go back to a category: they lower that category's spending in the month.
- **14 paychecks** (`Paychecks`) only plan: the extra paycheck shows up in the cycle containing payday of
  its month and is recorded when it arrives, so changing jobs never rewrites past months.
- **Pots** (`Pot`, `Pots`): the annual reserve and goals. Balance = what it held on `since` + entries +
  incomes sent to it − expenses paid from it. Goals' monthly amounts are set aside like the reserve.
- **Month close** (`MonthClose`): after a cycle ends, posts the monthly contributions to the reserve and
  goals (editable), sends a positive leftover to savings, the reserve, a goal or the next month, and stores
  a snapshot (`CycleClose`) so history keeps that month's limits. A close can be undone ("Reobre").
- **The day-to-day account** (`Account`, `Cash`): only the account where the salary arrives. The user gives
  its balance once (first-run form, Resum or Configuració) and Pessic estimates it from then on: + salary
  and − automatic savings and the reserve/goal transfers on each payday (real contributions once the
  cycle is closed), − expenses not paid from a pot, + incomes that stay in the account, ± corrections
  ("Ajusta amb el banc", or the bank balance asked at month close; the difference shows in Històric).
  "Pagaments fixos" turns its balance column into a forecast until payday (pending payments plus the
  rest of the budget) and warns when it would go below zero. When the balance is first given, Pessic
  offers to move part of it into the reserve (`Pots.reserveNeededNow`) and goals. Savings and
  investment accounts are not tracked: money sent there is an outflow.
- **No bank import**: considered and dropped. Pessic is for budgeting, not for analysing bank accounts,
  and the banks' old `.xls` exports would need Apache POI plus a preset per bank.

All edits go through the `…Ops` class of their feature (`ExpenseOps`, `PaymentOps`, `SavingsOps`…, in
core and unit-tested) and are saved immediately.

## Export

- **JSON backup** (Configuració): all profiles in Pessic's format v4 (`BackupExporter`): the web app's
  structure with COICOP codes, household size, paychecks (`pagues`), extra incomes and refunds
  (`ingressos`), pots and their entries (`guardioles`, `aportacions`), closed months (`tancaments`) and
  the day-to-day account (`compte`), i.e. format v4. The old web app cannot read it; Pessic still imports
  v1 (web app), v2 and v3 backups (`BackupImporter`).
- **CSV** (`CsvExport`): movements of the cycle (Moviments tab) or of all time (Configuració), with a
  "Tipus" column (despesa / ingrés / devolució); the per-category summary of the cycle (Resum); and the
  history (Històric). Made for Excel/LibreOffice in Catalan/Spanish:
  `;` separator, decimal comma, `dd/MM/yyyy`, UTF-8 with BOM. Text that a spreadsheet would run as a formula is neutralised.
- Files are written to a temporary file and then moved into place, so a failed export never leaves a half-written file.

## Roadmap

1. Optional: suggest updating limits yearly with INE's CPI by COICOP group (table 76125).
2. ES/EN translations: copy `strings_ca.properties`, translate it, and add a language setting.
3. Installers with `jpackage` (`.msi`, `.dmg`, `.deb`/`.rpm`) via a CI matrix.
