package UHack.Platform.controller;

import UHack.Platform.domain.LiveInsight;
import UHack.Platform.service.InsightService;
import UHack.Platform.service.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only API for live insights. Frontend polls this; values are populated
 * by LiveInsightConsumer reading off the {@code insights-*} Kafka topics.
 */
@RestController
@RequestMapping("/api/matches/{wyId}/insights")
@CrossOrigin(origins = "*")
public class InsightController {

    private final InsightService insights;
    private final MatchService matches;

    public InsightController(InsightService insights, MatchService matches) {
        this.insights = insights;
        this.matches = matches;
    }

    /**
     * All current live insights for one match, grouped by type then by team:
     * {
     *   "pressing":        { "9001": {payload}, "9010": {payload} },
     *   "passing-network": { "9001": {payload}, "9010": {payload} },
     *   ...
     * }
     */
    @GetMapping
    public ResponseEntity<Map<String, Map<String, Object>>> all(@PathVariable Long wyId) {
        return matches.findByWyId(wyId)
                .map(m -> {
                    Map<String, Map<String, Object>> out = new HashMap<>();
                    for (LiveInsight row : insights.listForMatch(m.getId())) {
                        out
                                .computeIfAbsent(toWireType(row.getType()), k -> new HashMap<>())
                                .put(String.valueOf(row.getTeamId()), Map.of(
                                        "computedAt", row.getComputedAt().toString(),
                                        "payload", row.getPayload()
                                ));
                    }
                    return ResponseEntity.ok(out);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Insight for a specific team. Required:
     *   GET /api/matches/{wyId}/insights/{type}?team_id=9001
     */
    @GetMapping("/{type}")
    public ResponseEntity<JsonNode> one(
            @PathVariable Long wyId,
            @PathVariable String type,
            @RequestParam("team_id") Long teamId
    ) {
        LiveInsight.Type t = parseType(type);
        if (t == null) return ResponseEntity.badRequest().build();
        return matches.findByWyId(wyId)
                .flatMap(m -> insights.get(m.getId(), t, teamId))
                .map(row -> ResponseEntity.ok(row.getPayload()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private LiveInsight.Type parseType(String wire) {
        return switch (wire) {
            case "passing-network" -> LiveInsight.Type.PASSING_NETWORK;
            case "pressing"        -> LiveInsight.Type.PRESSING;
            case "ball-losses"     -> LiveInsight.Type.BALL_LOSSES;
            case "line-breaks"     -> LiveInsight.Type.LINE_BREAKS;
            case "player-profile"  -> LiveInsight.Type.PLAYER_PROFILE;
            default -> null;
        };
    }

    private String toWireType(LiveInsight.Type t) {
        return switch (t) {
            case PASSING_NETWORK -> "passing-network";
            case PRESSING        -> "pressing";
            case BALL_LOSSES     -> "ball-losses";
            case LINE_BREAKS     -> "line-breaks";
            case PLAYER_PROFILE  -> "player-profile";
        };
    }
}
