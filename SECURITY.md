# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x     | Yes       |

## Scope

Pessic is a **desktop application that works offline**. It has no server, no user accounts and no network features: every budget is stored in a local SQLite database on the user's computer (`~/.local/share/pessic/`, `~/Library/Application Support/Pessic/` or `%APPDATA%\Pessic\`), with daily copies next to it.

Its attack surface is limited to the files it reads and writes. Potential security concerns include:

- **Malicious backup files**: importing a crafted `.json` backup (parsing, oversized values, unexpected structures)
- **Malicious database files**: restoring a crafted `.db` copy from the backups folder
- **Spreadsheet formula injection** in exported CSV files (Pessic neutralises cells that start with `=`, `+`, `-` or `@`)
- **File handling**: exports, backups and restores writing outside the expected folders, or leaving partial files
- **Dependency vulnerabilities** in SQLite JDBC, Jackson, OpenJFX or the Java runtime bundled in the installers
- **Installer integrity**: each release publishes `SHA256SUMS.txt` and, for public releases, signed build attestations (`gh attestation verify <file> --repo aocdev/pessic`). The installers are not code-signed.

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **Do NOT open a public issue**
2. Use [GitHub Security Advisories](https://github.com/aocdev/pessic/security/advisories/new) to report it privately
3. Include:
   - Description of the vulnerability
   - Steps to reproduce (with an invented profile, never real financial data)
   - Potential impact
   - Suggested fix (if any)

You should receive an acknowledgment within **48 hours** and a resolution plan within **7 days**.

## Disclosure Policy

- Vulnerabilities will be fixed in a patch release
- A security advisory will be published after the fix is available
- Credit will be given to the reporter (unless anonymity is requested)
