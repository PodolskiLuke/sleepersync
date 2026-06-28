package com.sleepersync.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/select-league
 * Sets the user's active league.
 */
@Data
public class SelectLeagueRequest {

    @NotBlank(message = "League ID is required")
    private String leagueId;
}
