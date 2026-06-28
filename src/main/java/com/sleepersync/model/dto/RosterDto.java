package com.sleepersync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a roster in a Sleeper league as returned by /league/{league_id}/rosters.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RosterDto {

    @JsonProperty("roster_id")
    private Integer rosterId;

    @JsonProperty("owner_id")
    private String ownerId;

    @JsonProperty("league_id")
    private String leagueId;

    @JsonProperty("players")
    private List<String> players;

    @JsonProperty("starters")
    private List<String> starters;

    @JsonProperty("reserve")
    private List<String> reserve;

    @JsonProperty("taxi")
    private List<String> taxi;

    @JsonProperty("settings")
    private Map<String, Integer> settings;
}
