package UHack.Platform.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class WyscoutPollingService {

    private static final Logger log = LoggerFactory.getLogger(WyscoutPollingService.class);
    private static final String MATCH_END_PRIMARY = "match_end";

    private final WyscoutClient client;
    private final DedupService dedup;
    private final EventPublisher publisher;
    private final ObjectMapper mapper;

    /** Track which matches we've already published the synthetic match_end for. */
    private final Set<Long> finalizedMatches = new HashSet<>();

    public WyscoutPollingService(WyscoutClient client,
                                 DedupService dedup,
                                 EventPublisher publisher,
                                 ObjectMapper mapper) {
        this.client = client;
        this.dedup = dedup;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${wyscout.poll.interval-ms}")
    public void poll() {
        WyscoutClient.PollResult result;
        try {
            result = client.fetch();
        } catch (Exception e) {
            log.warn("Wyscout fetch failed: {}", e.getMessage());
            return;
        }

        int fetched = result.events().size();
        int published = 0;
        int skipped = 0;

        for (JsonNode event : result.events()) {
            JsonNode idNode = event.get("id");
            if (idNode == null || idNode.isNull()) continue;
            String eventId = idNode.asText();
            if (!dedup.markIfAbsent(eventId)) {
                skipped++;
                continue;
            }
            publisher.publish(eventId, event.toString());
            published++;
        }

        // Once mock signals match has finished AND we haven't already, emit a
        // synthetic match_end sentinel onto the same wyscout-events topic.
        // Downstream consumers (ai-service, Platform's MatchEventConsumer)
        // already know how to react to type.primary == "match_end".
        if (result.finished() && result.matchId() != null && finalizedMatches.add(result.matchId())) {
            publishMatchEnd(result.matchId());
        }

        if (fetched > 0) {
            log.info("Poll cycle: fetched={}, published={}, deduped={}, finished={}",
                    fetched, published, skipped, result.finished());
        }
    }

    private void publishMatchEnd(long matchId) {
        ObjectNode end = mapper.createObjectNode();
        end.put("id", -matchId); // synthetic id space — keep negative to avoid collisions
        end.put("matchId", matchId);
        end.put("matchPeriod", "FT");
        end.put("minute", 90);
        end.put("second", 0);
        ObjectNode type = end.putObject("type");
        type.put("primary", MATCH_END_PRIMARY);
        type.putArray("secondary");
        publisher.publish("match-end:" + matchId, end.toString());
        log.info("emitted synthetic match_end sentinel for match {}", matchId);
    }
}
