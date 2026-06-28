package com.sleepersync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * DTO representing a Sleeper league as returned by /league/{league_id}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueDto {

    @JsonProperty("league_id")
    private String leagueId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private String status;

    @JsonProperty("sport")
    private String sport;

    @JsonProperty("season")
    private String season;

    @JsonProperty("season_type")
    private String seasonType;

    @JsonProperty("total_rosters")
    private Integer totalRosters;

    @JsonProperty("draft_id")
    private String draftId;

    @JsonProperty("scoring_settings")
    private Map<String, Double> scoringSettings;

    @JsonProperty("roster_positions")
    private String[] rosterPositions;

    @JsonProperty("settings")
    private Map<String, Object> settings;
}
