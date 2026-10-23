# Fakturomost demo data

A small, **entirely synthetic** set of KSeF FA(3) invoices for trying Fakturomost, for the CLI tests
and for the demo bundled with the installer. It shows one month (October 2026) of a fictitious
company, *Fikcyjne Studio Projektowe sp. z o.o.* (NIP `9999999999`, the Ministry of Finance's own
sample NIP).

All names, addresses, tax numbers, bank accounts and KSeF numbers are made up:

- Every NIP other than `9999999999` has an **invalid NIP checksum**, so it cannot belong to a real
  taxpayer. The EU VAT number is all zeros.
- KSeF numbers have the official format and a correct CRC-8 checksum, but were never issued by KSeF.
- Every XML file starts with the comment `Fakturomost demo data`.

`DemoDatasetTest` (in `fakturomost-core`) imports this folder on every build and checks all of the
above, so the table below stays true.

## Files and expected import results

Import the top-level files first, then `problemy/` (sorted by name):

| File | Result | What it shows |
|---|---|---|
| `9999999999-20261002-3A41F0C20001-22.xml` | Imported | Sale `FV/2026/10/0001`, PLN, 23% and 8% |
| `9999999999-20261009-3A41F0C20002-3E.xml` | Imported | Sale `FV/2026/10/0002`, PLN, 23%, quantity 3 |
| `9999999999-20261015-3A41F0C20003-2C.xml` | Imported | Advance invoice (`ZAL`) `FV/2026/10/0003` with an order |
| `9999999999-20261020-3A41F0C20004-99.xml` | Imported | Correction (`KOR`) `FK/2026/10/0001` of invoice 0001, linked by its KSeF number; negative amounts |
| `faktura-EUR-WDT.xml` | Imported | Sale `FV/2026/10/0004` in EUR to a German company, 0% intra-Community supply; file not named by a KSeF number, so no KSeF number is recorded |
| `zakupy-ksef-2026-10.zip` | 2 × Imported | KSeF-style bulk download (flat ZIP) with two **purchase** invoices: printing (23%) and a VAT-exempt training (`zw`) |
| `problemy/bez-numeru-faktury.xml` | Invalid | FA(3) without the required invoice number — fails schema validation |
| `problemy/faktura-FA2.xml` | Unsupported | Older FA(2) format — stored, not parsed |
| `problemy/kopia-9999999999-20261009-3A41F0C20002-3E.xml` | Duplicate | Byte-identical copy of invoice 0002 under another name |
| `problemy/skan-faktury.pdf` | Invalid | A PDF instead of XML |
| `problemy/uszkodzona.xml` | Invalid | Truncated, not well-formed XML |

Totals: 7 invoices (5 VAT, 1 advance, 1 correction; 5 sales, 2 purchases), 1 duplicate,
1 unsupported, 3 invalid.

## Regenerating

The files are committed as they are and must stay byte-identical (`demo-data/** -text` in
`.gitattributes`). Edit them by hand; keep the rules above, then run `./mvnw verify`.
