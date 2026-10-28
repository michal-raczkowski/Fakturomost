Fakturomost — experimental test build
=====================================

Open-source desktop app to import, browse and export Polish KSeF FA(3) e-invoices
(XML/ZIP) to CSV, Excel, JSON or the original XML. Everything runs locally; nothing is
sent anywhere. Java is included — nothing else needs to be installed.

IMPORTANT
- Experimental. Use the synthetic demo data or KSeF *test* environment invoices only.
- Imported invoices are kept in memory and are lost when you close the app.
- Exported files are not encrypted.
- Independent project, not affiliated with or endorsed by the Polish Ministry of Finance.

START
- Windows: run Fakturomost.exe
    The build is not code-signed, so Windows SmartScreen may say "Windows protected your
    PC": click "More info" -> "Run anyway".
    Fakturomost-console.exe starts the same app with a console window that shows errors.
- Linux:   run bin/Fakturomost   (needs a desktop with GTK 3, as on any common distribution)

TRY IT
- Click "Import folder..." and choose the demo-data folder next to this file.

Source code, documentation and licence: see LICENSE.txt and the project's repository.
Font: IBM Plex Sans, SIL Open Font License 1.1 (LICENSE-IBM-Plex-font.txt).
