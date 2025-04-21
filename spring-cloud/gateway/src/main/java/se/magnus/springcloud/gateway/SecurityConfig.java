package se.magnus.springcloud.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Slf4j
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(
                authorizeExchangeSpec -> {
                    authorizeExchangeSpec.pathMatchers("/headerrouting/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/actuator/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/eureka/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/oauth2/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/login/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/error/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/openapi/**").permitAll();
                    authorizeExchangeSpec.pathMatchers("/webjars/**").
                        permitAll();
                    authorizeExchangeSpec.anyExchange().authenticated();
                }
            )
            .oauth2ResourceServer(
                oauth2 -> oauth2.jwt(Customizer.withDefaults())
            );
        return http.build();
    }
}
