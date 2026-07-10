package com.sleepersync.controller;

import com.sleepersync.model.dto.DynastyRankingEntry;
import com.sleepersync.model.dto.RemainingDraftRankingsResponse;
import com.sleepersync.model.dto.AggregatedRankingEntry;
import com.sleepersync.service.DynastyRankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
@CrossOrigin(origins = "*")
public class RankingsController {

    private final DynastyRankingService dynastyRankingService;

    public RankingsController(DynastyRankingService dynastyRankingService) {
        this.dynastyRankingService = dynastyRankingService;
    }

    /**
     * Scrape configured ranking sources (next-season + dynasty) and return raw rows.
     */
    @GetMapping("/scrape")
    public ResponseEntity<List<DynastyRankingEntry>> scrapeConfiguredSources() {
        return ResponseEntity.ok(dynastyRankingService.scrapeConfiguredSources());
    }

    /**
     * Return all players ranked by blended score.
     * - Local Sleeper production/adp
     * - External scraped next-season/dynasty rankings
     */
    @GetMapping("/all")
    public ResponseEntity<List<AggregatedRankingEntry>> getAllRankings() {
        return ResponseEntity.ok(dynastyRankingService.getAllRankings());
    }

    /**
     * Return remaining draft candidates ranked by blended score:
     * - Local Sleeper production/adp
     * - External scraped next-season/dynasty rankings
     * - Includes rookie-only names from external sources
     */
    @GetMapping("/draft/{draftId}/remaining")
    public ResponseEntity<RemainingDraftRankingsResponse> getRemaining(
            @PathVariable String draftId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(dynastyRankingService.getRemainingRankings(draftId, limit));
    }
}
