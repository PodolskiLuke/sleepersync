package com.sleepersync.controller;

import com.sleepersync.model.entity.Player;
import com.sleepersync.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
@CrossOrigin(origins = "*")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /** Trigger a full sync of all NBA players from Sleeper into the local DB */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncPlayers() {
        int count = playerService.syncAllPlayers();
        return ResponseEntity.ok(Map.of("synced", count, "message", "Player sync complete"));
    }

    /** Get all players from local DB */
    @GetMapping
    public ResponseEntity<List<Player>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }

    /** Get only active players */
    @GetMapping("/active")
    public ResponseEntity<List<Player>> getActivePlayers() {
        return ResponseEntity.ok(playerService.getActivePlayers());
    }

    /** Get a single player by Sleeper player ID */
    @GetMapping("/{playerId}")
    public ResponseEntity<Player> getPlayer(@PathVariable String playerId) {
        return playerService.getPlayerById(playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Search players by name */
    @GetMapping("/search")
    public ResponseEntity<List<Player>> searchPlayers(@RequestParam String name) {
        return ResponseEntity.ok(playerService.searchPlayersByName(name));
    }

    /** Get players filtered by position (e.g. PG, SG, SF, PF, C) */
    @GetMapping("/position/{position}")
    public ResponseEntity<List<Player>> getByPosition(@PathVariable String position) {
        return ResponseEntity.ok(playerService.getPlayersByPosition(position));
    }

    /** Get players filtered by NBA team abbreviation (e.g. LAL, BOS) */
    @GetMapping("/team/{team}")
    public ResponseEntity<List<Player>> getByTeam(@PathVariable String team) {
        return ResponseEntity.ok(playerService.getPlayersByTeam(team));
    }
}
