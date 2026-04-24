package com.airtribe.chronos.execution.config;

import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.execution.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ExecutionWebSecurityConfig {

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${chronos.security.jwt.secret}") String secret,
            @Value("${chronos.security.jwt.issuer:chronos-identity}") String issuer,
            @Value("${chronos.security.jwt.ttl-seconds:3600}") long ttl) {
        return new JwtTokenService(secret, issuer, ttl);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenService jwt) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(req -> req
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/executions/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
