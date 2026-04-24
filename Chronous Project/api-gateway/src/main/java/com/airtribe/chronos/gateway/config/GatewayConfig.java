package com.airtribe.chronos.gateway.config;

import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.gateway.security.JwtValidationGlobalFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${chronos.security.jwt.secret}") String secret,
            @Value("${chronos.security.jwt.issuer:chronos-identity}") String issuer,
            @Value("${chronos.security.jwt.ttl-seconds:3600}") long ttl) {
        return new JwtTokenService(secret, issuer, ttl);
    }

    @Bean
    public JwtValidationGlobalFilter jwtValidationGlobalFilter(JwtTokenService jwt) {
        return new JwtValidationGlobalFilter(jwt);
    }
}
