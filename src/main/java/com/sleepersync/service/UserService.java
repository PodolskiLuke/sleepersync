package com.sleepersync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sleepersync.api.SleeperApiClient;
import com.sleepersync.model.dto.LeagueDto;
import com.sleepersync.model.dto.auth.AuthResponse;
import com.sleepersync.model.dto.auth.LinkSleeperRequest;
import com.sleepersync.model.dto.auth.SelectLeagueRequest;
import com.sleepersync.model.entity.User;
import com.sleepersync.repository.UserRepository;
import com.sleepersync.security.JwtUtil;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SleeperApiClient sleeperApiClient;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, SleeperApiClient sleeperApiClient, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.sleeperApiClient = sleeperApiClient;
        this.jwtUtil = jwtUtil;
    }

    // -------------------------------------------------------------------------
    // UserDetailsService (required by Spring Security)
    // -------------------------------------------------------------------------

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    // -------------------------------------------------------------------------
    // Sleeper Account Linking
    // -------------------------------------------------------------------------

    /**
     * Links a Sleeper username to the current user's account.
     * Validates the username exists on Sleeper before saving.
     * Returns a fresh JWT with updated user data.
     */
    @Transactional
    public AuthResponse linkSleeperAccount(User currentUser, LinkSleeperRequest request) {
        String sleeperUsername = request.getSleeperUsername().trim();

        // Check not already taken by another app user
        if (userRepository.existsBySleeperUsername(sleeperUsername)) {
            throw new IllegalArgumentException("This Sleeper username is already linked to another account");
        }

        // Validate username exists on Sleeper
        JsonNode sleeperUser = sleeperApiClient.getUserByUsername(sleeperUsername);
        if (sleeperUser == null || sleeperUser.get("user_id") == null) {
            throw new IllegalArgumentException("Sleeper username '" + sleeperUsername + "' was not found. Please check and try again.");
        }

        String sleeperUserId = sleeperUser.get("user_id").asText();

        currentUser.setSleeperUsername(sleeperUsername);
        currentUser.setSleeperUserId(sleeperUserId);
        userRepository.save(currentUser);

        String token = jwtUtil.generateToken(currentUser);
        return buildAuthResponse(token, currentUser);
    }

    // -------------------------------------------------------------------------
    // League Selection
    // -------------------------------------------------------------------------

    /**
     * Fetches all Sleeper leagues available for the current user's linked account.
     * Requires Sleeper to be linked first.
     */
    public List<LeagueDto> getAvailableLeagues(User currentUser) {
        if (currentUser.getSleeperUserId() == null) {
            throw new IllegalStateException("Please link your Sleeper account first");
        }
        return sleeperApiClient.getLeaguesForUser(currentUser.getSleeperUserId());
    }

    /**
     * Sets the user's active league.
     * Returns a fresh JWT with the updated activeLeagueId.
     */
    @Transactional
    public AuthResponse selectLeague(User currentUser, SelectLeagueRequest request) {
        if (currentUser.getSleeperUserId() == null) {
            throw new IllegalStateException("Please link your Sleeper account before selecting a league");
        }

        currentUser.setActiveLeagueId(request.getLeagueId());
        userRepository.save(currentUser);

        String token = jwtUtil.generateToken(currentUser);
        return buildAuthResponse(token, currentUser);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuthResponse buildAuthResponse(String token, User user) {
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .sleeperUsername(user.getSleeperUsername())
                .sleeperUserId(user.getSleeperUserId())
                .activeLeagueId(user.getActiveLeagueId())
                .build();
    }
}
