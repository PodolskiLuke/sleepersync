package com.sleepersync.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for persisting NBA player data in PostgreSQL.
 * Populated by syncing from the Sleeper /players/nba endpoint.
 */
@Entity
@Table(name = "players")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    @Column(name = "player_id", nullable = false)
    private String playerId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "position")
    private String position;

    @Column(name = "team")
    private String team;

    @Column(name = "age")
    private Integer age;

    @Column(name = "years_exp")
    private Integer yearsExp;

    @Column(name = "status")
    private String status;

    @Column(name = "injury_status")
    private String injuryStatus;

    @Column(name = "injury_notes", columnDefinition = "TEXT")
    private String injuryNotes;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "search_rank")
    private Integer searchRank;

    @Column(name = "college")
    private String college;

    @Column(name = "birth_date")
    private String birthDate;

    // -------------------------------------------------------------------------
    // Season stats (populated by POST /api/players/sync-stats)
    // Source: Sleeper /stats/nba/regular/{season}
    // -------------------------------------------------------------------------

    /** Games played in the synced season */
    @Column(name = "games_played")
    private Integer gamesPlayed;

    /** Per-game averages */
    @Column(name = "avg_pts")
    private Double avgPts;

    @Column(name = "avg_reb")
    private Double avgReb;

    @Column(name = "avg_ast")
    private Double avgAst;

    @Column(name = "avg_stl")
    private Double avgStl;

    @Column(name = "avg_blk")
    private Double avgBlk;

    @Column(name = "avg_to")
    private Double avgTo;

    @Column(name = "avg_fg3m")
    private Double avgFg3m;

    /**
     * Composite fantasy points per game using standard points league scoring:
     *   pts*1 + reb*1.2 + ast*1.5 + stl*3 + blk*3 - to*1 + fg3m*0.5
     */
    @Column(name = "fantasy_pts_avg")
    private Double fantasyPtsAvg;

    /** Timestamp of when this record was last synced from Sleeper */
    @Column(name = "last_synced")
    private LocalDateTime lastSynced;
}
