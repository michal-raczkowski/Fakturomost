package dev.michalraczkowski.fakturomost.desktop.ui.model;

/** Fixed texts the UI must show: the master prompt §14 notice and the storage warning. */
public final class Notices {

    private Notices() {
    }

    /** Banner under the toolbar, always visible. Changes when encrypted storage arrives (M5). */
    public static final String BANNER =
            "Experimental — use synthetic test data only. Imported invoices are kept in memory and are "
                    + "lost when you close Fakturomost.";

    /** Master prompt §14 draft wording (not legal advice; to be reviewed before production use). */
    public static final String ABOUT = """
            Fakturomost is an independent, experimental open-source project.

            It is not affiliated with or endorsed by the Polish Ministry of Finance.

            The software is provided under Apache License 2.0 on an “AS IS” basis, without warranties, \
            to the extent permitted by applicable law.

            The current version is intended for demonstration and testing purposes and is not designed \
            for production financial data.

            Users should independently verify exported information and maintain appropriate backups.

            Fakturomost does not replace professional accounting software or accounting advice.""";
}
