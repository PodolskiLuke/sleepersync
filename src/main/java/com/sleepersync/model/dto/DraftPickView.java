package com.sleepersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enriched, frontend-friendly view of a single draft pick.
 * Combines the raw Sleeper pick data with locally-synced Player info
 * (falling back to the pick's own metadata when a player isn't synced).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftPickView {
    private Integer round;
    private Integer pickNo;
    private Integer draftSlot;
    private Integer rosterId;
    private String pickedBy;
    private Boolean isKeeper;

    private String playerId;
    private String fullName;
    private String position;
    private String team;
    private String injuryStatus;
}
