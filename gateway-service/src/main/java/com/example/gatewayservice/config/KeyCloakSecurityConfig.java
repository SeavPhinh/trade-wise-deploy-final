package com.example.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;


@Configuration
@EnableWebFluxSecurity
public class KeyCloakSecurityConfig {
    @Bean
    public SecurityWebFilterChain securityFilter(ServerHttpSecurity http) {
        return http

                .csrf().disable()
                .cors().and()
                .authorizeExchange()
                .pathMatchers("/api/v1/product-for-sales/**"
//                        ,"/users/**",
//                        "/api/v1/categories/**",
//                        "/api/v1/users/**",
//                        "/actuator/**",
//                        "/webjars/**", "/v3/api-docs/**",
//                        "/keycloak-client/v3/api-docs",
//                        "/task-service/v3/api-docs"
                )
                .permitAll()
                .anyExchange().permitAll()
                .and()
                .oauth2ResourceServer()
                .jwt().and()
                .and()
                .build();
    }

}
