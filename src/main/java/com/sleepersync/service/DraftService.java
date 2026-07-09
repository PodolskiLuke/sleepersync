package com.sleepersync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sleepersync.api.SleeperApiClient;
import com.sleepersync.model.dto.BestAvailableResponse;
import com.sleepersync.model.dto.DraftDto;
import com.sleepersync.model.dto.DraftPickDto;
import com.sleepersync.model.dto.DraftPickView;
import com.sleepersync.model.dto.DraftUserResponse;
import com.sleepersync.model.dto.SleeperPlayerDto;
import com.sleepersync.model.entity.Player;
import com.sleepersync.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Powers the live Draft Helper tool:
 *  - resolve a Sleeper username -> user_id (so we can identify "my picks")
 *  - fetch draft info + picks (polled by the frontend while drafting is live)
 *  - compute best-available players, grouped by position, excluding anyone
 *    already picked
 *  - surface a single user's picks so far, enriched with local player data
 */
@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    /** Standard basketball position ordering for the "best available" board */
    private static final List<String> POSITION_ORDER = List.of("PG", "SG", "SF", "PF", "C");

    private final SleeperApiClient sleeperApiClient;
    private final PlayerRepository playerRepository;

    private static final long SLEEPER_PLAYERS_CACHE_TTL_MS = 10 * 60 * 1000L;
    private volatile Map<String, SleeperPlayerDto> sleeperPlayersCache = Map.of();
    private volatile long sleeperPlayersCacheAtMs = 0L;

    public DraftService(SleeperApiClient sleeperApiClient, PlayerRepository playerRepository) {
        this.sleeperApiClient = sleeperApiClient;
        this.playerRepository = playerRepository;
    }

    // -------------------------------------------------------------------------
    // User resolution
    // -------------------------------------------------------------------------

    public DraftUserResponse resolveUser(String username) {
        JsonNode user = sleeperApiClient.getUserByUsername(username.trim());
        if (user == null || user.get("user_id") == null) {
            throw new IllegalArgumentException(
                    "Sleeper username '" + username + "' was not found. Please check and try again.");
        }
        return DraftUserResponse.builder()
                .userId(user.get("user_id").asText())
                .username(user.hasNonNull("username") ? user.get("username").asText() : username)
                .displayName(user.hasNonNull("display_name") ? user.get("display_name").asText() : null)
                .avatar(user.hasNonNull("avatar") ? user.get("avatar").asText() : null)
                .build();
    }

    // -------------------------------------------------------------------------
    // Draft info / picks
    // -------------------------------------------------------------------------

    public DraftDto getDraft(String draftId) {
        DraftDto draft = sleeperApiClient.getDraft(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("Draft '" + draftId + "' was not found. Please check the draft ID.");
        }
        return draft;
    }

    public List<DraftPickDto> getPicks(String draftId) {
        return sleeperApiClient.getDraftPicks(draftId);
    }

    // -------------------------------------------------------------------------
    // My picks
    // -------------------------------------------------------------------------

    public List<DraftPickView> getMyPicks(String draftId, String sleeperUserId) {
        List<DraftPickDto> picks = getPicks(draftId);
        return picks.stream()
                .filter(p -> sleeperUserId.equals(p.getPickedBy()))
                .sorted(Comparator.comparing(DraftPickDto::getPickNo, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toView)
                .toList();
    }

    private DraftPickView toView(DraftPickDto pick) {
        Optional<Player> local = pick.getPlayerId() != null
                ? playerRepository.findByPlayerId(pick.getPlayerId())
                : Optional.empty();

        Map<String, String> meta = pick.getMetadata() != null ? pick.getMetadata() : Map.of();

        String fullName = local.map(Player::getFullName)
                .orElseGet(() -> {
                    String first = meta.getOrDefault("first_name", "");
                    String last = meta.getOrDefault("last_name", "");
                    String name = (first + " " + last).trim();
                    return name.isEmpty() ? "Unknown Player" : name;
                });

        String position = local.map(Player::getPosition).orElse(meta.get("position"));
        String team = local.map(Player::getTeam).orElse(meta.get("team"));
        String injuryStatus = local.map(Player::getInjuryStatus).orElse(null);

        return DraftPickView.builder()
                .round(pick.getRound())
                .pickNo(pick.getPickNo())
                .draftSlot(pick.getDraftSlot())
                .rosterId(pick.getRosterId())
                .pickedBy(pick.getPickedBy())
                .isKeeper(pick.getIsKeeper())
                .playerId(pick.getPlayerId())
                .fullName(fullName)
                .position(position)
                .team(team)
                .injuryStatus(injuryStatus)
                .build();
    }

    // -------------------------------------------------------------------------
    // Best available
    // -------------------------------------------------------------------------

    public BestAvailableResponse getBestAvailable(String draftId, int limitPerPosition) {
        List<Player> available = getAllAvailablePlayers(draftId);
        Set<String> pickedPlayerIds = getPicks(draftId).stream()
                .map(DraftPickDto::getPlayerId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        List<Player> overall = available.stream().limit(Math.max(limitPerPosition, 30)).toList();

        Map<String, List<Player>> byPosition = new LinkedHashMap<>();
        for (String position : POSITION_ORDER) {
            List<Player> top = available.stream()
                    .filter(p -> matchesPosition(p.getPosition(), position))
                    .limit(limitPerPosition)
                    .toList();
            byPosition.put(position, top);
        }

        return BestAvailableResponse.builder()
                .overall(overall)
                .byPosition(byPosition)
                .totalPicksMade(pickedPlayerIds.size())
                .build();
    }

            public List<Player> getAllAvailablePlayers(String draftId) {
            List<DraftPickDto> picks = getPicks(draftId);
            Set<String> pickedPlayerIds = picks.stream()
                .map(DraftPickDto::getPlayerId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

            SourcePlayersResult source = getSourcePlayers();
            List<Player> sourcePlayers = source.players();

            return sourcePlayers.stream()
                .filter(p -> p.getPlayerId() != null && !p.getPlayerId().isBlank())
                .filter(p -> !pickedPlayerIds.contains(p.getPlayerId()))
                .sorted(Comparator
                    .comparing(Player::getFantasyPtsAvg,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Player::getSearchRank,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
            }

            public Map<String, Object> getBestAvailableDebug(String draftId, int limitPerPosition) {
            List<DraftPickDto> picks = getPicks(draftId);
            Set<String> pickedPlayerIds = picks.stream()
                .map(DraftPickDto::getPlayerId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

            SourcePlayersResult source = getSourcePlayers();
            List<Player> available = source.players().stream()
                .filter(p -> p.getPlayerId() != null && !p.getPlayerId().isBlank())
                .filter(p -> !pickedPlayerIds.contains(p.getPlayerId()))
                .sorted(Comparator
                    .comparing(Player::getFantasyPtsAvg,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Player::getSearchRank,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("draftId", draftId);
            out.put("pickedIdsCount", pickedPlayerIds.size());
            out.put("sourcePlayersCount", source.players().size());
            out.put("usedFallbackPlayersApi", source.usedFallback());
            out.put("availableCount", available.size());

            Map<String, Integer> byPosCounts = new HashMap<>();
            for (String position : POSITION_ORDER) {
                int count = (int) available.stream()
                    .filter(p -> matchesPosition(p.getPosition(), position))
                    .limit(limitPerPosition)
                    .count();
                byPosCounts.put(position, count);
            }
            out.put("topByPositionCount", byPosCounts);

            List<Map<String, Object>> sample = available.stream()
                .limit(10)
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("playerId", p.getPlayerId());
                    m.put("fullName", p.getFullName());
                    m.put("position", p.getPosition());
                    m.put("team", p.getTeam());
                    m.put("searchRank", p.getSearchRank());
                    m.put("fantasyPtsAvg", p.getFantasyPtsAvg());
                    return m;
                })
                .toList();
            out.put("sampleTopAvailable", sample);

            return out;
            }

            private SourcePlayersResult getSourcePlayers() {
        List<Player> localPlayers = playerRepository.findAll();

        Map<String, SleeperPlayerDto> sleeperPlayers = getSleeperPlayersCached();
        if (sleeperPlayers == null || sleeperPlayers.isEmpty()) {
            if (!localPlayers.isEmpty()) {
                return new SourcePlayersResult(localPlayers, false);
            }
            return new SourcePlayersResult(Collections.emptyList(), true);
        }

        if (localPlayers.isEmpty()) {
            log.warn("Local players table is empty; using Sleeper /players fallback for best-available pool");
            List<Player> fallbackPlayers = sleeperPlayers.values().stream()
                    .filter(dto -> dto.getPlayerId() != null && !dto.getPlayerId().isBlank())
                    .map(this::toPlayer)
                    .toList();
            return new SourcePlayersResult(fallbackPlayers, true);
        }

        Map<String, Player> mergedById = new LinkedHashMap<>();
        for (Player local : localPlayers) {
            if (local.getPlayerId() == null || local.getPlayerId().isBlank()) {
                continue;
            }
            mergedById.put(local.getPlayerId(), local);
        }

        for (SleeperPlayerDto dto : sleeperPlayers.values()) {
            if (dto.getPlayerId() == null || dto.getPlayerId().isBlank()) {
                continue;
            }
            if (!mergedById.containsKey(dto.getPlayerId())) {
                mergedById.put(dto.getPlayerId(), toPlayer(dto));
            }
        }

        return new SourcePlayersResult(new ArrayList<>(mergedById.values()), false);

    }

    private synchronized Map<String, SleeperPlayerDto> getSleeperPlayersCached() {
        long now = System.currentTimeMillis();
        if (!sleeperPlayersCache.isEmpty() && (now - sleeperPlayersCacheAtMs) < SLEEPER_PLAYERS_CACHE_TTL_MS) {
            return sleeperPlayersCache;
        }

        Map<String, SleeperPlayerDto> fetched = sleeperApiClient.getAllPlayers();
        if (fetched != null && !fetched.isEmpty()) {
            sleeperPlayersCache = fetched;
            sleeperPlayersCacheAtMs = now;
            return sleeperPlayersCache;
        }

        return sleeperPlayersCache;
    }

    private Player toPlayer(SleeperPlayerDto dto) {
        String fullName = dto.getFullName() != null
                ? dto.getFullName()
                : ((dto.getFirstName() != null ? dto.getFirstName() : "")
                + " "
                + (dto.getLastName() != null ? dto.getLastName() : "")).trim();

        return Player.builder()
                .playerId(dto.getPlayerId())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .fullName(fullName.isBlank() ? "Unknown Player" : fullName)
                .position(dto.getPosition())
                .team(dto.getTeam())
                .age(dto.getAge())
            .yearsExp(dto.getYearsExp())
                .injuryStatus(dto.getInjuryStatus())
                .searchRank(dto.getSearchRank())
                .active(dto.getActive())
                .build();
    }

    private boolean matchesPosition(String playerPosition, String wantedPosition) {
        if (playerPosition == null || playerPosition.isBlank()) {
            return false;
        }

        String normalizedWanted = wantedPosition.toUpperCase();
        String[] tokens = playerPosition.toUpperCase().split("[^A-Z]+");
        Set<String> tokenSet = Arrays.stream(tokens)
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());

        if (tokenSet.contains(normalizedWanted)) {
            return true;
        }

        // Sleeper sometimes uses generic slots (G/F) instead of strict PG/SG/SF/PF.
        return switch (normalizedWanted) {
            case "PG", "SG" -> tokenSet.contains("G");
            case "SF", "PF" -> tokenSet.contains("F");
            case "C" -> tokenSet.contains("C");
            default -> false;
        };
    }

    private record SourcePlayersResult(List<Player> players, boolean usedFallback) {
    }
}
