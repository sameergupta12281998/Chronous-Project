package com.airtribe.chronos.identity.service;

import com.airtribe.chronos.commons.error.BadRequestException;
import com.airtribe.chronos.commons.error.ConflictException;
import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.identity.domain.UserEntity;
import com.airtribe.chronos.identity.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public UserEntity register(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                username,
                email,
                passwordEncoder.encode(rawPassword),
                Instant.now()
        );
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResult login(String username, String rawPassword) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }
        String token = jwtTokenService.issueToken(user.getId(), user.getUsername());
        return new TokenResult(token, jwtTokenService.ttlSeconds(), user);
    }

    public record TokenResult(String token, long expiresInSeconds, UserEntity user) {}
}
