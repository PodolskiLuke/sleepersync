package com.sleepersync.controller;

import com.sleepersync.model.dto.BestAvailableResponse;
import com.sleepersync.model.dto.DraftDto;
import com.sleepersync.model.dto.DraftPickDto;
import com.sleepersync.model.dto.DraftPickView;
import com.sleepersync.model.dto.DraftUserResponse;
import com.sleepersync.service.DraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Powers the Draft Helper tool.
 *
 * --- Typical flow ---
 * 1. GET /api/drafts/resolve/{username}            -> resolve Sleeper user_id
 * 2. GET /api/drafts/{draftId}                      -> draft metadata
 * 3. GET /api/drafts/{draftId}/picks                -> all picks so far (poll while live)
 * 4. GET /api/drafts/{draftId}/my-picks             -> a specific user's picks
 * 5. GET /api/drafts/{draftId}/best-available       -> remaining players by position
 */
@RestController
@RequestMapping("/api/drafts")
@CrossOrigin(origins = "*")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    /** Resolve a Sleeper username to a user_id (needed to identify "my picks") */
    @GetMapping("/resolve/{username}")
    public ResponseEntity<DraftUserResponse> resolveUser(@PathVariable String username) {
        return ResponseEntity.ok(draftService.resolveUser(username));
    }

    /** Get draft metadata: status, type, rounds, draft order, etc. */
    @GetMapping("/{draftId}")
    public ResponseEntity<DraftDto> getDraft(@PathVariable String draftId) {
        return ResponseEntity.ok(draftService.getDraft(draftId));
    }

    /** Get every pick made so far. Safe to poll on an interval while drafting is live. */
    @GetMapping("/{draftId}/picks")
    public ResponseEntity<List<DraftPickDto>> getPicks(@PathVariable String draftId) {
        return ResponseEntity.ok(draftService.getPicks(draftId));
    }

    /** Get a specific Sleeper user's picks so far, enriched with local player data. */
    @GetMapping("/{draftId}/my-picks")
    public ResponseEntity<List<DraftPickView>> getMyPicks(
            @PathVariable String draftId,
            @RequestParam String sleeperUserId) {
        return ResponseEntity.ok(draftService.getMyPicks(draftId, sleeperUserId));
    }

    /** Get the best remaining players overall and grouped by position (PG/SG/SF/PF/C). */
    @GetMapping("/{draftId}/best-available")
    public ResponseEntity<BestAvailableResponse> getBestAvailable(
            @PathVariable String draftId,
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(draftService.getBestAvailable(draftId, limit));
    }

    /** Debug counts for troubleshooting empty best-available results. */
    @GetMapping("/{draftId}/best-available/debug")
    public ResponseEntity<Map<String, Object>> getBestAvailableDebug(
            @PathVariable String draftId,
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(draftService.getBestAvailableDebug(draftId, limit));
    }
}
