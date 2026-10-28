package dev.michalraczkowski.fakturomost.desktop.ui.model;

/** Which invoices "Export…" writes: the selected rows or every row the filters currently show. */
public enum ExportScope {
    SELECTED,
    ALL_SHOWN;

    /** Selected rows if there are any (the user picked them on purpose), otherwise everything shown. */
    public static ExportScope defaultFor(int selected) {
        return selected > 0 ? SELECTED : ALL_SHOWN;
    }

    /** Radio button text, e.g. {@code Selected invoices (3)} or {@code All 7 shown invoices}. */
    public String label(int count, boolean filtered) {
        return switch (this) {
            case SELECTED -> "Selected " + (count == 1 ? "invoice" : "invoices") + " (" + count + ")";
            case ALL_SHOWN -> (count == 1 ? "The 1 shown invoice" : "All " + count + " shown invoices")
                    + (filtered ? " (as filtered)" : "");
        };
    }
}
