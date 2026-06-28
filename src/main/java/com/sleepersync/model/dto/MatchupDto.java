package com.sleepersync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a matchup entry as returned by /league/{league_id}/matchups/{week}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchupDto {

    @JsonProperty("matchup_id")
    private Integer matchupId;

    @JsonProperty("roster_id")
    private Integer rosterId;

    @JsonProperty("points")
    private Double points;

    @JsonProperty("starters")
    private List<String> starters;

    @JsonProperty("players")
    private List<String> players;

    @JsonProperty("starters_points")
    private List<Double> startersPoints;

    @JsonProperty("players_points")
    private Map<String, Double> playersPoints;
}
