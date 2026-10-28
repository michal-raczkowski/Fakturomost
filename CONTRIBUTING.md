# Contributing to Fakturomost

Thanks for your interest! Fakturomost is a small, single-maintainer project, so focused
contributions help most.

## Good ways to help

- **Report how real KSeF downloads look**: file names, ZIP layouts, schema versions, edge cases
  that Fakturomost rejects or misreads. Describe them or share **anonymised** samples.
- **Check the exports** in the software you use (Excel, LibreOffice, bookkeeping or inventory
  programs) and tell us what imports cleanly and what doesn't.
- **Bugs and ideas**: open an issue with steps to reproduce, ideally using files from
  [`demo-data/`](demo-data/README.md).

**Never attach real invoices, NIPs of real companies, KSeF tokens or certificates.** Security issues
go through [SECURITY.md](SECURITY.md), not public issues.

## Development

Requirements: JDK 21 and Git. Maven comes with the wrapper.

```bash
./mvnw verify        # build + all tests (Windows: mvnw.cmd verify)
```

- CI runs the same command on **Linux and Windows**; both must pass.
- Branches: short-lived `type/topic` branches (`feat/`, `fix/`, `docs/`, `test/`, `build/`, `ci/`,
  `chore/`), pull request into `main`, squash merge. Commit messages follow
  [Conventional Commits](https://www.conventionalcommits.org/).
- Architecture rules are tests (ArchUnit) and build rules (Maven Enforcer): `fakturomost-core` stays
  free of frameworks, UI, databases and networking; front ends use only `core.application` and
  domain types; neither front end depends on the other. If a rule blocks you, open an issue to
  discuss the design rather than weakening the rule.
- New behaviour comes with tests. Security-relevant code (XML parsing, ZIP handling, exports) gets
  tests for the hostile case too.
- New dependencies need a reason: check licence, maintenance and known vulnerabilities first.

## Licence

By contributing you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the licence of this project.
