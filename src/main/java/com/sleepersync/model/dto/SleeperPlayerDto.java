package com.sleepersync.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO representing a player as returned by the Sleeper /players/nba endpoint.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SleeperPlayerDto {

    @JsonProperty("player_id")
    private String playerId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("position")
    private String position;

    @JsonProperty("team")
    private String team;

    @JsonProperty("age")
    private Integer age;

    @JsonProperty("years_exp")
    private Integer yearsExp;

    @JsonProperty("status")
    private String status;

    @JsonProperty("injury_status")
    private String injuryStatus;

    @JsonProperty("injury_notes")
    private String injuryNotes;

    @JsonProperty("sport")
    private String sport;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("search_rank")
    private Integer searchRank;

    @JsonProperty("number")
    private Integer number;

    @JsonProperty("height")
    private String height;

    @JsonProperty("weight")
    private String weight;

    @JsonProperty("college")
    private String college;

    @JsonProperty("birth_date")
    private String birthDate;

    @JsonProperty("fantasy_positions")
    private String[] fantasyPositions;
}
