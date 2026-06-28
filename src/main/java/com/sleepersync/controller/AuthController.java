package com.sleepersync.controller;

import com.sleepersync.model.dto.LeagueDto;
import com.sleepersync.model.dto.auth.AuthResponse;
import com.sleepersync.model.dto.auth.LinkSleeperRequest;
import com.sleepersync.model.dto.auth.LoginRequest;
import com.sleepersync.model.dto.auth.RegisterRequest;
import com.sleepersync.model.dto.auth.SelectLeagueRequest;
import com.sleepersync.model.entity.User;
import com.sleepersync.service.AuthService;
import com.sleepersync.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles user registration, login, Sleeper account linking, and league selection.
 *
 * --- User journey ---
 * 1. POST /api/auth/register       -> create account
 * 2. POST /api/auth/login          -> get JWT
 * 3. POST /api/auth/link-sleeper   -> link Sleeper username (requires JWT)
 * 4. GET  /api/auth/leagues        -> list available Sleeper leagues (requires JWT + linked Sleeper)
 * 5. POST /api/auth/select-league  -> choose active league (requires JWT)
 * 6. GET  /api/auth/me             -> get current user profile
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    // -------------------------------------------------------------------------
    // Step 1: Register
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // -------------------------------------------------------------------------
    // Step 2: Login
    // -------------------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // -------------------------------------------------------------------------
    // Step 3: Link Sleeper Username
    // -------------------------------------------------------------------------

    @PostMapping("/link-sleeper")
    public ResponseEntity<AuthResponse> linkSleeper(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody LinkSleeperRequest request
    ) {
        return ResponseEntity.ok(userService.linkSleeperAccount(currentUser, request));
    }

    // -------------------------------------------------------------------------
    // Step 4: Get available leagues
    // -------------------------------------------------------------------------

    @GetMapping("/leagues")
    public ResponseEntity<List<LeagueDto>> getAvailableLeagues(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(userService.getAvailableLeagues(currentUser));
    }

    // -------------------------------------------------------------------------
    // Step 5: Select active league
    // -------------------------------------------------------------------------

    @PostMapping("/select-league")
    public ResponseEntity<AuthResponse> selectLeague(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody SelectLeagueRequest request
    ) {
        return ResponseEntity.ok(userService.selectLeague(currentUser, request));
    }

    // -------------------------------------------------------------------------
    // Me (profile)
    // -------------------------------------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getMe(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(AuthResponse.builder()
                .email(currentUser.getEmail())
                .displayName(currentUser.getDisplayName())
                .sleeperUsername(currentUser.getSleeperUsername())
                .sleeperUserId(currentUser.getSleeperUserId())
                .activeLeagueId(currentUser.getActiveLeagueId())
                .build());
    }
}
