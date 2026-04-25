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
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestTemplate rest;
    private final String base;

    public AiServiceClient(RestTemplateBuilder b, @Value("${dashboard.ai-service.url}") String base) {
        this.rest = b.connectTimeout(Duration.ofSeconds(2))
                     .readTimeout(Duration.ofSeconds(5))
                     .build();
        this.base = base.replaceAll("/$", "");
    }

    public JsonNode passingNetwork(long matchId, long teamId) {
        return get(base + "/insights/passing-network/" + matchId + "?team_id=" + teamId);
    }

    public JsonNode pressing(long matchId, long teamId) {
        return get(base + "/insights/pressing/" + matchId + "?team_id=" + teamId);
    }

    private JsonNode get(String url) {
        try {
            return rest.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.debug("ai-service GET failed [{}]: {}", url, e.getMessage());
            return null;
        }
    }
}
