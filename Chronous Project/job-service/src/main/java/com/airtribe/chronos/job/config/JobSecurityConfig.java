package com.airtribe.chronos.job.config;

import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.job.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;

@Configuration
public class JobSecurityConfig {

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${chronos.security.jwt.secret}") String secret,
            @Value("${chronos.security.jwt.issuer:chronos-identity}") String issuer,
            @Value("${chronos.security.jwt.ttl-seconds:3600}") long ttl) {
        return new JwtTokenService(secret, issuer, ttl);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenService jwtTokenService) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(req -> req
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/jobs/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
