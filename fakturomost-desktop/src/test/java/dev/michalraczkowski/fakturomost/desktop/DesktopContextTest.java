package dev.michalraczkowski.fakturomost.desktop;
import dev.michalraczkowski.fakturomost.core.ProductInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopContextTest {
    @Test
    void springContextStartsWithoutJavaFx() {
        try (var ctx = new SpringApplicationBuilder(DesktopConfiguration.class)
                .web(WebApplicationType.NONE)
                .run()) {
            assertThat(ctx.getBean(ProductInfo.class).name()).isEqualTo("Fakturomost");
        }
    }
}