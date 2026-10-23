# Bundled FA(3) schemas

Official XML schemas of the FA(3) e-invoice structure (schema version 1-0E), bundled so that
validation never needs the network.

- **Source:** https://github.com/CIRFMF/ksef-docs (Ministry of Finance, official KSeF documentation)
- **Commit:** `ce3670ea042de380039de4c5849fd6455811b292` (14 Oct 2026)
- **License:** MIT, Copyright (c) 2025 Ministerstwo Finansów — see `LICENSE-ksef-docs.txt`
  (the notice must stay next to the files).
- **Files are byte-identical copies. Do not edit or reformat them.** `BundledSchemaFilesTest`
  checks the SHA-256 digests below, which also catches line-ending conversion by Git.

| Bundled file | Path in ksef-docs | Served for URI | SHA-256 |
|---|---|---|---|
| `schemat_FA(3)_v1-0E.xsd` | `faktury/schemy/FA/schemat_FA(3)_v1-0E.xsd` | (main schema, loaded directly) | `b646b6b525f51adf1bb2545f111fc8ca6e7aa6dd2f98948f1667d3695c06d958` |
| `StrukturyDanych_v10-0E.xsd` | `faktury/schemy/FA/bazowe/` | `http://crd.gov.pl/xml/schematy/dziedzinowe/mf/2022/01/05/eD/DefinicjeTypy/StrukturyDanych_v10-0E.xsd` | `cb08348374598e1e716e086c40d740390fb9e1bfa3aba1f4ec4cba0e1ef6d60f` |
| `ElementarneTypyDanych_v10-0E.xsd` | `faktury/schemy/FA/bazowe/` | `.../DefinicjeTypy/ElementarneTypyDanych_v10-0E.xsd` | `8daf4d3771de200b26b697294cc906a2add3de9acfbbd97f4b1bd4fc0e5ecb2f` |
| `KodyKrajow_v10-0E.xsd` | `faktury/schemy/FA/bazowe/` | `.../DefinicjeTypy/KodyKrajow_v10-0E.xsd` | `48be2a9f181d7ff80f185c62491ba12604c5cacbbe21af8e2aaaf2c585bbd214` |

Difference from the copies on crd.gov.pl: in the ksef-docs base schemas the `xsd:include`s are
relative (`KodyKrajow_v10-0E.xsd`) instead of absolute URLs. The resolver expands every reference
against its base URI, so both variants resolve to the same three allowed URIs.

To update: copy the new files from ksef-docs, update this table and the digests in
`BundledSchemaFilesTest`, and re-run all tests including `ReferenceSamplesTest`.
