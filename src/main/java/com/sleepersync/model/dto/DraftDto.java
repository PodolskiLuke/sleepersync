package com.sleepersync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * DTO representing a Sleeper draft as returned by /draft/{draft_id}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DraftDto {

    @JsonProperty("draft_id")
    private String draftId;

    @JsonProperty("league_id")
    private String leagueId;

    /** snake | linear | auction */
    @JsonProperty("type")
    private String type;

    /** pre_draft | drafting | paused | complete */
    @JsonProperty("status")
    private String status;

    @JsonProperty("sport")
    private String sport;

    @JsonProperty("season")
    private String season;

    @JsonProperty("start_time")
    private Long startTime;

    /** Misc draft settings - e.g. rounds, teams, pick_timer */
    @JsonProperty("settings")
    private Map<String, Object> settings;

    /** Maps sleeper user_id -> draft slot (1-indexed column) */
    @JsonProperty("draft_order")
    private Map<String, Integer> draftOrder;

    /** Maps draft slot -> roster_id */
    @JsonProperty("slot_to_roster_id")
    private Map<String, Integer> slotToRosterId;
}
