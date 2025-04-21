package se.magnus.microservices.composite.product;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.
            authorizeExchange(auth ->
                auth.pathMatchers("/openapi/**").permitAll()
                    .pathMatchers("/webjars/**").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers(POST, "/product-composite/**")
                    .hasAuthority("SCOPE_product:write")
                    .pathMatchers(DELETE, "/product-composite/**")
                    .hasAuthority("SCOPE_product:write")
                    .pathMatchers(GET, "/product-composite/**")
                    .hasAuthority("SCOPE_product:read")
                    .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
