package com.sleepersync.service;

import com.sleepersync.api.SleeperApiClient;
import com.sleepersync.model.dto.BestAvailableResponse;
import com.sleepersync.model.dto.DraftPickDto;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @Mock
    private SleeperApiClient sleeperApiClient;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private DraftService draftService;

    @Test
    void getBestAvailable_enrichesOverallAndPositionBucketsWithFantasyPositions() {
        Player klay = Player.builder()
                .playerId("1081")
                .fullName("Klay Thompson")
                .position("SF")
                .team("DAL")
                .searchRank(119)
                .age(36)
                .active(true)
                .build();

        SleeperPlayerDto sleeperKlay = new SleeperPlayerDto();
        sleeperKlay.setPlayerId("1081");
        sleeperKlay.setFullName("Klay Thompson");
        sleeperKlay.setPosition("SF");
        sleeperKlay.setFantasyPositions(new String[] {"PF", "SF", "SG"});

        when(sleeperApiClient.getDraftPicks("draft-1")).thenReturn(List.of());
        when(playerRepository.findAll()).thenReturn(List.of(klay));
        when(sleeperApiClient.getAllPlayers()).thenReturn(Map.of("1081", sleeperKlay));

        BestAvailableResponse response = draftService.getBestAvailable("draft-1", 15);

        assertNotNull(response);
        assertEquals(1, response.getOverall().size());
        assertEquals("PF/SF/SG", response.getOverall().get(0).getPosition());
        assertEquals("PF/SF/SG", response.getOverall().get(0).getEligiblePositions());

        assertTrue(response.getByPosition().get("SG").stream()
                .anyMatch(player -> "1081".equals(player.getPlayerId()) && "PF/SF/SG".equals(player.getPosition())));
        assertTrue(response.getByPosition().get("SF").stream()
                .anyMatch(player -> "1081".equals(player.getPlayerId())));
        assertTrue(response.getByPosition().get("PF").stream()
                .anyMatch(player -> "1081".equals(player.getPlayerId())));
    }

    @Test
    void getMyPicks_prefersFantasyPositionsOverMetadataPosition() {
        Player beal = Player.builder()
                .playerId("1128")
                .fullName("Bradley Beal")
                .position("SG")
                .team("PHX")
                .build();

        SleeperPlayerDto sleeperBeal = new SleeperPlayerDto();
        sleeperBeal.setPlayerId("1128");
        sleeperBeal.setFullName("Bradley Beal");
        sleeperBeal.setPosition("SG");
        sleeperBeal.setFantasyPositions(new String[] {"SF", "SG"});

        DraftPickDto pick = new DraftPickDto();
        pick.setPlayerId("1128");
        pick.setPickedBy("user-1");
        pick.setPickNo(7);
        pick.setMetadata(Map.of("position", "SG", "first_name", "Bradley", "last_name", "Beal"));

        when(sleeperApiClient.getDraftPicks("draft-1")).thenReturn(List.of(pick));
        when(playerRepository.findByPlayerId("1128")).thenReturn(java.util.Optional.of(beal));
        when(sleeperApiClient.getAllPlayers()).thenReturn(Map.of("1128", sleeperBeal));

        var myPicks = draftService.getMyPicks("draft-1", "user-1");

        assertEquals(1, myPicks.size());
        assertEquals("SF/SG", myPicks.get(0).getPosition());
        assertEquals("Bradley Beal", myPicks.get(0).getFullName());
    }
}
