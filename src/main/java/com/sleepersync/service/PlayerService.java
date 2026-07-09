package com.sleepersync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sleepersync.api.SleeperApiClient;
import com.sleepersync.model.dto.SleeperPlayerDto;
import com.sleepersync.model.entity.Player;
import com.sleepersync.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    /**
     * Standard fantasy points league scoring weights.
     * pts*1 + reb*1.2 + ast*1.5 + stl*3 + blk*3 - to*1 + fg3m*0.5
     */
    private static final double W_PTS  = 1.0;
    private static final double W_REB  = 1.2;
    private static final double W_AST  = 1.5;
    private static final double W_STL  = 3.0;
    private static final double W_BLK  = 3.0;
    private static final double W_TO   = -1.0;
    private static final double W_FG3M = 0.5;

    @Value("${sleeper.api.season}")
    private String currentSeason;

    private final SleeperApiClient sleeperApiClient;
    private final PlayerRepository playerRepository;

    public PlayerService(SleeperApiClient sleeperApiClient, PlayerRepository playerRepository) {
        this.sleeperApiClient = sleeperApiClient;
        this.playerRepository = playerRepository;
    }

    /**
     * Syncs all NBA players from the Sleeper API into the local database.
     * Safe to call repeatedly - uses save (upsert) semantics.
     */
    @Transactional
    public int syncAllPlayers() {
        log.info("Starting full player sync from Sleeper API...");
        Map<String, SleeperPlayerDto> playerMap = sleeperApiClient.getAllPlayers();

        if (playerMap.isEmpty()) {
            log.warn("No players returned from Sleeper API");
            return 0;
        }

        List<Player> entities = playerMap.values().stream()
                .filter(dto -> dto.getPlayerId() != null)
                .map(this::mapToEntity)
                .toList();

        playerRepository.saveAll(entities);
        log.info("Synced {} players to database", entities.size());
        return entities.size();
    }

    /**
     * Fetches season stats from Sleeper and updates each player's per-game
     * averages and fantasy points average in the local database.
     *
     * Fantasy pts = pts*1 + reb*1.2 + ast*1.5 + stl*3 + blk*3 - to*1 + fg3m*0.5
     *
     * Fetches the configured season AND the prior season so stats are present
     * even during a season rollover period.
     */
    @Transactional
    public int syncPlayerStats() {
        int currentSeasonInt;
        try {
            currentSeasonInt = Integer.parseInt(currentSeason);
        } catch (NumberFormatException e) {
            currentSeasonInt = java.time.Year.now().getValue();
        }

        int updated = 0;
        // Try current season first, fall back to prior if empty
        for (int s = currentSeasonInt; s >= currentSeasonInt - 1; s--) {
            Map<String, JsonNode> statsMap = sleeperApiClient.getSeasonStats(String.valueOf(s));
            if (statsMap.isEmpty()) continue;

            log.info("Applying stats from season {} to {} players in DB", s, statsMap.size());
            for (Map.Entry<String, JsonNode> entry : statsMap.entrySet()) {
                String playerId = entry.getKey();
                JsonNode stats   = entry.getValue();

                Optional<Player> opt = playerRepository.findByPlayerId(playerId);
                if (opt.isEmpty()) continue;

                Player player = opt.get();

                int gp = getInt(stats, "gp");
                if (gp <= 0) continue;

                double pts  = getDouble(stats, "pts");
                double reb  = getDouble(stats, "reb");
                double ast  = getDouble(stats, "ast");
                double stl  = getDouble(stats, "stl");
                double blk  = getDouble(stats, "blk");
                double to   = getDouble(stats, "to");
                double fg3m = getDouble(stats, "fg3m");

                player.setGamesPlayed(gp);
                player.setAvgPts(round(pts / gp));
                player.setAvgReb(round(reb / gp));
                player.setAvgAst(round(ast / gp));
                player.setAvgStl(round(stl / gp));
                player.setAvgBlk(round(blk / gp));
                player.setAvgTo(round(to / gp));
                player.setAvgFg3m(round(fg3m / gp));

                double fpts = (pts * W_PTS + reb * W_REB + ast * W_AST
                             + stl * W_STL + blk * W_BLK + to * W_TO
                             + fg3m * W_FG3M) / gp;
                player.setFantasyPtsAvg(round(fpts));

                playerRepository.save(player);
                updated++;
            }
            if (updated > 0) break; // stop after first season that has data
        }

        log.info("Stats sync complete — updated {} players", updated);
        return updated;
    }

    public List<Player> getAllPlayers() {
        return playerRepository.findAll();
    }

    public List<Player> getActivePlayers() {
        return playerRepository.findByActiveTrue();
    }

    public Optional<Player> getPlayerById(String playerId) {
        return playerRepository.findByPlayerId(playerId);
    }

    public List<Player> searchPlayersByName(String name) {
        return playerRepository.findByFullNameContainingIgnoreCase(name);
    }

    public List<Player> getPlayersByPosition(String position) {
        return playerRepository.findByPosition(position.toUpperCase());
    }

    public List<Player> getPlayersByTeam(String team) {
        return playerRepository.findByTeam(team.toUpperCase());
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private Player mapToEntity(SleeperPlayerDto dto) {
        String fullName = dto.getFullName() != null
                ? dto.getFullName()
                : (dto.getFirstName() != null && dto.getLastName() != null)
                    ? dto.getFirstName() + " " + dto.getLastName()
                    : dto.getFirstName();

        return Player.builder()
                .playerId(dto.getPlayerId())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .fullName(fullName)
                .position(dto.getPosition())
                .team(dto.getTeam())
                .age(dto.getAge())
                .yearsExp(dto.getYearsExp())
                .status(dto.getStatus())
                .injuryStatus(dto.getInjuryStatus())
                .injuryNotes(dto.getInjuryNotes())
                .active(dto.getActive())
                .searchRank(dto.getSearchRank())
                .college(dto.getCollege())
                .birthDate(dto.getBirthDate())
                .lastSynced(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int getInt(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asInt(0) : 0;
    }

    private double getDouble(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asDouble(0) : 0;
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
