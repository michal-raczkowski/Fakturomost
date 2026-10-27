package dev.michalraczkowski.fakturomost.desktop;

import dev.michalraczkowski.fakturomost.core.ProductInfo;
import dev.michalraczkowski.fakturomost.core.application.InvoiceCatalog;
import dev.michalraczkowski.fakturomost.core.application.InvoiceExports;
import dev.michalraczkowski.fakturomost.core.application.InvoiceImporter;
import dev.michalraczkowski.fakturomost.desktop.ui.Background;
import dev.michalraczkowski.fakturomost.desktop.ui.MainWindow;
import dev.michalraczkowski.fakturomost.desktop.ui.Theme;
import dev.michalraczkowski.fakturomost.desktop.ui.UiServices;
import javafx.application.Application;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX entry point. Spring starts in {@link #init()} (launcher thread, not the FX thread), the
 * window is built in {@link #start}, both are shut down in {@link #stop()}.
 *
 * <p>The only place where Spring meets the UI: the beans are looked up once and handed to the
 * window as plain objects ({@link UiServices}).
 */
public class FakturomostApp extends Application {

    private ConfigurableApplicationContext context;
    private Background background;

    @Override
    public void init() {
        context = DesktopContext.start(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage stage) {
        UiServices services = new UiServices(
                context.getBean(ProductInfo.class),
                context.getBean(InvoiceImporter.class),
                context.getBean(InvoiceCatalog.class),
                context.getBean(InvoiceExports.class));
        Theme.loadFonts();
        background = new Background();
        MainWindow.show(stage, services, background);
    }

    @Override
    public void stop() {
        if (background != null) {
            background.close();
        }
        context.close();
    }
}
