package com.airtribe.chronos.notification.config;

import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.notification.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class NotificationConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean public ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }

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
                        .requestMatchers("/notifications/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwt), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> cf) {
        ConcurrentKafkaListenerContainerFactory<String, String> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(cf);
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(30_000);
        f.setCommonErrorHandler(new DefaultErrorHandler(backOff));
        f.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return f;
    }
}
