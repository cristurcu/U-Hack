package UHack.Platform.dashboard;

import UHack.Platform.dashboard.client.AiServiceClient;
import UHack.Platform.dashboard.client.StatsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SseHub hub;
    private final StatsClient stats;
    private final AiServiceClient ai;

    public DashboardController(SseHub hub, StatsClient stats, AiServiceClient ai) {
        this.hub = hub;
        this.stats = stats;
        this.ai = ai;
    }

    @GetMapping(value = "/{matchId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable long matchId) {
        return hub.register(matchId);
    }

    @GetMapping("/{matchId}")
    public ObjectNode snapshot(@PathVariable long matchId) {
        ObjectNode out = MAPPER.createObjectNode();
        out.put("matchId", matchId);

        CompletableFuture<JsonNode> summaryF = CompletableFuture.supplyAsync(() -> stats.summary(matchId));

        JsonNode summary = summaryF.join();
        out.set("summary", summary != null ? summary : MAPPER.nullNode());

        Long homeId = null, awayId = null;
        if (summary != null && summary.has("metadata")) {
            JsonNode md = summary.get("metadata");
            if (md.has("homeTeam") && md.get("homeTeam").has("id"))
                homeId = md.get("homeTeam").get("id").asLong();
            if (md.has("awayTeam") && md.get("awayTeam").has("id"))
                awayId = md.get("awayTeam").get("id").asLong();
        }

        ObjectNode passingNode = MAPPER.createObjectNode();
        ObjectNode pressingNode = MAPPER.createObjectNode();

        if (homeId != null && awayId != null) {
            final long fHome = homeId, fAway = awayId;
            CompletableFuture<JsonNode> p1 = CompletableFuture.supplyAsync(() -> ai.passingNetwork(matchId, fHome));
            CompletableFuture<JsonNode> p2 = CompletableFuture.supplyAsync(() -> ai.passingNetwork(matchId, fAway));
            CompletableFuture<JsonNode> r1 = CompletableFuture.supplyAsync(() -> ai.pressing(matchId, fHome));
            CompletableFuture<JsonNode> r2 = CompletableFuture.supplyAsync(() -> ai.pressing(matchId, fAway));

            for (Map.Entry<Long, CompletableFuture<JsonNode>> e :
                    Map.of(fHome, p1, fAway, p2).entrySet()) {
                JsonNode v = e.getValue().join();
                if (v != null) passingNode.set(String.valueOf(e.getKey()), v);
            }
            for (Map.Entry<Long, CompletableFuture<JsonNode>> e :
                    Map.of(fHome, r1, fAway, r2).entrySet()) {
                JsonNode v = e.getValue().join();
                if (v != null) pressingNode.set(String.valueOf(e.getKey()), v);
            }
        }
        out.set("passingNetworks", passingNode);
        out.set("pressingStats", pressingNode);

        return out;
    }
}
