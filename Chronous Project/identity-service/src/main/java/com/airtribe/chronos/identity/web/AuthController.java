package com.airtribe.chronos.identity.web;

import com.airtribe.chronos.identity.domain.UserEntity;
import com.airtribe.chronos.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        UserEntity user = userService.register(request.username(), request.email(), request.password());
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        UserService.TokenResult result = userService.login(request.username(), request.password());
        return new AuthTokenResponse(
                result.token(),
                "Bearer",
                result.expiresInSeconds(),
                result.user().getId(),
                result.user().getUsername()
        );
    }
}
