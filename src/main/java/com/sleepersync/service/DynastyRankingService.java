package com.sleepersync.service;

import com.sleepersync.model.dto.AggregatedRankingEntry;
import com.sleepersync.model.dto.DraftPickDto;
import com.sleepersync.model.dto.DynastyRankingEntry;
import com.sleepersync.model.dto.RemainingDraftRankingsResponse;
import com.sleepersync.model.dto.BestAvailableResponse;
import com.sleepersync.model.entity.Player;
import com.sleepersync.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class DynastyRankingService {

    private static final List<String> POSITION_ORDER = List.of("PG", "SG", "SF", "PF", "C");

    private final RankingScraperService rankingScraperService;
    private final DraftService draftService;
    private final PlayerRepository playerRepository;

    public DynastyRankingService(
            RankingScraperService rankingScraperService,
            DraftService draftService,
            PlayerRepository playerRepository
    ) {
        this.rankingScraperService = rankingScraperService;
        this.draftService = draftService;
        this.playerRepository = playerRepository;
    }

    public List<DynastyRankingEntry> scrapeConfiguredSources() {
        return rankingScraperService.scrapeAllConfigured();
    }

    public RemainingDraftRankingsResponse getRemainingRankings(String draftId, int limitPerPosition) {
        List<DraftPickDto> picks = draftService.getPicks(draftId);
        Set<String> pickedPlayerIds = picks.stream()
            .map(DraftPickDto::getPlayerId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());

        // Reuse the exact Sleeper-mode drafted filtering logic, but use the full
        // available pool (not capped top-N) so rookie candidates are not dropped.
        List<Player> availablePlayers = draftService.getAllAvailablePlayers(draftId);

        Map<String, Player> byNameNorm = availablePlayers.stream()
                .filter(p -> p.getFullName() != null && !p.getFullName().isBlank())
                .collect(Collectors.toMap(
                        p -> normalizeName(p.getFullName()),
                        p -> p,
                        (a, b) -> a
                ));

        Set<String> draftedNameNorms = picks.stream()
            .map(this::extractPickName)
            .flatMap(Optional::stream)
            .map(this::normalizeName)
            .filter(n -> !n.isBlank())
            .collect(Collectors.toSet());

        List<DynastyRankingEntry> external = rankingScraperService.scrapeAllConfigured();

        Map<String, List<DynastyRankingEntry>> externalByName = external.stream()
                .filter(e -> e.getPlayerName() != null && !e.getPlayerName().isBlank())
                .collect(Collectors.groupingBy(e -> normalizeName(e.getPlayerName())));

        List<AggregatedRankingEntry> candidates = new ArrayList<>();

        // Existing Sleeper/local players that are not drafted
        for (Player p : availablePlayers) {
            String playerNameNorm = normalizeName(p.getFullName());
            if (p.getPlayerId() == null
                || pickedPlayerIds.contains(p.getPlayerId())
                || draftedNameNorms.contains(playerNameNorm)
                || isDraftedByLastNameHeuristic(playerNameNorm, draftedNameNorms)) {
                continue;
            }

            String norm = normalizeName(p.getFullName());
            List<DynastyRankingEntry> sources = externalByName.getOrDefault(norm, List.of());
            Double externalAvg = avgRank(sources);
                boolean matchedRookieSource = sources.stream()
                    .anyMatch(s -> "rookie".equalsIgnoreCase(s.getSource()));

            AggregatedRankingEntry entry = AggregatedRankingEntry.builder()
                    .playerId(p.getPlayerId())
                    .playerName(p.getFullName())
                    .position(p.getPosition())
                    .team(p.getTeam())
                    .rookie(isSleeperRookie(p) || matchedRookieSource)
                    .sleeperFantasyPtsAvg(p.getFantasyPtsAvg())
                    .sleeperSearchRank(p.getSearchRank())
                    .externalAvgRank(externalAvg)
                    .externalRankCount(sources.size())
                    .blendedScore(calcBlendedScore(p, externalAvg))
                    .sourceRanks(sources)
                    .build();
            candidates.add(entry);
        }

        List<AggregatedRankingEntry> ranked = candidates.stream()
                .sorted(Comparator.comparing(AggregatedRankingEntry::getBlendedScore, Comparator.reverseOrder()))
                .toList();

        int rank = 1;
        for (AggregatedRankingEntry e : ranked) {
            e.setFinalRank(rank++);
        }

        Map<String, List<AggregatedRankingEntry>> byPosition = new LinkedHashMap<>();
        for (String pos : POSITION_ORDER) {
            List<AggregatedRankingEntry> top = ranked.stream()
                    .filter(e -> matchesPosition(e.getPosition(), pos))
                    .limit(limitPerPosition)
                    .toList();
            byPosition.put(pos, top);
        }

        return RemainingDraftRankingsResponse.builder()
                .draftId(draftId)
                .pickedCount(pickedPlayerIds.size())
                .candidateCount(ranked.size())
                .overall(ranked.stream().limit(Math.max(limitPerPosition, 50)).toList())
            .rookies(ranked.stream()
                .filter(AggregatedRankingEntry::isRookie)
                .limit(Math.max(limitPerPosition * 4, 100))
                .toList())
                .byPosition(byPosition)
                .build();
    }

    private Optional<String> extractPickName(DraftPickDto pick) {
        if (pick == null || pick.getMetadata() == null) {
            return Optional.empty();
        }
        String first = pick.getMetadata().getOrDefault("first_name", "").trim();
        String last = pick.getMetadata().getOrDefault("last_name", "").trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? Optional.empty() : Optional.of(full);
    }

    private Double avgRank(List<DynastyRankingEntry> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return sources.stream().mapToInt(DynastyRankingEntry::getRank).average().orElse(999.0);
    }

    private double calcBlendedScore(Player p, Double externalAvgRank) {
        // Higher score is better.
        double fptsComponent = 0;
        double adpComponent = 0;
        double externalComponent = 0;

        if (p != null && p.getFantasyPtsAvg() != null) {
            fptsComponent = p.getFantasyPtsAvg() * 1.0;
        }

        if (p != null && p.getSearchRank() != null && p.getSearchRank() > 0) {
            adpComponent = (1000.0 - p.getSearchRank()) / 1000.0;
        }

        if (externalAvgRank != null && externalAvgRank > 0) {
            externalComponent = (1000.0 - externalAvgRank) / 1000.0;
        }

        // Keep blended board anchored to Sleeper availability + ADP. External ranks
        // influence ordering but should not drown out vetted Sleeper players.
        double base = (fptsComponent * 0.60) + (adpComponent * 0.30) + (externalComponent * 0.10);

        // Mild boost for players with multiple external confirmations.
        // (Handled by caller through externalRankCount visibility; no hard rookie boost.)
        return base;
    }

    private boolean matchesPosition(String playerPosition, String wanted) {
        if (playerPosition == null || playerPosition.isBlank()) {
            return false;
        }
        String normalized = playerPosition.toUpperCase(Locale.ROOT);
        String[] tokens = normalized.split("[^A-Z]+") ;
        Set<String> set = Arrays.stream(tokens).filter(t -> !t.isBlank()).collect(Collectors.toSet());

        if (set.contains(wanted)) return true;
        if (("PG".equals(wanted) || "SG".equals(wanted)) && set.contains("G")) return true;
        if (("SF".equals(wanted) || "PF".equals(wanted)) && set.contains("F")) return true;
        return "C".equals(wanted) && set.contains("C");
    }

    private String normalizeName(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private boolean isDraftedByLastNameHeuristic(String normalizedName, Set<String> draftedNameNorms) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return false;
        }

        String[] parts = normalizedName.split(" ");
        if (parts.length < 2) {
            return false;
        }

        String last = parts[parts.length - 1];
        if (last.length() < 4) {
            return false;
        }

        return draftedNameNorms.stream().anyMatch(dn -> dn.endsWith(" " + last));
    }

    private boolean isSleeperRookie(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getYearsExp() != null) {
            return player.getYearsExp() == 0;
        }
        return false;
    }
}
