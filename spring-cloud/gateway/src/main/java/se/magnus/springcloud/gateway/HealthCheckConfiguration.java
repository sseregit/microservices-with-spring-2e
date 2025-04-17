package se.magnus.springcloud.gateway;

import static java.util.logging.Level.FINE;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.CompositeReactiveHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class HealthCheckConfiguration {

    private final WebClient webClient;

    public HealthCheckConfiguration(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Bean
    ReactiveHealthContributor healthcheckMicroservices() {
        final Map<String, ReactiveHealthIndicator> registry = new LinkedHashMap<>();

        registry.put("product", () -> getHealth("http://product"));
        registry.put("recommendation", () -> getHealth("http://recommendation"));
        registry.put("review", () -> getHealth("http://review"));
        registry.put("product-composite", () -> getHealth("http://product-composite"));
        registry.put("auth-server", () -> getHealth("http://auth-server"));

        return CompositeReactiveHealthContributor.fromMap(registry);
    }

    private Mono<Health> getHealth(String baseUrl) {
        String url = baseUrl + "/actuator/health";
        log.debug("Setting up a call to the Health API on URL: {}", url);
        return webClient.get().uri(url).retrieve().bodyToMono(String.class)
            .map(s -> new Health.Builder().up().build())
            .onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
            .log(log.getName(), FINE);

    }
}
