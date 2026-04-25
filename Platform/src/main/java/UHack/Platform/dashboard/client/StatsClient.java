package UHack.Platform.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class StatsClient {

    private static final Logger log = LoggerFactory.getLogger(StatsClient.class);

    private final RestTemplate rest;
    private final String base;

    public StatsClient(RestTemplateBuilder b, @Value("${dashboard.stats.url}") String base) {
        this.rest = b.connectTimeout(Duration.ofSeconds(2))
                     .readTimeout(Duration.ofSeconds(5))
                     .build();
        this.base = base.replaceAll("/$", "");
    }

    public JsonNode summary(long matchId) {
        try {
            return rest.getForObject(base + "/stats/" + matchId + "/summary", JsonNode.class);
        } catch (Exception e) {
            log.debug("stats summary unavailable for {}: {}", matchId, e.getMessage());
            return null;
        }
    }
}
