package com.sleepersync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * DTO representing a single pick as returned by /draft/{draft_id}/picks.
 * Note: Sleeper includes a "metadata" map with the picked player's basic
 * info (first_name, last_name, position, team) even if we don't have that
 * player synced locally - handy as a fallback for display purposes.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DraftPickDto {

    @JsonProperty("round")
    private Integer round;

    @JsonProperty("pick_no")
    private Integer pickNo;

    @JsonProperty("draft_slot")
    private Integer draftSlot;

    @JsonProperty("roster_id")
    private Integer rosterId;

    @JsonProperty("player_id")
    private String playerId;

    /** Sleeper user_id of the drafter (may be null/empty for un-owned/bot slots) */
    @JsonProperty("picked_by")
    private String pickedBy;

    @JsonProperty("is_keeper")
    private Boolean isKeeper;

    @JsonProperty("metadata")
    private Map<String, String> metadata;
}
