# Fakturomost

**A local, private bridge between KSeF and your business software.**

Fakturomost is an open-source Java desktop application for importing, browsing and exporting structured invoices from Poland's National e-Invoicing System (KSeF). Invoices are processed and stored only on your own computer.

> ⚠️ **Experimental — not for production financial data.**
> Fakturomost is an independent personal project. It is **not affiliated with or endorsed by the Polish Ministry of Finance**, and it is not accounting software. It is provided under the Apache License 2.0 "AS IS", without warranties, to the extent permitted by applicable law. Always verify exported data yourself.

## Why

Many small businesses receive invoices through KSeF but use older software that cannot read them. Fakturomost is meant to sit in between: take invoices downloaded from KSeF, let you search and inspect them, and export the data as CSV or JSON for import into existing tools.

It is a read-only bridge. It never modifies original invoice documents and does not issue or submit invoices.

## No network listeners

The desktop app has no reason to accept network connections, so it doesn't. Three layers keep it that way:

1. **Build:** the Maven Enforcer plugin fails the build if an embedded web-server library (Tomcat, Jetty, Undertow, Reactor Netty) reaches the classpath.
2. **Startup:** Spring Boot runs with `WebApplicationType.NONE`.
3. **Test:** an automated test starts the application context and asserts that the process opens no listening sockets (Linux CI).


## Tech stack

Java 21 · JavaFX 21 · Spring Boot 4 (non-web) · Maven · JUnit 5 · GitHub Actions (Linux + Windows)

## Security

Invoices contain sensitive financial and personal data. Security work (encrypted storage, key protection, safe XML/ZIP handling)  **do not use real invoice data**.

## License

Copyright 2026 Michał Raczkowski.

Licensed under the [Apache License, Version 2.0](LICENSE).