package UHack.Platform.consumer;

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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Subscribes to a synthetic {@code match-lifecycle} Kafka topic for events of
 * the form {@code {"event": "match_end", "match_id": ...}}.
 *
 * On match_end:
 *   1. Read the full match_event log from DB
 *   2. POST it to ai-decision-quality and ai-tactical-intelligence
 *   3. Those services do their own work and publish to analysis-* topics
 *      which the AnalysisReportConsumer above picks up
 */
@Component
public class MatchLifecycleConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchLifecycleConsumer.class);

    private final MatchRepository matches;
    private final MatchEventRepository events;
    private final ObjectMapper mapper;
    private final RestTemplate http;
    private final String dqUrl;
    private final String tiUrl;

    public MatchLifecycleConsumer(MatchRepository matches,
                                  MatchEventRepository events,
                                  ObjectMapper mapper,
                                  RestTemplateBuilder builder,
                                  @Value("${ai-decision-quality.url}") String dqUrl,
                                  @Value("${ai-tactical-intelligence.url}") String tiUrl) {
        this.matches = matches;
        this.events = events;
        this.mapper = mapper;
        this.http = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        this.dqUrl = dqUrl;
        this.tiUrl = tiUrl;
    }

    @KafkaListener(
            topics = "${matches.topic.lifecycle}",
            groupId = "platform-lifecycle",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onLifecycle(String raw) {
        JsonNode body;
        try {
            body = mapper.readTree(raw);
        } catch (Exception e) {
            log.warn("Bad lifecycle JSON: {}", e.getMessage());
            return;
        }
        if (!"match_end".equals(body.path("event").asText())) {
            return;
        }
        Long wyId = body.has("match_id") ? body.get("match_id").asLong() : null;
        if (wyId == null) {
            log.warn("match_end without match_id");
            return;
        }
        Match match = matches.findByWyId(wyId).orElse(null);
        if (match == null) {
            log.warn("match_end for unknown match {}", wyId);
            return;
        }

        ObjectNode payload = buildFullMatchPayload(match);

        // Fire-and-forget POST to both downstream services. They'll publish
        // to Kafka analysis-* topics on completion, picked up by AnalysisReportConsumer.
        dispatch(dqUrl  + "/api/v1/matches/analyze",            payload, "decision-quality");
        dispatch(tiUrl  + "/api/insights/from-players-stats",   payload, "tactical-intelligence");
    }

    private ObjectNode buildFullMatchPayload(Match match) {
        ObjectNode root = mapper.createObjectNode();
        root.put("match_id", match.getWyId());
        root.put("label", match.getLabel());
        root.put("home_team_id", match.getHomeTeamId());
        root.put("away_team_id", match.getAwayTeamId());

        List<MatchEvent> all = events.findByMatchIdOrderByMinuteAscSecondAscIdAsc(match.getId());
        ArrayNode arr = mapper.createArrayNode();
        for (MatchEvent e : all) {
            if (e.getPayload() != null) arr.add(e.getPayload());
        }
        root.set("events", arr);
        return root;
    }

    private void dispatch(String url, ObjectNode payload, String label) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ObjectNode> req = new HttpEntity<>(payload, headers);
            http.postForEntity(url, req, String.class);
            log.info("dispatched full match → {} ({})", label, url);
        } catch (Exception e) {
            log.warn("Dispatch to {} failed: {}", label, e.getMessage());
        }
    }
}
