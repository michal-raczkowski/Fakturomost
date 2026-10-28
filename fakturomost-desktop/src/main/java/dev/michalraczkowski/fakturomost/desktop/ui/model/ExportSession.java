package dev.michalraczkowski.fakturomost.desktop.ui.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * The export choices remembered while the app runs, so the next "Export…" starts where the last one
 * ended. Kept in memory only and lost on close — like the invoices themselves until encrypted
 * storage arrives; nothing here is written to disk.
 *
 * <p>Both CSV formats share one column layout (their default names are the same); each keeps its
 * own file settings. The chosen-fields JSON has its own layout, with English keys by default.
 * Used on the JavaFX thread only.
 */
public final class ExportSession {

    private ExportFormat lastFormat = ExportFormat.CSV_EXCEL_PL;
    private ColumnLayout csvLayout = ColumnLayout.defaults(ColumnLayout.Names.POLISH);
    private ColumnLayout jsonLayout = ColumnLayout.defaults(ColumnLayout.Names.ENGLISH);
    private final Map<ExportFormat, CsvSettings> csvSettings = new EnumMap<>(ExportFormat.class);

    public ExportFormat lastFormat() {
        return lastFormat;
    }

    /** The remembered setup for {@code format}, or its defaults if it was never used. */
    public ExportSetup setupFor(ExportFormat format) {
        ExportSetup defaults = ExportSetup.defaults(format);
        if (format.isCsv()) {
            return defaults.withLayout(csvLayout).withCsv(csvSettings.getOrDefault(format, defaults.csv()));
        }
        if (format == ExportFormat.JSON_FIELDS) {
            return defaults.withLayout(jsonLayout);
        }
        return defaults;
    }

    /** Called after the user confirmed a setup (before choosing the file). */
    public void remember(ExportSetup setup) {
        lastFormat = setup.format();
        if (setup.format().isCsv()) {
            csvLayout = setup.layout();
            csvSettings.put(setup.format(), setup.csv());
        } else if (setup.format() == ExportFormat.JSON_FIELDS) {
            jsonLayout = setup.layout();
        }
    }

    /** Only the format, e.g. after exporting originals. */
    public void rememberFormat(ExportFormat format) {
        lastFormat = format;
    }
}
