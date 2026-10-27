# Security policy

Fakturomost processes invoices, which contain business and personal data. Security reports are
welcome and taken seriously.

## Supported versions

Fakturomost is **experimental** and has no production release yet. Only the latest commit on `main`
receives fixes. The current version is not designed for production financial data: imported
invoices are kept in memory only, and exported files are not encrypted.

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Report them privately through
GitHub's [private vulnerability reporting](https://github.com/michalraczkowski/fakturomost/security/advisories/new)
("Security" tab → "Report a vulnerability").

Please include what you found, how to reproduce it (a synthetic file is ideal), and the impact you
expect. **Never send real invoices, KSeF tokens, certificates or other credentials** — anonymise
samples or use the files in `demo-data/`.

This is a personal, non-commercial project maintained in spare time, so there is no fixed response
time; you can expect an acknowledgement as soon as reasonably possible.

## Scope and known limitations

In scope: anything that lets a crafted XML or ZIP file read local files, reach the network, exhaust
memory or disk, escape the import limits, alter imported data, or inject content into exports
(e.g. CSV formula injection); any listening network socket; invoice contents appearing in logs or
console output.

Known limitations, documented and planned:

- Data is held **in memory** only; encrypted local storage (SQLite with a key protected by Windows
  DPAPI) is the next milestone.
- Exported CSV/JSON/XML files are **not encrypted**; the app says so on every export.
- No protection against malware running with the same user's privileges — no desktop app can
  promise that.
- Installers are not code-signed yet; Windows SmartScreen may warn.
