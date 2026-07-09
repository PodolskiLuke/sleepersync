package com.sleepersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DynastyRankingEntry {
    private String source;
    private String playerName;
    private String position;
    private String team;
    private Integer rank;
    private String rawText;
}
