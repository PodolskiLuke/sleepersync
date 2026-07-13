package com.sleepersync.service;

import com.sleepersync.model.dto.AggregatedRankingEntry;
import com.sleepersync.model.dto.DynastyRankingEntry;
import com.sleepersync.model.dto.SleeperPlayerDto;
import com.sleepersync.model.entity.Player;
import com.sleepersync.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynastyRankingServiceTest {

    @Mock
    private RankingScraperService rankingScraperService;

    @Mock
    private DraftService draftService;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private DynastyRankingService dynastyRankingService;

    @Test
    void getAllRankings_setsEligiblePositionsFromSleeperFantasyPositions() {
        Player klay = Player.builder()
                .playerId("1081")
                .fullName("Klay Thompson")
                .position("SF")
                .team("DAL")
                .searchRank(119)
                .fantasyPtsAvg(19.1)
                .gamesPlayed(60)
                .age(36)
                .yearsExp(10)
                .build();

        SleeperPlayerDto sleeperKlay = new SleeperPlayerDto();
        sleeperKlay.setPlayerId("1081");
        sleeperKlay.setFullName("Klay Thompson");
        sleeperKlay.setPosition("SF");
        sleeperKlay.setFantasyPositions(new String[] {"PF", "SF", "SG"});

        when(playerRepository.findAll()).thenReturn(List.of(klay));
        when(rankingScraperService.scrapeAllConfigured()).thenReturn(List.of(
                DynastyRankingEntry.builder()
                        .source("source-a")
                        .playerName("Klay Thompson")
                        .position("SF")
                        .rank(18)
                        .build()
        ));
        when(draftService.getSleeperPlayersSnapshot()).thenReturn(Map.of("1081", sleeperKlay));
        when(draftService.resolveBestPositionForPlayer(klay, Map.of("1081", sleeperKlay))).thenReturn("PF/SF/SG");

        List<AggregatedRankingEntry> rankings = dynastyRankingService.getAllRankings();

        assertEquals(1, rankings.size());
        AggregatedRankingEntry entry = rankings.get(0);
        assertNotNull(entry);
        assertEquals("PF/SF/SG", entry.getPosition());
        assertEquals("PF/SF/SG", entry.getEligiblePositions());
        assertFalse(entry.isRookie());
    }
}
