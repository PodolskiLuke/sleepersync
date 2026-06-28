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

    /** Timestamp of when this record was last synced from Sleeper */
    @Column(name = "last_synced")
    private LocalDateTime lastSynced;
}
