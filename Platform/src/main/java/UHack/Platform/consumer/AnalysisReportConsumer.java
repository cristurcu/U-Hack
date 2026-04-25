package UHack.Platform.consumer;

import UHack.Platform.domain.AnalysisReport;
import UHack.Platform.service.AnalysisService;
import UHack.Platform.service.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code analysis-*} topics published by ai-decision-quality and
 * ai-tactical-intelligence after the match-end handoff. One report per
 * (match, type) — latest wins on retries.
 *
 * Expected message shape:
 *   {
 *     "match_id": 99042601,
 *     "type": "decision-quality" | "tactical-intelligence",
 *     "payload": { ... }
 *   }
 */
@Component
public class AnalysisReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisReportConsumer.class);

    private final AnalysisService analyses;
    private final MatchService matches;
    private final ObjectMapper mapper;

    public AnalysisReportConsumer(AnalysisService analyses, MatchService matches, ObjectMapper mapper) {
        this.analyses = analyses;
        this.matches = matches;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = {
                    "${analysis.topic.decision-quality}",
                    "${analysis.topic.tactical-intelligence}"
            },
            groupId = "platform-analysis-reports",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onReport(String raw) {
        JsonNode body;
        try {
            body = mapper.readTree(raw);
        } catch (Exception e) {
            log.warn("Bad analysis JSON: {}", e.getMessage());
            return;
        }

        Long wyId = body.has("match_id") ? body.get("match_id").asLong() : null;
        String type = body.path("type").asText(null);
        JsonNode payload = body.path("payload");

        if (wyId == null || type == null || payload.isMissingNode()) {
            log.warn("Analysis missing match_id/type/payload: {}", raw);
            return;
        }

        AnalysisReport.Type reportType = switch (type) {
            case "decision-quality"      -> AnalysisReport.Type.DECISION_QUALITY;
            case "tactical-intelligence" -> AnalysisReport.Type.TACTICAL_INTELLIGENCE;
            default -> null;
        };
        if (reportType == null) {
            log.warn("Unknown analysis type '{}'", type);
            return;
        }

        var match = matches.findOrCreate(wyId);
        analyses.upsert(match.getId(), reportType, payload);

        // If both reports are in, mark match as ANALYSED
        var stored = analyses.listForMatch(match.getId());
        if (stored.size() >= 2) {
            matches.markAnalysed(wyId);
        }
        log.info("stored {} report for match {}", reportType, wyId);
    }
}
