package UHack.Platform.consumer;

import UHack.Platform.service.InsightService;
import UHack.Platform.service.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the {@code insights-*} Kafka topics produced by ai-service. Each
 * payload is a full snapshot of one insight type for one team — we cache it
 * in Redis (latest-wins per match × type × team), never in Postgres.
 *
 * Expected message shape:
 *   {
 *     "match_id": 99042601,
 *     "team_id":  9001,
 *     "type":     "passing-network" | "pressing" | "ball-losses" | "line-breaks" | "player-profile",
 *     "payload":  { ... }
 *   }
 */
@Component
public class LiveInsightConsumer {

    private static final Logger log = LoggerFactory.getLogger(LiveInsightConsumer.class);

    private final InsightService insights;
    private final MatchService matches;
    private final ObjectMapper mapper;

    public LiveInsightConsumer(InsightService insights, MatchService matches, ObjectMapper mapper) {
        this.insights = insights;
        this.matches = matches;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = {
                    "${insights.topic.passing-network}",
                    "${insights.topic.pressing}",
                    "${insights.topic.ball-losses}",
                    "${insights.topic.line-breaks}",
                    "${insights.topic.player-profile}"
            },
            groupId = "platform-live-insights",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInsight(String raw) {
        JsonNode body;
        try {
            body = mapper.readTree(raw);
        } catch (Exception e) {
            log.warn("Bad insight JSON: {}", e.getMessage());
            return;
        }

        Long wyId   = body.has("match_id") ? body.get("match_id").asLong() : null;
        Long teamId = body.has("team_id")  ? body.get("team_id").asLong()  : null;
        String type = body.path("type").asText(null);
        JsonNode payload = body.path("payload");

        if (wyId == null || teamId == null || type == null || payload.isMissingNode()) {
            log.warn("Insight missing match_id/team_id/type/payload: {}", raw);
            return;
        }

        InsightService.Type liveType = mapType(type);
        if (liveType == null) {
            log.warn("Unknown insight type '{}'", type);
            return;
        }

        // Touch the Match row so post-match queries / SSE can filter on wyId.
        matches.findOrCreate(wyId);
        // Cache the snapshot in Redis (TTL handled by the service).
        insights.upsert(wyId, teamId, liveType, payload);
    }

    private InsightService.Type mapType(String wireType) {
        return switch (wireType) {
            case "passing-network" -> InsightService.Type.PASSING_NETWORK;
            case "pressing"        -> InsightService.Type.PRESSING;
            case "ball-losses"     -> InsightService.Type.BALL_LOSSES;
            case "line-breaks"     -> InsightService.Type.LINE_BREAKS;
            case "player-profile"  -> InsightService.Type.PLAYER_PROFILE;
            default -> null;
        };
    }
}
