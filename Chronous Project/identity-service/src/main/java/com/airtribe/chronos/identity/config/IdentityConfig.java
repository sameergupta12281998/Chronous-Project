package com.airtribe.chronos.identity.config;

import com.airtribe.chronos.commons.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${chronos.security.jwt.secret}") String secret,
            @Value("${chronos.security.jwt.issuer:chronos}") String issuer,
            @Value("${chronos.security.jwt.ttl-seconds:3600}") long ttl) {
        return new JwtTokenService(secret, issuer, ttl);
    }
}
