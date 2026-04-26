package UHack.Platform.service;

import UHack.Platform.domain.AnalysisReport;
import UHack.Platform.domain.Match;
import UHack.Platform.domain.MatchEvent;
import UHack.Platform.repository.MatchEventRepository;
import UHack.Platform.repository.MatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class PostMatchPipelineService {

    private static final Logger log = LoggerFactory.getLogger(PostMatchPipelineService.class);

    private final MatchService matchService;
    private final MatchRepository matches;
    private final MatchEventRepository events;
    private final AnalysisService analyses;
    private final RagIndexingService ragIndexing;
    private final ObjectMapper mapper;
    private final RestTemplate http;
    private final String dqUrl;
    private final String tiUrl;
    private final String fusionUrl;

    public PostMatchPipelineService(MatchService matchService,
                                    MatchRepository matches,
                                    MatchEventRepository events,
                                    AnalysisService analyses,
                                    RagIndexingService ragIndexing,
                                    ObjectMapper mapper,
                                    RestTemplateBuilder builder,
                                    @Value("${ai-decision-quality.url}") String dqUrl,
                                    @Value("${ai-tactical-intelligence.url}") String tiUrl,
                                    @Value("${tactical-fusion.url}") String fusionUrl) {
        this.matchService = matchService;
        this.matches = matches;
        this.events = events;
        this.analyses = analyses;
        this.ragIndexing = ragIndexing;
        this.mapper = mapper;
        this.dqUrl = dqUrl;
        this.tiUrl = tiUrl;
        this.fusionUrl = fusionUrl;
        this.http = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(180))
                .build();
    }

    public ObjectNode runUploadedPipeline(JsonNode eventsPayload,
                                          JsonNode playersStats,
                                          Long requestedMatchId,
                                          String homeTeamName,
                                          String awayTeamName,
                                          Integer homeScore,
                                          Integer awayScore,
                                          String focusTeamName) {
        Match match = persistUploadedEvents(eventsPayload, requestedMatchId, homeTeamName, awayTeamName, homeScore, awayScore);
        JsonNode decisionQuality = runDecisionQuality(match, eventsPayload, focusTeamName);
        analyses.upsert(match.getId(), AnalysisReport.Type.DECISION_QUALITY, decisionQuality);

        JsonNode tacticalIntelligence = runTacticalIntelligence(
                match,
                playersStats,
                homeTeamName,
                awayTeamName,
                homeScore,
                awayScore,
                focusTeamName
        );
        analyses.upsert(match.getId(), AnalysisReport.Type.TACTICAL_INTELLIGENCE, tacticalIntelligence);

        JsonNode fusion = runFusion(decisionQuality, tacticalIntelligence);
        analyses.upsert(match.getId(), AnalysisReport.Type.TACTICAL_FUSION, fusion);
        matchService.markAnalysed(match.getWyId());
        ragIndexing.indexMatchAsync(match);

        ObjectNode response = mapper.createObjectNode();
        response.put("matchId", match.getWyId());
        response.put("status", "analysed");
        ObjectNode reports = response.putObject("reports");
        reports.set("decisionQuality", decisionQuality);
        reports.set("tacticalIntelligence", tacticalIntelligence);
        reports.set("tacticalFusion", fusion);
        return response;
    }

    public void tryRunFusionAndRag(Match match) {
        Optional<AnalysisReport> dq = analyses.get(match.getId(), AnalysisReport.Type.DECISION_QUALITY);
        Optional<AnalysisReport> ti = analyses.get(match.getId(), AnalysisReport.Type.TACTICAL_INTELLIGENCE);
        if (dq.isEmpty() || ti.isEmpty()) {
            return;
        }

        try {
            JsonNode fusion = runFusion(dq.get().getPayload(), ti.get().getPayload());
            analyses.upsert(match.getId(), AnalysisReport.Type.TACTICAL_FUSION, fusion);
            matchService.markAnalysed(match.getWyId());
            ragIndexing.indexMatchAsync(match);
        } catch (Exception e) {
            log.warn("fusion/RAG pipeline failed for match {}: {}", match.getWyId(), e.getMessage());
        }
    }

    public JsonNode runDecisionQuality(Match match, JsonNode eventsPayload, String focusTeamName) {
        ObjectNode request = mapper.createObjectNode();
        request.set("match_data", normalizeMatchData(match, eventsPayload));
        request.put("match_id", match.getWyId());
        if (focusTeamName != null && !focusTeamName.isBlank()) {
            request.put("team_name", focusTeamName);
        }
        return postJson(dqUrl + "/api/v1/matches/analyze", request);
    }

    public JsonNode runTacticalIntelligence(Match match,
                                            JsonNode playersStats,
                                            String homeTeamName,
                                            String awayTeamName,
                                            Integer homeScore,
                                            Integer awayScore,
                                            String focusTeamName) {
        ObjectNode request = mapper.createObjectNode();
        request.set("players_stats", playersStats);
        request.put("home_team_name", blankToDefault(homeTeamName, "Home"));
        request.put("away_team_name", blankToDefault(awayTeamName, "Away"));
        request.put("match_id", match.getWyId());
        if (homeScore != null) request.put("home_score", homeScore);
        if (awayScore != null) request.put("away_score", awayScore);
        if (focusTeamName != null && !focusTeamName.isBlank()) {
            request.put("focus_team_name", focusTeamName);
        }
        return postJson(tiUrl + "/api/insights/detailed/from-players-stats", request);
    }

    public JsonNode runFusion(JsonNode decisionQuality, JsonNode tacticalIntelligence) {
        ObjectNode request = mapper.createObjectNode();
        request.set("input1", tacticalIntelligence);
        request.set("input2", decisionQuality);
        return postJson(fusionUrl + "/fusion/analysis/json", request);
    }

    @Transactional
    public Match persistUploadedEvents(JsonNode eventsPayload,
                                       Long requestedMatchId,
                                       String homeTeamName,
                                       String awayTeamName,
                                       Integer homeScore,
                                       Integer awayScore) {
        Long wyId = requestedMatchId != null ? requestedMatchId : extractMatchId(eventsPayload);
        if (wyId == null) {
            throw new IllegalArgumentException("Could not infer matchId from events file. Provide matchId form field.");
        }
        Match match = matchService.findOrCreate(wyId);
        match.setStatus(Match.Status.FINISHED);
        match.setLabel(resolveLabel(eventsPayload, homeTeamName, awayTeamName));
        match.setScoreHome(homeScore);
        match.setScoreAway(awayScore);
        matches.save(match);

        events.deleteByMatchId(match.getId());
        for (JsonNode event : extractEvents(eventsPayload)) {
            MatchEvent row = new MatchEvent();
            row.setMatchId(match.getId());
            if (event.has("id") && !event.get("id").isNull()) row.setWyEventId(event.get("id").asLong());
            if (event.has("minute")) row.setMinute(event.get("minute").asInt());
            if (event.has("second")) row.setSecond(event.get("second").asInt());
            if (event.has("matchPeriod")) row.setPeriod(event.get("matchPeriod").asText());
            String primary = event.path("type").path("primary").asText("");
            row.setPrimaryType(primary.isBlank() ? null : primary);
            row.setPayload(event);
            events.save(row);
        }
        return match;
    }

    private JsonNode postJson(String url, JsonNode payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForObject(url, new HttpEntity<>(payload, headers), JsonNode.class);
    }

    private JsonNode normalizeMatchData(Match match, JsonNode eventsPayload) {
        if (eventsPayload != null && eventsPayload.isObject()) {
            return eventsPayload;
        }
        ObjectNode root = mapper.createObjectNode();
        ObjectNode matchNode = root.putObject("match");
        matchNode.put("wyId", match.getWyId());
        matchNode.put("label", match.getLabel());
        root.set("events", extractEvents(eventsPayload));
        return root;
    }

    private ArrayNode extractEvents(JsonNode payload) {
        if (payload == null || payload.isNull()) return mapper.createArrayNode();
        if (payload.isArray()) return (ArrayNode) payload;
        JsonNode eventsNode = payload.path("events");
        if (eventsNode.isArray()) return (ArrayNode) eventsNode;
        JsonNode elements = payload.path("elements");
        if (elements.isArray() && !elements.isEmpty() && elements.get(0).path("events").isArray()) {
            return (ArrayNode) elements.get(0).path("events");
        }
        return mapper.createArrayNode();
    }

    private Long extractMatchId(JsonNode payload) {
        JsonNode match = payload.path("match");
        if (match.has("wyId")) return match.get("wyId").asLong();
        if (match.has("id")) return match.get("id").asLong();
        if (payload.has("match_id")) return payload.get("match_id").asLong();
        if (payload.has("matchId")) return payload.get("matchId").asLong();
        JsonNode elements = payload.path("elements");
        if (elements.isArray() && !elements.isEmpty()) {
            Long fromElement = extractMatchId(elements.get(0));
            if (fromElement != null) return fromElement;
        }
        List<JsonNode> firstEvents = extractEvents(payload).findValues("matchId");
        if (!firstEvents.isEmpty() && firstEvents.get(0).canConvertToLong()) {
            return firstEvents.get(0).asLong();
        }
        return null;
    }

    private String resolveLabel(JsonNode payload, String homeTeamName, String awayTeamName) {
        String label = payload.path("match").path("label").asText(null);
        if (label != null && !label.isBlank()) return label;
        if (homeTeamName != null && awayTeamName != null && !homeTeamName.isBlank() && !awayTeamName.isBlank()) {
            return homeTeamName + " - " + awayTeamName;
        }
        return "Uploaded match";
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
