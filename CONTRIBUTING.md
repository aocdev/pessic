# Contributing to Pessic

First off, thank you for considering contributing to Pessic! Every contribution helps people keep their monthly budget under control.

## Code of Conduct

This project adheres to the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). By participating, you are expected to uphold this code. Please report unacceptable behavior via [GitHub Issues](https://github.com/aocdev/pessic/issues).

## How Can I Contribute?

### Reporting Bugs

Before creating a bug report, please check [existing issues](https://github.com/aocdev/pessic/issues) to avoid duplicates.

When filing a bug, include:

- **Pessic version** and **operating system** (and how you installed it: `.msi`, `.dmg`, `.deb`, `.rpm`, or built from source)
- **Steps to reproduce**: the screen, what you did, and the dates involved (many calculations depend on payday and the current date)
- **Expected vs actual** result (a screenshot helps)
- **Error message**, if any

**Never attach your real budget, database (`pessic.db`) or backup (`.json`)**: they contain your personal finances. If a bug only happens with your data, reproduce it with an invented profile first.

### Suggesting Features

Open a [GitHub Discussion](https://github.com/aocdev/pessic/discussions) with:

- **Use case**: What problem does this solve in your monthly budget?
- **Proposed behavior**: Which screen, and how would you use it?
- **Example**: Numbers or a sketch of what Pessic should show.

Pessic is for **planning and following a monthly budget**, not for analysing bank accounts. Bank statement import, for example, was considered and deliberately left out (see the [README](README.md)).

### Translations

All the texts of the app are in [`strings_ca.properties`](pessic-core/src/main/resources/i18n/strings_ca.properties) (Catalan). A translation is a copy of that file with the same keys, such as `strings_es.properties`. Open an issue first so we can agree on how users will choose the language.

### Submitting Code

We welcome pull requests for bug fixes, new features, translations and documentation improvements.

## Development Setup

### Prerequisites

- **Java 21+** (LTS, compilation target is 21)
- **Git**

Maven is **not** required to be installed. The project includes the [Maven Wrapper](https://maven.apache.org/wrapper/) (`mvnw`), which automatically downloads the correct Maven version (3.9.9).

### Building the Project

```bash
git clone https://github.com/aocdev/pessic.git
cd pessic
./mvnw clean install
```

The project is a multi-module Maven build:

```
pessic/
  pessic-core/   # budgeting rules, no dependencies (cat.pessic.core.<feature>)
  pessic-db/     # SQLite storage, migrations, JSON backups (cat.pessic.db.<feature>)
  pessic-app/    # JavaFX user interface (cat.pessic.app.<feature>)
  packaging/     # jpackage installers
```

### Running the App

```bash
./mvnw -pl pessic-app javafx:run
```

**While developing, never use your own data**: point Pessic to a throwaway database, and pretend it is another day when you need to test a month close:

```bash
PESSIC_DB=/tmp/pessic-dev.db ./mvnw -pl pessic-app javafx:run
PESSIC_DB=/tmp/pessic-dev.db PESSIC_TODAY=2026-12-01 ./mvnw -pl pessic-app javafx:run
```

### Running Tests

```bash
# Full build with all tests
./mvnw clean verify

# Only one module
./mvnw -pl pessic-core test
```

The tests include `I18nTest` (every text key exists and is used), migration tests from every schema version, and parity tests against the original budget formulas (`BackupImportTest`).

## Branching Strategy (GitHub Flow)

We use [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow):

1. **`main`** is always releasable
2. Create a **feature branch** from `main`
3. Open a **Pull Request** when ready
4. After review and CI passing, merge to `main`

### Branch Naming

```
feat/short-description      # New feature
fix/short-description       # Bug fix
docs/short-description      # Documentation only
refactor/short-description  # Code refactoring
i18n/short-description      # Texts and translations
```

Examples:

```
feat/goal-deadline-reminder
fix/weekly-limit-last-week
docs/manual-month-close
i18n/spanish-translation
```

## Commit Convention

We follow [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

### Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only changes (README, MANUAL, CHANGELOG) |
| `style` | Code style (formatting, no logic change) |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `test` | Adding or correcting tests |
| `build` | Changes to the build, dependencies or installers (Maven, jpackage) |
| `ci` | CI configuration changes (GitHub Actions, workflows) |
| `chore` | Other changes that don't modify src or test files |
| `revert` | Reverts a previous commit |

### Scopes (optional)

- `core` - Budgeting rules in `pessic-core`
- `db` - Storage, migrations and backups in `pessic-db`
- `app` - User interface in `pessic-app`
- `i18n` - Texts in `strings_*.properties`
- `packaging` - Installers (`packaging/`)
- The feature name when it is clearer: `budget`, `expense`, `payment`, `income`, `savings`, `account`, `monthclose`, `history`

### Examples

```
feat(savings): warn when a goal will not be reached by its date

fix(monthclose): keep the leftover when reopening a month twice

docs: explain the payroll account in the manual

refactor(db): move pot queries to SavingsStore

i18n: add Spanish translation

build(packaging): bundle jdk.localedata for month names

feat(db)!: store amounts per currency

BREAKING CHANGE: backups use format v5; v4 still imports
```

### Breaking Changes

Append `!` after the type/scope and add a `BREAKING CHANGE:` footer:

```
feat(db)!: change the backup format

BREAKING CHANGE: older versions of Pessic cannot read v5 backups
```

## Versioning

We follow [Semantic Versioning 2.0.0](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

| Increment | When |
|-----------|------|
| **MAJOR** | Breaking changes: a backup format older versions cannot read, removed features |
| **MINOR** | New features or non-breaking enhancements |
| **PATCH** | Bug fixes, texts, documentation, internal refactoring |

The version is derived from Conventional Commits:

- `feat` commits trigger a **MINOR** bump
- `fix` commits trigger a **PATCH** bump
- `BREAKING CHANGE` footer triggers a **MAJOR** bump

Releases are made by pushing a tag such as `v1.1.0`: the [Installers workflow](.github/workflows/installers.yml) builds the installers for every system and publishes them.

## Pull Request Process

### Before Submitting

1. **Create an issue first** for non-trivial changes
2. **Branch from `main`**: `git checkout -b feat/my-feature main`
3. **Follow commit conventions** described above
4. **Ensure the build passes**: `./mvnw clean verify`
5. **Add/update tests** for any change in the budgeting rules (`pessic-core`) or storage (`pessic-db`)
6. **Update documentation**: `MANUAL.md` for anything users see, `CHANGELOG.md` under *Unreleased*

### Review Criteria

PRs will be reviewed for:

- **Correctness**: Budget figures are right to the cent (amounts are `BigDecimal`, cents in the database)
- **Consistency**: Code goes in the package of its feature; texts go in the strings file
- **Data safety**: Existing databases and backups keep working (see below)
- **No personal data**: Test data, screenshots and examples are invented

## Architecture Guidelines

When contributing code, keep these principles in mind:

### Package by feature

Each module groups its code by what the app does, with the same names everywhere: the rules of savings goals are in `cat.pessic.core.savings`, their storage in `cat.pessic.db.savings`, their screen in `cat.pessic.app.savings`. Put new code in the package of its feature; `common` is only for what every feature shares.

### Rules (`pessic-core`)

- Plain Java, no dependencies (no JavaFX, no SQL)
- Profiles are immutable: every change goes through the feature's `…Ops` class (`ExpenseOps`, `SavingsOps`…), which returns the new profile
- Money is `BigDecimal` at scale 2; divide with `Money` and round only for display

### Storage (`pessic-db`)

- Each feature has a store (`ExpenseStore`, `SavingsStore`…) that reads and writes its own tables
- **Never edit a released migration.** Add `V<n>__description.sql` and register it in `Database.MIGRATIONS`; existing databases are backed up before upgrading
- A change to the backup format needs a new format version, and the importer must keep reading every older one

### Texts (`pessic-app` and `pessic-core`)

- **No user-visible text in the code.** Add a key to `strings_ca.properties` and use `t("feature.key", values…)` or `plural("feature.key", n)`; placeholders are `{0}`, `{1}`…
- Keys are English and start with the feature: `savings.goal.deleteQuestion`, `payment.plan.add`
- `I18nTest` fails if a key is missing, unused, or gets the wrong number of values

### Test data

- `backup-sample.json` and every other fixture are **invented** (the profile "Marta"). Never commit a real budget, even "anonymised".

## Getting Help

- **Questions**: Open a [GitHub Discussion](https://github.com/aocdev/pessic/discussions)
- **Bugs**: Open a [GitHub Issue](https://github.com/aocdev/pessic/issues)
- **Feature ideas**: Open a [GitHub Discussion](https://github.com/aocdev/pessic/discussions) first

Thank you for contributing!
