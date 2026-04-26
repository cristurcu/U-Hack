package UHack.Platform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches/{wyId}/rag")
@CrossOrigin(origins = "*")
public class RagController {

    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final String ragUrl;

    public RagController(RestTemplateBuilder builder,
                         ObjectMapper mapper,
                         @Value("${rag-service.url:}") String ragUrl) {
        this.http = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        this.mapper = mapper;
        this.ragUrl = ragUrl == null ? "" : ragUrl.strip();
    }

    @PostMapping("/query")
    public ResponseEntity<JsonNode> query(@PathVariable Long wyId, @RequestBody JsonNode body) {
        if (ragUrl.isBlank()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "rag-service.url is not configured");
            return ResponseEntity.status(503).body(error);
        }

        ObjectNode request = mapper.createObjectNode();
        request.put("sessionId", body.path("sessionId").asText(UUID.randomUUID().toString()));
        request.put("question", body.path("question").asText(""));
        request.put("matchId", wyId);
        request.put("clubKey", body.path("clubKey").asText("u_cluj"));
        request.put("includeClubKnowledge", body.path("includeClubKnowledge").asBoolean(false));
        request.put("topK", body.path("topK").asInt(6));
        if (body.has("teamId") && !body.get("teamId").isNull()) {
            request.put("teamId", body.get("teamId").asLong());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = http.postForEntity(
                ragUrl + "/rag/query",
                new HttpEntity<>(request, headers),
                JsonNode.class
        );
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}
