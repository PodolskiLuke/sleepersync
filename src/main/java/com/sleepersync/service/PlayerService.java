package com.sleepersync.service;

import com.sleepersync.api.SleeperApiClient;
import com.sleepersync.model.dto.SleeperPlayerDto;
import com.sleepersync.model.entity.Player;
import com.sleepersync.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

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
}
