package com.sleepersync.controller;

import com.sleepersync.model.dto.LeagueDto;
import com.sleepersync.model.dto.MatchupDto;
import com.sleepersync.model.dto.RosterDto;
import com.sleepersync.service.LeagueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
@CrossOrigin(origins = "*")
public class LeagueController {

    private final LeagueService leagueService;

    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    /** Get league info by league ID */
    @GetMapping("/{leagueId}")
    public ResponseEntity<LeagueDto> getLeague(@PathVariable String leagueId) {
        LeagueDto league = leagueService.getLeague(leagueId);
        return league != null ? ResponseEntity.ok(league) : ResponseEntity.notFound().build();
    }

    /** Get all rosters in a league */
    @GetMapping("/{leagueId}/rosters")
    public ResponseEntity<List<RosterDto>> getRosters(@PathVariable String leagueId) {
        return ResponseEntity.ok(leagueService.getRosters(leagueId));
    }

    /** Get matchups for a specific week */
    @GetMapping("/{leagueId}/matchups/{week}")
    public ResponseEntity<List<MatchupDto>> getMatchups(
            @PathVariable String leagueId,
            @PathVariable int week) {
        return ResponseEntity.ok(leagueService.getMatchups(leagueId, week));
    }

    /** Get all leagues for a user by their Sleeper user ID */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LeagueDto>> getLeaguesByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(leagueService.getLeaguesForUser(userId));
    }

    /** Get all leagues for a user by their Sleeper username */
    @GetMapping("/username/{username}")
    public ResponseEntity<List<LeagueDto>> getLeaguesByUsername(@PathVariable String username) {
        return ResponseEntity.ok(leagueService.getLeaguesByUsername(username));
    }
}
