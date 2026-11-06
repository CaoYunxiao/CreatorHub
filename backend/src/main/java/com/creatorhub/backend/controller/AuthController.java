package com.creatorhub.backend.controller;

import java.security.Principal;

import com.creatorhub.backend.dto.RegisterRequest;
import com.creatorhub.backend.dto.UserResponse;
import com.creatorhub.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @GetMapping("/me")
    public UserResponse currentUser(Principal principal) {
        return userService.currentUser(principal.getName());
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
