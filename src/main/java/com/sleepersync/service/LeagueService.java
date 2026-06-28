package com.sleepersync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sleepersync.api.SleeperApiClient;
import com.sleepersync.model.dto.LeagueDto;
import com.sleepersync.model.dto.MatchupDto;
import com.sleepersync.model.dto.RosterDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeagueService {

    private static final Logger log = LoggerFactory.getLogger(LeagueService.class);

    private final SleeperApiClient sleeperApiClient;

    public LeagueService(SleeperApiClient sleeperApiClient) {
        this.sleeperApiClient = sleeperApiClient;
    }

    public LeagueDto getLeague(String leagueId) {
        return sleeperApiClient.getLeague(leagueId);
    }

    public List<RosterDto> getRosters(String leagueId) {
        return sleeperApiClient.getRosters(leagueId);
    }

    public List<MatchupDto> getMatchups(String leagueId, int week) {
        return sleeperApiClient.getMatchups(leagueId, week);
    }

    public List<LeagueDto> getLeaguesForUser(String userId) {
        return sleeperApiClient.getLeaguesForUser(userId);
    }

    /**
     * Looks up a user by Sleeper username, then returns all their leagues.
     */
    public List<LeagueDto> getLeaguesByUsername(String username) {
        JsonNode user = sleeperApiClient.getUserByUsername(username);
        if (user == null || user.get("user_id") == null) {
            log.warn("No user found for username: {}", username);
            return List.of();
        }
        String userId = user.get("user_id").asText();
        return sleeperApiClient.getLeaguesForUser(userId);
    }
}
