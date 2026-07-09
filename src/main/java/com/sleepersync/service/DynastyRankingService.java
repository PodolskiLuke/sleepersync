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

        Map<String, List<DynastyRankingEntry>> externalByLastName = external.stream()
            .filter(e -> e.getPlayerName() != null && !e.getPlayerName().isBlank())
            .collect(Collectors.groupingBy(e -> lastNameOfNormalized(normalizeName(e.getPlayerName()))));

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

                    boolean sleeperRookie = isSleeperRookie(p);
                    List<DynastyRankingEntry> sources = findExternalSourcesForPlayer(
                        p,
                        externalByName,
                        externalByLastName,
                        sleeperRookie
                    );
            Double externalAvg = avgRank(sources);
                    boolean matchedRookieSource = sleeperRookie && sources.stream()
                    .anyMatch(s -> "rookie".equalsIgnoreCase(s.getSource()));

                boolean hasExternalRank = externalAvg != null;

                AggregatedRankingEntry entry = AggregatedRankingEntry.builder()
                    .playerId(p.getPlayerId())
                    .playerName(p.getFullName())
                    .position(p.getPosition())
                    .team(p.getTeam())
                        .rookie(sleeperRookie)
                    .sleeperFantasyPtsAvg(p.getFantasyPtsAvg())
                    .sleeperSearchRank(p.getSearchRank())
                    .externalAvgRank(externalAvg)
                    .externalRankCount(sources.size())
                    .blendedScore(calcBlendedScore(p, externalAvg, sleeperRookie, hasExternalRank))
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

    private double calcBlendedScore(Player p, Double externalAvgRank, boolean rookieCandidate, boolean hasExternalRank) {
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

        // Blend rookies and veterans into one board:
        // - Veterans: anchored to Sleeper production + ADP.
        // - Rookies with external rank: substantial external influence.
        // - Rookies without external rank: discounted so fringe names do not dominate.
        if (rookieCandidate) {
            if (hasExternalRank) {
                double base = (fptsComponent * 0.10) + (adpComponent * 0.30) + (externalComponent * 0.28);
                if (externalAvgRank != null && externalAvgRank <= 20) {
                    base += 0.015;
                }
                if (p == null || p.getSearchRank() == null || p.getSearchRank() <= 0) {
                    base -= 0.03;
                }
                return base;
            }

            double base = (fptsComponent * 0.05) + (adpComponent * 0.30);
            base -= 0.06;

            if (p == null || p.getSearchRank() == null || p.getSearchRank() <= 0 || p.getSearchRank() > 220) {
                base -= 0.10;
            }
            if (p == null || p.getTeam() == null || p.getTeam().isBlank() || "FA".equalsIgnoreCase(p.getTeam())) {
                base -= 0.06;
            }
            return base;
        }

        double base = (fptsComponent * 0.60) + (adpComponent * 0.30) + (externalComponent * 0.10);

        return base;
    }

    private List<DynastyRankingEntry> findExternalSourcesForPlayer(
            Player player,
            Map<String, List<DynastyRankingEntry>> externalByName,
            Map<String, List<DynastyRankingEntry>> externalByLastName,
            boolean sleeperRookie
    ) {
        if (player == null || player.getFullName() == null || player.getFullName().isBlank()) {
            return List.of();
        }

        String normName = normalizeName(player.getFullName());
        List<DynastyRankingEntry> exact = externalByName.getOrDefault(normName, List.of()).stream()
                .filter(e -> sleeperRookie || !"rookie".equalsIgnoreCase(e.getSource()))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }

        if (!sleeperRookie) {
            return List.of();
        }

        String last = lastNameOfNormalized(normName);
        if (last.isBlank() || last.length() < 4) {
            return List.of();
        }

        List<DynastyRankingEntry> byLast = externalByLastName.getOrDefault(last, List.of());
        if (byLast.isEmpty()) {
            return List.of();
        }

        String first = firstNameOfNormalized(normName);
        List<DynastyRankingEntry> narrowed = byLast.stream()
                .filter(e -> {
                    String extNorm = normalizeName(e.getPlayerName());
                    if (!extNorm.endsWith(" " + last) && !extNorm.equals(last)) {
                        return false;
                    }
                    if (first.isBlank()) {
                        return true;
                    }
                    return extNorm.startsWith(first + " ")
                            || extNorm.startsWith(first.substring(0, 1) + " ");
                })
                .filter(e -> positionCompatible(player.getPosition(), e.getPosition()))
                .toList();

        if (!narrowed.isEmpty()) {
            return narrowed;
        }

        return byLast.size() == 1 ? byLast : List.of();
    }

    private String firstNameOfNormalized(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return "";
        }
        String[] parts = normalizedName.split(" ");
        return parts.length > 0 ? parts[0] : "";
    }

    private String lastNameOfNormalized(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return "";
        }
        String[] parts = normalizedName.split(" ");
        return parts[parts.length - 1];
    }

    private boolean positionCompatible(String sleeperPos, String externalPos) {
        if (externalPos == null || externalPos.isBlank()) {
            return true;
        }
        if (sleeperPos == null || sleeperPos.isBlank()) {
            return true;
        }
        return matchesPosition(sleeperPos, "PG") && matchesPosition(externalPos, "PG")
                || matchesPosition(sleeperPos, "SG") && matchesPosition(externalPos, "SG")
                || matchesPosition(sleeperPos, "SF") && matchesPosition(externalPos, "SF")
                || matchesPosition(sleeperPos, "PF") && matchesPosition(externalPos, "PF")
                || matchesPosition(sleeperPos, "C") && matchesPosition(externalPos, "C");
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
