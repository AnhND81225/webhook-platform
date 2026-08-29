package com.webhookplatform.webhook.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("!oauth")
class BaseSecurityConfiguration {

    @Bean
    SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/healthz", "/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
