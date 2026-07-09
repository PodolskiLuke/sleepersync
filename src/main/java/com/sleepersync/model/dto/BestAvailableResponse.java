package com.sleepersync.model.dto;

import com.sleepersync.model.entity.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response shape for the "best available" draft helper endpoint.
 * - overall: top players across all positions, ranked by Sleeper search_rank
 * - byPosition: top players grouped by position (PG, SG, SF, PF, C)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BestAvailableResponse {
    private List<Player> overall;
    private Map<String, List<Player>> byPosition;
    private int totalPicksMade;
}
