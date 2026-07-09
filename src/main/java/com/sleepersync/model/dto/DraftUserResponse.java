package com.sleepersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal Sleeper user identity, resolved from a username.
 * Used by the Draft Helper so a user can identify their own picks
 * without needing to fully register/link their account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftUserResponse {
    private String userId;
    private String username;
    private String displayName;
    private String avatar;
}
