package UHack.Platform.consumer;

import UHack.Platform.domain.LiveInsight;
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
 * payload is a full snapshot of one insight type for one match — we store
 * latest-wins per (matchId, type).
 *
 * Expected message shape:
 *   {
 *     "match_id": 99042601,
 *     "type": "passing-network" | "pressing" | "ball-losses" | "line-breaks" | "player-profile",
 *     "payload": { ... }   // the existing /insights/* response body, unchanged
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

        Long matchId = body.has("match_id") ? body.get("match_id").asLong() : null;
        Long teamId  = body.has("team_id")  ? body.get("team_id").asLong()  : null;
        String type = body.path("type").asText(null);
        JsonNode payload = body.path("payload");

        if (matchId == null || teamId == null || type == null || payload.isMissingNode()) {
            log.warn("Insight missing match_id/team_id/type/payload: {}", raw);
            return;
        }

        LiveInsight.Type liveType = mapType(type);
        if (liveType == null) {
            log.warn("Unknown insight type '{}'", type);
            return;
        }

        // Make sure we have a Match row keyed by wyId for this insight
        var match = matches.findOrCreate(matchId);
        insights.upsert(match.getId(), teamId, liveType, payload);
    }

    private LiveInsight.Type mapType(String wireType) {
        return switch (wireType) {
            case "passing-network" -> LiveInsight.Type.PASSING_NETWORK;
            case "pressing"        -> LiveInsight.Type.PRESSING;
            case "ball-losses"     -> LiveInsight.Type.BALL_LOSSES;
            case "line-breaks"     -> LiveInsight.Type.LINE_BREAKS;
            case "player-profile"  -> LiveInsight.Type.PLAYER_PROFILE;
            default -> null;
        };
    }
}
