package UHack.Platform.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class WyscoutClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;

    public WyscoutClient(RestTemplateBuilder builder,
                         @Value("${wyscout.api.url}") String apiUrl) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.apiUrl = apiUrl;
    }

    public List<JsonNode> fetchEvents() {
        return fetch().events;
    }

    /**
     * Fetches the full mock response so callers can look at meta.finished
     * (used by Platform's polling service to decide when to emit the
     * match_end sentinel).
     */
    public PollResult fetch() {
        JsonNode body = restTemplate.getForObject(apiUrl, JsonNode.class);
        if (body == null) {
            return new PollResult(Collections.emptyList(), false, null);
        }
        JsonNode events = body.isArray() ? body : body.path("events");
        List<JsonNode> list = events.isArray()
                ? StreamSupport.stream(events.spliterator(), false).toList()
                : Collections.emptyList();

        boolean finished = body.path("meta").path("finished").asBoolean(false);
        Long matchId = null;
        JsonNode m = body.path("match");
        if (m.has("wyId")) {
            matchId = m.get("wyId").asLong();
        } else {
            // Fall back to first event's matchId
            for (JsonNode e : list) {
                if (e.has("matchId") && !e.get("matchId").isNull()) {
                    matchId = e.get("matchId").asLong();
                    break;
                }
            }
        }
        return new PollResult(list, finished, matchId);
    }

    public record PollResult(List<JsonNode> events, boolean finished, Long matchId) {}
}
