package dev.michalraczkowski.fakturomost.desktop;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

final class DesktopContext {

    private DesktopContext() {
    }

    static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(DesktopConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}