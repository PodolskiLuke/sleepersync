package com.sleepersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedRankingEntry {
    private String playerId;
    private String playerName;
    private String position;
    private String eligiblePositions;
    private String team;
    private boolean rookie;

    private Double sleeperFantasyPtsAvg;
    private Integer sleeperSearchRank;

    private Double externalAvgRank;
    private Integer externalRankCount;

    private Double blendedScore;
    private Integer finalRank;

    private List<DynastyRankingEntry> sourceRanks;
}
