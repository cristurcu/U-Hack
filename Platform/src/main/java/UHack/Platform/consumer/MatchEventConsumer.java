package UHack.Platform.consumer;

import UHack.Platform.domain.Match;
import UHack.Platform.domain.MatchEvent;
import UHack.Platform.repository.MatchEventRepository;
import UHack.Platform.service.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscribes to {@code wyscout-events} (the same topic Platform itself
 * publishes to). Persists every event into match_event and lazily creates the
 * Match row. Handles the synthetic {@code match_end} sentinel by flipping the
 * Match status — actual DQ/TI dispatch lives in MatchLifecycleConsumer.
 */
@Component
public class MatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MatchEventConsumer.class);
    private static final String MATCH_END_PRIMARY = "match_end";

    private final MatchService matches;
    private final MatchEventRepository events;
    private final ObjectMapper mapper;

    public MatchEventConsumer(MatchService matches, MatchEventRepository events, ObjectMapper mapper) {
        this.matches = matches;
        this.events = events;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "${wyscout.kafka.topic}",
            groupId = "platform-event-store",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onEvent(String payload) {
        JsonNode event;
        try {
            event = mapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Bad event JSON: {}", e.getMessage());
            return;
        }

        Long matchId = extractMatchId(event);
        if (matchId == null) {
            return;
        }

        Match match = matches.findOrCreate(matchId);
        matches.applyEventMetadata(match, event);

        // Match-end sentinel — flip status. Lifecycle consumer dispatches DQ/TI.
        String primary = event.path("type").path("primary").asText("");
        if (MATCH_END_PRIMARY.equals(primary)) {
            matches.markFinished(matchId);
            log.info("match {} marked FINISHED via match_end sentinel", matchId);
            return;
        }

        Long wyEventId = event.has("id") ? event.get("id").asLong() : null;
        if (wyEventId != null && events.existsByWyEventId(wyEventId)) {
            return; // idempotent on retries
        }

        MatchEvent row = new MatchEvent();
        row.setMatchId(match.getId());
        row.setWyEventId(wyEventId);
        if (event.has("minute"))      row.setMinute(event.get("minute").asInt());
        if (event.has("second"))      row.setSecond(event.get("second").asInt());
        if (event.has("matchPeriod")) row.setPeriod(event.get("matchPeriod").asText());
        row.setPrimaryType(primary.isEmpty() ? null : primary);
        row.setPayload(event);
        events.save(row);
    }

    private Long extractMatchId(JsonNode event) {
        if (event.has("matchId") && !event.get("matchId").isNull()) return event.get("matchId").asLong();
        JsonNode m = event.path("match");
        if (m.has("wyId")) return m.get("wyId").asLong();
        return null;
    }
}
