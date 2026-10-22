package dev.michalraczkowski.fakturomost.desktop;
import dev.michalraczkowski.fakturomost.core.ProductInfo;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class FakturomostApp extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        // Runs on the JavaFX launcher thread, NOT the FX Application Thread.
        context = new SpringApplicationBuilder(DesktopConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage stage) {
        ProductInfo info = context.getBean(ProductInfo.class);
        var label = new Label(info.displayName() + " — experimental, synthetic data only");
        stage.setTitle(info.displayName());
        stage.setScene(new Scene(new StackPane(label), 900, 600));
        stage.show();
    }

    @Override
    public void stop() {
        context.close();
    }
}