package com.sleepersync.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sleepersync.model.dto.DraftDto;
import com.sleepersync.model.dto.DraftPickDto;
import com.sleepersync.model.dto.LeagueDto;
import com.sleepersync.model.dto.MatchupDto;
import com.sleepersync.model.dto.RosterDto;
import com.sleepersync.model.dto.SleeperPlayerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for all Sleeper API interactions.
 * Base URL: https://api.sleeper.app/v1
 *
 * Key endpoints covered:
 *  - GET /players/nba                              -> all NBA players
 *  - GET /league/{league_id}                       -> league info
 *  - GET /league/{league_id}/rosters               -> all rosters in a league
 *  - GET /league/{league_id}/matchups/{week}       -> week matchups
 *  - GET /user/{username}                          -> user by username
 *  - GET /user/{user_id}/leagues/nba/{season}      -> all leagues for a user
 *  - GET /draft/{draft_id}/picks                   -> draft picks
 */
@Component
public class SleeperApiClient {

    private static final Logger log = LoggerFactory.getLogger(SleeperApiClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Value("${sleeper.api.sport}")
    private String sport;

    @Value("${sleeper.api.season}")
    private String season;

    public SleeperApiClient(RestTemplate restTemplate, SleeperApiConfig config) {
        this.restTemplate = restTemplate;
        this.baseUrl = config.getBaseUrl();
    }

    // -------------------------------------------------------------------------
    // Players
    // -------------------------------------------------------------------------

    /**
     * Fetches ALL NBA players from Sleeper.
     * Returns a map of player_id -> player data.
     * Note: This is a large payload (~5MB). Cache locally after first fetch.
     */
    public Map<String, SleeperPlayerDto> getAllPlayers() {
        String url = baseUrl + "/players/" + sport;
        log.info("Fetching all {} players from Sleeper API", sport.toUpperCase());
        try {
            ResponseEntity<Map<String, SleeperPlayerDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, SleeperPlayerDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch players: {} - {}", e.getStatusCode(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    // -------------------------------------------------------------------------
    // League
    // -------------------------------------------------------------------------

    /**
     * Fetches league info by league ID.
     */
    public LeagueDto getLeague(String leagueId) {
        String url = baseUrl + "/league/" + leagueId;
        log.info("Fetching league {}", leagueId);
        try {
            return restTemplate.getForObject(url, LeagueDto.class);
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch league {}: {}", leagueId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches all rosters in a league.
     */
    public List<RosterDto> getRosters(String leagueId) {
        String url = baseUrl + "/league/" + leagueId + "/rosters";
        log.info("Fetching rosters for league {}", leagueId);
        try {
            ResponseEntity<List<RosterDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<RosterDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch rosters for league {}: {}", leagueId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches matchups for a given week.
     */
    public List<MatchupDto> getMatchups(String leagueId, int week) {
        String url = baseUrl + "/league/" + leagueId + "/matchups/" + week;
        log.info("Fetching matchups for league {} week {}", leagueId, week);
        try {
            ResponseEntity<List<MatchupDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<MatchupDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch matchups for league {} week {}: {}", leagueId, week, e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Users
    // -------------------------------------------------------------------------

    /**
     * Fetches a Sleeper user by username.
     */
    public JsonNode getUserByUsername(String username) {
        String url = baseUrl + "/user/" + username;
        log.info("Fetching user by username: {}", username);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch user {}: {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches all leagues for a user in a specific season.
     */
    public List<LeagueDto> getLeaguesForUser(String userId, String forSeason) {
        String url = baseUrl + "/user/" + userId + "/leagues/" + sport + "/" + forSeason;
        log.info("Fetching leagues for user {} season {}", userId, forSeason);
        try {
            ResponseEntity<List<LeagueDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<LeagueDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch leagues for user {} season {}: {}", userId, forSeason, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches all leagues for a user, merged across the configured season AND
     * the prior season.
     *
     * Why: Sleeper scopes leagues to a single season - a dynasty league gets
     * a brand new league_id each year once the commissioner rolls it over.
     * Right after a season transition, some of a user's leagues may have
     * already rolled over to the new season while others haven't yet, so
     * querying only one season can make leagues silently "disappear" from
     * results. Merging both (deduped by league_id) avoids that.
     */
    public List<LeagueDto> getLeaguesForUser(String userId) {
        int currentSeason;
        try {
            currentSeason = Integer.parseInt(season);
        } catch (NumberFormatException e) {
            currentSeason = java.time.Year.now().getValue();
        }

        Map<String, LeagueDto> merged = new LinkedHashMap<>();
        for (int s = currentSeason; s >= currentSeason - 1; s--) {
            for (LeagueDto league : getLeaguesForUser(userId, String.valueOf(s))) {
                if (league.getLeagueId() != null) {
                    merged.putIfAbsent(league.getLeagueId(), league);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    // -------------------------------------------------------------------------
    // Drafts
    // -------------------------------------------------------------------------

    /**
     * Fetches draft metadata (status, settings, draft order, slot mapping).
     */
    public DraftDto getDraft(String draftId) {
        String url = baseUrl + "/draft/" + draftId;
        log.info("Fetching draft {}", draftId);
        try {
            return restTemplate.getForObject(url, DraftDto.class);
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch draft {}: {}", draftId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches all picks made so far in a draft. Safe to poll repeatedly
     * while a draft is live - Sleeper returns the full, up-to-date pick list.
     */
    public List<DraftPickDto> getDraftPicks(String draftId) {
        String url = baseUrl + "/draft/" + draftId + "/picks";
        log.info("Fetching picks for draft {}", draftId);
        try {
            ResponseEntity<List<DraftPickDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<DraftPickDto>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch draft picks for draft {}: {}", draftId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    /**
     * Fetches season-total stats for all NBA players from Sleeper.
     * Returns a map of player_id -> stats node containing raw season totals
     * (pts, reb, ast, stl, blk, to, fg3m, gp, etc.).
     *
     * Endpoint: GET /stats/nba/regular/{season}
     * Note: stats are SEASON TOTALS, not per-game. Divide by gp for averages.
     */
    public Map<String, JsonNode> getSeasonStats(String seasonYear) {
        String url = baseUrl + "/stats/" + sport + "/regular/" + seasonYear;
        log.info("Fetching {} season stats for {}", sport.toUpperCase(), seasonYear);
        try {
            ResponseEntity<Map<String, JsonNode>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, JsonNode>>() {}
            );
            return response.getBody() != null ? response.getBody() : Collections.emptyMap();
        } catch (HttpClientErrorException e) {
            log.error("Failed to fetch season stats for {}: {}", seasonYear, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
