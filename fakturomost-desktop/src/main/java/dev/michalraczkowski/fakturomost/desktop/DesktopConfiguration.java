package dev.michalraczkowski.fakturomost.desktop;

import dev.michalraczkowski.fakturomost.core.ProductInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DesktopConfiguration {

    @Bean
    ProductInfo productInfo(@Value("${fakturomost.version}") String version) {
        return new ProductInfo("Fakturomost", version);
    }
}
