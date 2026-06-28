package com.sleepersync.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/link-sleeper
 * Links a Sleeper username to the authenticated user's account.
 */
@Data
public class LinkSleeperRequest {

    @NotBlank(message = "Sleeper username is required")
    private String sleeperUsername;
}
