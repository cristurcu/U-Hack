package UHack.Platform.service;

import UHack.Platform.domain.AnalysisReport;
import UHack.Platform.domain.Match;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RagIndexingService {

    private static final Logger log = LoggerFactory.getLogger(RagIndexingService.class);

    private final AnalysisService analyses;
    private final InsightService insights;
    private final ObjectMapper mapper;
    private final RestTemplate http;
    private final String ragUrl;
    private final boolean enabled;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rag-indexer");
        t.setDaemon(true);
        return t;
    });

    public RagIndexingService(AnalysisService analyses,
                              InsightService insights,
                              ObjectMapper mapper,
                              RestTemplateBuilder builder,
                              @Value("${rag-service.url:}") String ragUrl,
                              @Value("${rag-service.enabled:true}") boolean enabled) {
        this.analyses = analyses;
        this.insights = insights;
        this.mapper = mapper;
        this.ragUrl = ragUrl == null ? "" : ragUrl.strip();
        this.enabled = enabled;
        this.http = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }

    public void indexMatchAsync(Match match) {
        if (!enabled || ragUrl.isBlank()) {
            return;
        }
        executor.submit(() -> indexMatch(match));
    }

    private void indexMatch(Match match) {
        try {
            ObjectNode payload = buildPayload(match);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            http.postForEntity(ragUrl + "/index/match", new HttpEntity<>(payload, headers), String.class);
            log.info("indexed match {} in rag-service", match.getWyId());
        } catch (Exception e) {
            log.warn("RAG indexing failed for match {}: {}", match.getWyId(), e.getMessage());
        }
    }

    private ObjectNode buildPayload(Match match) {
        ObjectNode root = mapper.createObjectNode();
        root.put("matchId", match.getWyId());
        root.put("source", "platform-orchestrator");

        ObjectNode outputs = mapper.createObjectNode();
        addAnalysisOutputs(match, outputs);
        addLiveInsightOutputs(match, outputs);
        root.set("outputs", outputs);

        ObjectNode options = mapper.createObjectNode();
        options.put("vectorStore", "faiss");
        options.put("rebuild", true);
        options.put("topNPhases", 10);
        options.put("includeDebugDocuments", false);
        root.set("options", options);

        return root;
    }

    private void addAnalysisOutputs(Match match, ObjectNode outputs) {
        List<AnalysisReport> rows = analyses.listForMatch(match.getId());
        for (AnalysisReport row : rows) {
            if (row.getPayload() == null || row.getPayload().isMissingNode() || row.getPayload().isNull()) {
                continue;
            }
            if (row.getType() == AnalysisReport.Type.DECISION_QUALITY) {
                outputs.set("decisionQuality", row.getPayload());
            } else if (row.getType() == AnalysisReport.Type.TACTICAL_INTELLIGENCE) {
                outputs.set("tacticalIntelligence", row.getPayload());
            } else if (row.getType() == AnalysisReport.Type.TACTICAL_FUSION) {
                outputs.set("fusion", row.getPayload());
            }
        }
    }

    private void addLiveInsightOutputs(Match match, ObjectNode outputs) {
        ArrayNode passingNetwork = mapper.createArrayNode();
        ArrayNode pressing = mapper.createArrayNode();
        ArrayNode ballLosses = mapper.createArrayNode();
        ArrayNode lineBreaks = mapper.createArrayNode();
        ArrayNode playerProfiles = mapper.createArrayNode();

        for (InsightService.Snapshot snap : insights.listForMatch(match.getWyId())) {
            ObjectNode payload = enrichWithTeamId(snap.payload(), snap.teamId());
            switch (snap.type()) {
                case PASSING_NETWORK -> passingNetwork.add(payload);
                case PRESSING -> pressing.add(payload);
                case BALL_LOSSES -> ballLosses.add(payload);
                case LINE_BREAKS -> lineBreaks.add(payload);
                case PLAYER_PROFILE -> playerProfiles.add(payload);
            }
        }

        if (!passingNetwork.isEmpty()) outputs.set("passingNetwork", passingNetwork);
        if (!pressing.isEmpty()) outputs.set("pressing", pressing);
        if (!ballLosses.isEmpty()) outputs.set("ballLosses", ballLosses);
        if (!lineBreaks.isEmpty()) outputs.set("lineBreaks", lineBreaks);
        if (!playerProfiles.isEmpty()) outputs.set("playerProfiles", playerProfiles);
    }

    private ObjectNode enrichWithTeamId(JsonNode payload, Long teamId) {
        ObjectNode copy = payload != null && payload.isObject()
                ? ((ObjectNode) payload).deepCopy()
                : mapper.createObjectNode();
        if (teamId != null && !copy.has("team_id")) {
            copy.put("team_id", teamId);
        }
        return copy;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
