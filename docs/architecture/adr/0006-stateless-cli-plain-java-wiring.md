# ADR 0006: Stateless CLI with plain Java wiring (no Spring)

- **Status:** Accepted (Milestone 3, October 2026)

## Context

`fakturomost-cli` exists to prove that `fakturomost-core` runs headless, without JavaFX or Spring, and to give technical users and CI a scriptable converter (addendum §4). The desktop module uses Spring Boot (without HTTP) for dependency injection, configuration and, later, transactions around the encrypted database.

The CLI reads input files, processes them in memory, writes one output and exits. It has no database, no stored keys, no configuration files and no long-lived components.

## Decision

- No Spring in the CLI. All objects are created in a few lines of plain Java (`Main.newCommandLine`, and later the core services such as `InvoiceImporter` over an `InMemoryDocumentStore`).
- Argument parsing with **picocli 4.7.7** (Apache-2.0, a single jar with no runtime dependencies). Checked 24 Oct 2026: newest release (19 Apr 2025), no CVEs or GitHub security advisories, commits on `main` as recently as August 2026; the project is maintained but releases are infrequent, mostly by one maintainer.
- Maven Enforcer bans JavaFX, Spring, SQLite drivers, JNA and the desktop module from the CLI's dependency tree. An ArchUnit rule (later M3 task) limits the CLI to `core.application` and domain types.
- Fixed exit codes: `0` success, `1` usage error, `2` some documents invalid or unsupported, `3` I/O or unexpected error.

## Consequences

- The contrast with the Spring-wired desktop module is visible in the code: the same core services, wired by hand here and by `@Bean` methods there. Core stays free of annotations either way.
- Startup is fast and the distribution is small (thin jar + `lib/`).
- Adding dependencies to the CLI later means editing `main`; acceptable while the CLI has two commands.
- If picocli stopped being maintained, the CLI could switch to hand-written parsing: the commands are small and picocli is used only at the edge (`Main`, command classes).

## Alternatives considered

- **Spring Boot with `CommandLineRunner`** (or picocli's Spring Boot starter): adds startup time and a framework to a process that has nothing to manage, and would weaken the "core runs without Spring" proof.
- **Hand-written argument parsing:** no dependency, but help text, typo suggestions, option validation and exit-code handling would all need writing and testing.
- **Apache Commons CLI / JCommander:** viable, but picocli was already the named choice and offers subcommands, generated help and exit-code mapping out of the box.
