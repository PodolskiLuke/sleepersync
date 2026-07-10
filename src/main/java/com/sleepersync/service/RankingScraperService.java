package com.sleepersync.service;

import com.sleepersync.model.dto.DynastyRankingEntry;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RankingScraperService {

    private static final Logger log = LoggerFactory.getLogger(RankingScraperService.class);

    private static final Pattern RANK_PREFIX = Pattern.compile("^(\\d{1,3})(?:\\.\\d+)?[.)\\-\\s]+(.+)$");
    private static final Pattern NAME_POS_TEAM_INLINE = Pattern.compile("^([A-Za-z'\\-\\. ]{3,}?)\\s+(PG|SG|SF|PF|C|G|F|PG,SG|SG,SF|SF,PF|PF,C|PG,SG,SF|SG,SF,PF|SF,PF,C)\\s+([A-Z]{2,3})\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_POS_TEAM = Pattern.compile("^([A-Za-z'\\-\\. ]+?)\\s*(?:\\(|-)?\\s*(PG|SG|SF|PF|C|G|F|UTIL)?\\s*(?:[,/\\- ]+([A-Z]{2,3}))?.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPOTRAC_DRAFT_ROW = Pattern.compile("^\\d+\\s+(\\d+)\\s+([A-Z]{2,3})\\s+([A-Z]{2,3})\\s+([A-Za-z'\\-\\. ]+?)\\s+(PG|SG|SF|PF|C|G|F)\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESPN_DISPLAY_NAME = Pattern.compile("\"displayName\":\"([^\"]+)\"");
    private static final Pattern ESPN_OVERALL = Pattern.compile("\"overall\":(\\d+)");
    private static final Pattern ESPN_POSITION_ID = Pattern.compile("\"position\":\\{\"id\":\"?(\\d+)\"?\\}");
    private static final Pattern ESPN_SELECTION_MADE = Pattern.compile("\"status\":\"SELECTION_MADE\"");

    @Value("${rankings.scraper.enabled:true}")
    private boolean enabled;

    @Value("${rankings.scraper.next-season-url:}")
    private String nextSeasonUrl;

    @Value("${rankings.scraper.dynasty-url:}")
    private String dynastyUrl;

    @Value("${rankings.scraper.rookie-url:}")
    private String rookieUrl;

    @Value("${rankings.scraper.rookie-url-alt:}")
    private String rookieUrlAlt;

    @Value("${rankings.scraper.rookie-url-alt2:}")
    private String rookieUrlAlt2;

    @Value("${rankings.scraper.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${rankings.scraper.rookie-class-year:2026}")
    private int rookieClassYear;

    public List<DynastyRankingEntry> scrapeAllConfigured() {
        if (!enabled) {
            return List.of();
        }

        List<DynastyRankingEntry> all = new ArrayList<>();
        if (nextSeasonUrl != null && !nextSeasonUrl.isBlank()) {
            all.addAll(scrapeUrl("next-season", nextSeasonUrl));
        }
        if (dynastyUrl != null && !dynastyUrl.isBlank()) {
            all.addAll(scrapeUrl("dynasty", dynastyUrl));
        }
        if (rookieUrl != null && !rookieUrl.isBlank()) {
            all.addAll(scrapeUrl("rookie", rookieUrl));
        }
        if (rookieUrlAlt != null && !rookieUrlAlt.isBlank()) {
            all.addAll(scrapeUrl("rookie", rookieUrlAlt));
        }
        if (rookieUrlAlt2 != null && !rookieUrlAlt2.isBlank()) {
            all.addAll(scrapeUrl("rookie", rookieUrlAlt2));
        }
        return all;
    }

    public List<DynastyRankingEntry> scrapeUrl(String source, String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(timeoutMs)
                    .maxBodySize(0) // unlimited: some pages (e.g. ESPN) embed large JSON payloads past the 2MB default
                .execute();
            String rawHtml = response.body();
            Document doc = response.parse();

            if ("rookie".equalsIgnoreCase(source) && rookieClassYear > 0) {
                Optional<Integer> pageYear = extractYear(doc.title());
                if (pageYear.isPresent() && pageYear.get() != rookieClassYear) {
                    log.warn("Rookie source {} title year {} differs from configured class {}; scraping anyway and matching only against Sleeper rookie pool",
                            url, pageYear.get(), rookieClassYear);
                }
            }

            List<DynastyRankingEntry> entries = parseRankingDocument(source, doc, rawHtml);
            log.info("Scraped {} ranking rows from {}", entries.size(), url);
            return entries;
        } catch (IOException e) {
            log.warn("Failed to scrape {} from {}: {}", source, url, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("Unexpected scrape failure for {} from {}: {}", source, url, e.getMessage());
            return List.of();
        }
    }

    private List<DynastyRankingEntry> parseRankingDocument(String source, Document doc, String rawHtml) {
        List<DynastyRankingEntry> entries = new ArrayList<>();

        // Source-specific parser for Tankathon big board rookie rows
        if ("rookie".equalsIgnoreCase(source)) {
            List<DynastyRankingEntry> espn = parseEspnRookieRows(source, doc, rawHtml);
            if (!espn.isEmpty()) {
                return espn;
            }

            List<DynastyRankingEntry> spotrac = parseSpotracRookieRows(source, doc);
            if (!spotrac.isEmpty()) {
                return spotrac;
            }

            List<DynastyRankingEntry> tankathon = parseTankathonRookieRows(source, doc);
            if (!tankathon.isEmpty()) {
                return tankathon;
            }
        }

        // Generic strategy: parse ordered list items first
        for (Element li : doc.select("ol li")) {
            String text = li.text().trim();
            DynastyRankingEntry parsed = parseTextRow(source, text, entries.size() + 1);
            if (parsed != null) {
                entries.add(parsed);
            }
        }

        // Fallback strategy: parse table rows that look ranked
        if (entries.size() < 10) {
            for (Element tr : doc.select("table tr")) {
                String text = tr.text().trim();
                DynastyRankingEntry parsed = parseTextRow(source, text, entries.size() + 1);
                if (parsed != null) {
                    entries.add(parsed);
                }
            }
        }

        // Structured fallback for sites where rows are multi-column (FantasyPros/Tankathon-like)
        if (entries.size() < 10) {
            List<DynastyRankingEntry> structured = parseStructuredTableRows(source, doc);
            entries.addAll(structured);
        }

        return entries;
    }

    private List<DynastyRankingEntry> parseTankathonRookieRows(String source, Document doc) {
        List<DynastyRankingEntry> out = new ArrayList<>();

        for (Element row : doc.select(".mock-row")) {
            String rankText = row.select(".mock-row-pick-number").text().trim();
            Integer rank = tryParseInt(rankText);
            if (rank == null) {
                continue;
            }

            String name = row.select(".mock-row-name").text().trim();
            if (name.isBlank()) {
                continue;
            }

            String schoolPos = row.select(".mock-row-school-position").text().trim();
            String position = null;
            if (!schoolPos.isBlank()) {
                String[] split = schoolPos.split("\\|");
                if (split.length > 0) {
                    position = normalizePosition(split[0].trim().replace('/', ','));
                }
            }

            out.add(DynastyRankingEntry.builder()
                    .source(source)
                    .playerName(name)
                    .position(position)
                    .team(null)
                    .rank(rank)
                    .rawText(row.text())
                    .build());
        }

        return out;
    }

    private List<DynastyRankingEntry> parseEspnRookieRows(String source, Document doc, String rawHtml) {
        String location = doc.location() != null ? doc.location().toLowerCase() : "";
        String body = rawHtml != null ? rawHtml.toLowerCase() : "";
        if (!location.contains("espn.com") && !body.contains("espn.com")) {
            return List.of();
        }

        String payload = rawHtml != null && !rawHtml.isBlank() ? rawHtml : extractScriptPayload(doc);
        if (payload.isBlank()) {
            payload = doc.outerHtml();
        }

        Matcher matcher = ESPN_DISPLAY_NAME.matcher(payload);
        if (!matcher.find()) {
            return List.of();
        }
        Map<String, DynastyRankingEntry> bestByName = new LinkedHashMap<>();

        do {
            String playerName = sanitizePlayerName(matcher.group(1));
            int start = matcher.start();
            int end = Math.min(payload.length(), start + 4000);
            String window = payload.substring(start, end);

            if (!ESPN_SELECTION_MADE.matcher(window).find()) {
                continue;
            }

            Matcher overallMatcher = ESPN_OVERALL.matcher(window);
            Integer rank = overallMatcher.find() ? tryParseInt(overallMatcher.group(1)) : null;

            Matcher posMatcher = ESPN_POSITION_ID.matcher(window);
            String position = posMatcher.find() ? mapEspnPositionId(posMatcher.group(1)) : null;

            if (playerName.isBlank() || rank == null) {
                continue;
            }

            DynastyRankingEntry candidate = DynastyRankingEntry.builder()
                    .source(source)
                    .playerName(playerName)
                    .position(position)
                    .team(null)
                    .rank(rank)
                    .rawText(playerName + " overall " + rank)
                    .build();

            DynastyRankingEntry existing = bestByName.get(playerName.toLowerCase());
            if (existing == null || candidate.getRank() < existing.getRank()) {
                bestByName.put(playerName.toLowerCase(), candidate);
            }
        } while (matcher.find());

        return new ArrayList<>(bestByName.values());
    }

    private String extractScriptPayload(Document doc) {
        if (doc == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Element script : doc.select("script")) {
            String data = script.data();
            if (data != null && !data.isBlank()) {
                builder.append(data).append('\n');
            }
        }
        return builder.toString();
    }

    private String mapEspnPositionId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return switch (id.trim()) {
            case "1" -> "PG";
            case "2" -> "SG";
            case "3" -> "SF";
            case "4" -> "PF";
            case "5" -> "C";
            default -> null;
        };
    }

    private List<DynastyRankingEntry> parseSpotracRookieRows(String source, Document doc) {
        String location = doc.location() != null ? doc.location().toLowerCase() : "";
        if (!location.contains("spotrac.com") || !location.contains("/nba/draft")) {
            return List.of();
        }

        List<DynastyRankingEntry> out = new ArrayList<>();
        for (Element tr : doc.select("table tr")) {
            String rowText = tr.text().trim().replaceAll("\\s+", " ");
            Matcher m = SPOTRAC_DRAFT_ROW.matcher(rowText);
            if (!m.find()) {
                continue;
            }

            Integer rank = tryParseInt(m.group(1));
            String team = m.group(3) != null ? m.group(3).toUpperCase() : null;
            String playerName = sanitizePlayerName(m.group(4));
            String position = normalizePosition(m.group(5));

            if (playerName.isBlank() || playerName.length() < 3) {
                continue;
            }

            out.add(DynastyRankingEntry.builder()
                    .source(source)
                    .playerName(playerName)
                    .position(position)
                    .team(team.isBlank() ? null : team)
                    .rank(rank)
                    .rawText(rowText)
                    .build());
        }

        return out;
    }

    private List<DynastyRankingEntry> parseStructuredTableRows(String source, Document doc) {
        List<DynastyRankingEntry> out = new ArrayList<>();

        for (Element tr : doc.select("table tr")) {
            List<Element> tds = tr.select("td");
            if (tds.size() < 2) {
                continue;
            }

            String rankText = tds.get(0).text().trim();
            Integer rank = tryParseInt(rankText);
            if (rank == null) {
                continue;
            }

            // Find best candidate text cell for player name.
            String playerName = null;
            String position = null;
            String team = null;

            for (Element td : tds) {
                String txt = td.text().trim();
                if (txt.isBlank()) continue;

                // Try direct inline parse "Name POS TEAM ..."
                Matcher inline = NAME_POS_TEAM_INLINE.matcher(txt);
                if (inline.find()) {
                    playerName = sanitizePlayerName(inline.group(1));
                    position = normalizePosition(inline.group(2));
                    team = inline.group(3) != null ? inline.group(3).toUpperCase() : team;
                    break;
                }

                // Use first alpha-heavy cell as fallback name
                if (playerName == null && txt.matches(".*[A-Za-z].*[A-Za-z].*")) {
                    String candidate = sanitizePlayerName(sanitizeNameChunk(txt));
                    if (candidate.length() >= 3 && !candidate.matches("^[A-Z]{1,4}$")) {
                        playerName = candidate;
                    }
                }
            }

            if (playerName == null || playerName.isBlank()) {
                continue;
            }

            out.add(DynastyRankingEntry.builder()
                    .source(source)
                    .playerName(playerName)
                    .position(position)
                    .team(team)
                    .rank(rank)
                    .rawText(tr.text().trim())
                    .build());
        }

        return out;
    }

    private Integer tryParseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(raw);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Optional<Integer> extractYear(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher m = Pattern.compile("\\b(20\\d{2})\\b").matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private DynastyRankingEntry parseTextRow(String source, String text, int fallbackRank) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Integer rank = null;
        String nameChunk = text;

        Matcher rankMatcher = RANK_PREFIX.matcher(text);
        if (rankMatcher.find()) {
            rank = Integer.parseInt(rankMatcher.group(1));
            nameChunk = rankMatcher.group(2).trim();
        } else if (text.length() > 2 && Character.isDigit(text.charAt(0))) {
            return null;
        }

        String cleaned = sanitizeNameChunk(nameChunk);
        if (cleaned.isBlank()) {
            return null;
        }

        Matcher inline = NAME_POS_TEAM_INLINE.matcher(cleaned);
        if (inline.find()) {
            String extractedName = inline.group(1) != null ? inline.group(1).trim() : cleaned;
            String extractedPos = normalizePosition(inline.group(2));
            String extractedTeam = inline.group(3) != null ? inline.group(3).toUpperCase() : null;

            return DynastyRankingEntry.builder()
                .source(source)
                .playerName(extractedName)
                .position(extractedPos)
                .team(extractedTeam)
                .rank(rank != null ? rank : fallbackRank)
                .rawText(text)
                .build();
        }

        Matcher npt = PLAYER_POS_TEAM.matcher(cleaned);
        String playerName = cleaned;
        String position = null;
        String team = null;

        if (npt.find()) {
            playerName = npt.group(1) != null ? npt.group(1).trim() : cleaned;
            position = normalizePosition(npt.group(2));
            team = npt.group(3) != null ? npt.group(3).toUpperCase() : null;
        }

        playerName = sanitizePlayerName(playerName);
        if (playerName.isBlank() || playerName.length() < 3) {
            return null;
        }

        return DynastyRankingEntry.builder()
                .source(source)
                .playerName(playerName)
                .position(position)
                .team(team)
                .rank(rank != null ? rank : fallbackRank)
                .rawText(text)
                .build();
    }

    private String sanitizeNameChunk(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("\\s+", " ").trim();
        // Remove one or more leading numeric tokens like "2.4" or "3" from table rows.
        cleaned = cleaned.replaceFirst("^(?:\\d+(?:\\.\\d+)?\\s+)+", "").trim();
        return cleaned;
    }

    private String sanitizePlayerName(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        // Stop at first obvious stat-like numeric segment if parser didn't already split.
        cleaned = cleaned.replaceFirst("\\s+\\d+(?:\\.\\d+)?\\s.*$", "").trim();
        return cleaned;
    }

    private String normalizePosition(String pos) {
        if (pos == null) return null;
        return pos.toUpperCase().replace(" ", "");
    }
}
