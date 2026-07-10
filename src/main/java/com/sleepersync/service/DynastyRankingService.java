package com.sleepersync.service;

import com.sleepersync.model.dto.AggregatedRankingEntry;
import com.sleepersync.model.dto.DraftPickDto;
import com.sleepersync.model.dto.DynastyRankingEntry;
import com.sleepersync.model.dto.RemainingDraftRankingsResponse;
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

    /**
     * Get all ranked players (no draft filtering).
     * Returns all players ranked by blended score from Sleeper + external sources.
     * Uses conservative rookie scoring so proven veterans rank higher than unproven prospects.
     */
    public List<AggregatedRankingEntry> getAllRankings() {
        List<Player> allPlayers = playerRepository.findAll();

        return buildRankings(allPlayers, Set.of(), Set.of(), false);
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

        Set<String> draftedNameNorms = picks.stream()
            .map(this::extractPickName)
            .flatMap(Optional::stream)
            .map(this::normalizeName)
            .filter(n -> !n.isBlank())
            .collect(Collectors.toSet());

        List<AggregatedRankingEntry> ranked = buildRankings(
                availablePlayers,
                pickedPlayerIds,
                draftedNameNorms,
                false
        );

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
                .filter(this::isRookieQualityCandidate)
                .limit(Math.max(limitPerPosition * 3, 60))
                .toList())
                .byPosition(byPosition)
                .build();
    }

            private List<AggregatedRankingEntry> buildRankings(
                List<Player> players,
                Set<String> excludedPlayerIds,
                Set<String> excludedNameNorms,
                boolean forDraftMode
            ) {
            List<DynastyRankingEntry> external = rankingScraperService.scrapeAllConfigured();

            Map<String, List<DynastyRankingEntry>> externalByName = external.stream()
                .filter(e -> e.getPlayerName() != null && !e.getPlayerName().isBlank())
                .collect(Collectors.groupingBy(e -> normalizeName(e.getPlayerName())));

            Map<String, List<DynastyRankingEntry>> externalByLastName = external.stream()
                .filter(e -> e.getPlayerName() != null && !e.getPlayerName().isBlank())
                .collect(Collectors.groupingBy(e -> lastNameOfNormalized(normalizeName(e.getPlayerName()))));

            List<AggregatedRankingEntry> candidates = new ArrayList<>();

            for (Player p : players) {
                if (p == null || p.getFullName() == null || p.getFullName().isBlank()) {
                continue;
                }

                String playerNameNorm = normalizeName(p.getFullName());
                if (p.getPlayerId() == null
                    || excludedPlayerIds.contains(p.getPlayerId())
                    || excludedNameNorms.contains(playerNameNorm)) {
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
                    .blendedScore(calcBlendedScore(p, externalAvg, sleeperRookie, hasExternalRank, forDraftMode))
                    .sourceRanks(sources)
                    .build();
                candidates.add(entry);
            }

            List<AggregatedRankingEntry> ranked = candidates.stream()
                .sorted(Comparator.comparing(AggregatedRankingEntry::getBlendedScore, Comparator.reverseOrder()))
                .toList();

            int rank = 1;
            for (AggregatedRankingEntry entry : ranked) {
                entry.setFinalRank(rank++);
            }

            return ranked;
            }

    private boolean isRookieQualityCandidate(AggregatedRankingEntry entry) {
        if (entry == null) {
            return false;
        }

        boolean hasTeam = hasNbaTeamCode(entry.getTeam());
        boolean hasExternal = entry.getExternalAvgRank() != null;
        Integer sleeperRank = entry.getSleeperSearchRank();
        boolean strongSleeperRank = sleeperRank != null && sleeperRank > 0 && sleeperRank <= 220;

        // Keep all rookies that are already on NBA teams.
        if (hasTeam) {
            return true;
        }

        // Unsigned/FA rookies are kept only if both signals are strong.
        if (!hasTeam && hasExternal && entry.getExternalAvgRank() <= 30 && strongSleeperRank) {
            return true;
        }

        return false;
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

    private double calcBlendedScore(Player p, Double externalAvgRank, boolean rookieCandidate, boolean hasExternalRank, boolean forDraftMode) {
        // All component scores are normalized to roughly 0..1 so that veterans
        // (scored on production + ADP) and rookies (scored on draft/consensus rank)
        // sit on ONE comparable scale. Higher score is better.
        //
        // forDraftMode=true: Used in draft context (remaining available players).
        //   Rookies with draft rankings can be scored high since they're competing
        //   for draft slots vs other rookies.
        //
        // forDraftMode=false: Used in all-players ranking context.
        //   Rookies should be scored conservatively compared to proven veterans,
        //   since unproven prospects shouldn't outrank multi-year track records.
        double prodNorm = 0.0;

        if (p != null && p.getFantasyPtsAvg() != null) {
            prodNorm = clamp(p.getFantasyPtsAvg() / 50.0, 0.0, 1.1);
        }

        // Shrink production toward zero for small sample sizes so a handful of
        // garbage-time games cannot rocket a fringe player up the board.
        double sampleConfidence = 1.0;
        if (p != null && p.getGamesPlayed() != null) {
            sampleConfidence = clamp(p.getGamesPlayed() / 20.0, 0.0, 1.0);
        }
        double prodEff = prodNorm * sampleConfidence;

        // Sleeper dynasty ADP (search_rank): lower number = more valued.
        Integer searchRank = (p != null) ? p.getSearchRank() : null;
        boolean hasAdp = searchRank != null && searchRank > 0;
        double adpNorm = hasAdp ? clamp((400.0 - searchRank) / 400.0, 0.0, 1.0) : 0.0;

        double extNorm = 0.0;
        if (externalAvgRank != null && externalAvgRank > 0) {
            extNorm = clamp((200.0 - externalAvgRank) / 200.0, 0.0, 1.0);
        }

        // Blend rookies and veterans into one board:
        // - Veterans: production gated by dynasty ADP (see below).
        // - Rookies with a draft/consensus rank: mapped into a dynasty-value band so
        //   elite prospects sit near the top and late picks sink, interleaving with vets.
        // - Rookies without any external signal: discounted so fringe names do not dominate.
        if (rookieCandidate) {
            boolean hasNbaTeam = hasNbaTeam(p);

            if (hasExternalRank && externalAvgRank != null) {
                // In draft mode, score rookies optimistically based on draft consensus
                if (forDraftMode) {
                    double extValue = clamp(0.62 - (externalAvgRank - 1.0) * 0.010, 0.06, 0.62);
                    double score = extValue + (prodEff * 0.40);
                    if (hasAdp) {
                        score += (adpNorm - 0.5) * 0.12;
                    }
                    score += hasNbaTeam ? 0.02 : -0.06;
                    return score;
                } else {
                    // In full-ranking mode, heavily cap prospect upside so incoming
                    // rookies don't leapfrog proven elite dynasty assets.
                    // Keep top prospects meaningfully valuable in dynasty while
                    // still below the proven top veteran tier.
                    // #1 rookie ~0.66, #10 ~0.62, #25 ~0.54 before tier/team tweaks.
                    double rookieExtValue = clamp(0.66 - (externalAvgRank - 1.0) * 0.005, 0.18, 0.66);
                    double score = rookieExtValue + (prodEff * 0.10);

                    // Prospect tier uplift (non-draft mode only).
                    if (externalAvgRank <= 3.0) {
                        score += 0.080;
                    } else if (externalAvgRank <= 8.0) {
                        score += 0.055;
                    } else if (externalAvgRank <= 15.0) {
                        score += 0.030;
                    } else if (externalAvgRank <= 30.0) {
                        score += 0.015;
                    }

                    // Taper the long tail of rookies more aggressively so premium
                    // prospects remain strong, but later rookie names do not crowd
                    // out proven veterans in the remaining-player board.
                    if (externalAvgRank > 12.0 && externalAvgRank <= 20.0) {
                        score -= (externalAvgRank - 12.0) * 0.010;
                    } else if (externalAvgRank > 20.0 && externalAvgRank <= 35.0) {
                        score -= 0.080 + ((externalAvgRank - 20.0) * 0.012);
                    } else if (externalAvgRank > 35.0) {
                        score -= 0.260 + Math.min((externalAvgRank - 35.0) * 0.006, 0.120);
                    }

                    if (hasAdp) {
                        score += (adpNorm - 0.5) * 0.10;
                    }
                    score += hasNbaTeam ? 0.015 : -0.05;
                    return score;
                }
            }

            // Rookie with no external signal: keep low so unknown free agents
            // stay near the bottom unless they are actually producing.
            double score = (prodEff * 0.40) - 0.10;
            if (hasAdp) {
                score += adpNorm * 0.20;
            }
            if (!hasNbaTeam) {
                score -= 0.10;
            }
            return score;
        }

        // Full all-player dynasty board (not draft-room mode):
        // keep the model ADP-first with moderate age and signal adjustments.
        if (!forDraftMode) {
            double score = 0.0;

            // Dynasty ADP is the strongest global market signal.
            score += adpNorm * 0.84;

            // External ranks are supportive but not dominant.
            score += extNorm * 0.06;

            // Production adds stability when available.
            score += prodEff * 0.03;

            // Slight premium for NBA role stability.
            score += hasNbaTeam(p) ? 0.007 : -0.03;

            // Age curve tuned for dynasty windows (general, not player-specific).
            score += ageDynastyAdjustment(p != null ? p.getAge() : null);

            // Elite ADP tiering to preserve foundational assets near the top.
            if (hasAdp) {
                if (searchRank <= 3) {
                    score += 0.11;
                } else if (searchRank <= 6) {
                    score += 0.06;
                } else if (searchRank <= 10) {
                    score += 0.035;
                } else if (searchRank <= 25) {
                    score += 0.015;
                } else if (searchRank <= 50) {
                    score += 0.006;
                }

                // Keep the board close to dynasty startup market behavior:
                // beyond the elite core, require stronger ADP alignment.
                if (searchRank > 6 && searchRank <= 20) {
                    score -= (searchRank - 6) * 0.003;
                } else if (searchRank > 20) {
                    score -= 0.042 + Math.min((searchRank - 20) * 0.001, 0.020);
                }
            }

            return score;
        }

        // Veterans: ADP is the market's dynasty valuation, so it BOTH anchors the score
        // AND gates how much we trust raw production. A big per-game line paired with a
        // poor/absent ADP (small-sample flukes, end-of-bench players) gets discounted.
        double adpAnchor = hasAdp ? adpNorm : 0.12;
        double adpConfidence = hasAdp ? clamp((300.0 - searchRank) / 300.0, 0.15, 1.0) : 0.25;
        double corroboratedProd = prodEff * adpConfidence;

        return (corroboratedProd * 0.62) + (adpAnchor * 0.32) + (extNorm * 0.06);
    }

    private double ageDynastyAdjustment(Integer age) {
        if (age == null) {
            return 0.0;
        }

        if (age <= 21) return 0.030;
        if (age <= 23) return 0.024;
        if (age <= 25) return 0.017;
        if (age <= 27) return 0.010;
        if (age <= 29) return 0.000;
        if (age == 30) return -0.006;
        if (age == 31) return -0.012;
        if (age == 32) return -0.020;
        if (age == 33) return -0.028;
        return -0.036;
    }

    private double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private boolean hasNbaTeam(Player player) {
        if (player == null || player.getTeam() == null) {
            return false;
        }
        return hasNbaTeamCode(player.getTeam());
    }

    private boolean hasNbaTeamCode(String teamValue) {
        if (teamValue == null) {
            return false;
        }

        String team = teamValue.trim();
        if (team.isBlank() || "FA".equalsIgnoreCase(team)) {
            return false;
        }
        return team.length() >= 2 && team.length() <= 4;
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
