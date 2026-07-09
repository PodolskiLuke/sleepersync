package com.sleepersync.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemainingDraftRankingsResponse {
    private String draftId;
    private int pickedCount;
    private int candidateCount;
    private List<AggregatedRankingEntry> overall;
    private List<AggregatedRankingEntry> rookies;
    private Map<String, List<AggregatedRankingEntry>> byPosition;
}
