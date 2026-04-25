package UHack.Platform.controller;

import UHack.Platform.service.InsightService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only API for live insights, served from Redis (no Postgres on the
 * hot path).
 */
@RestController
@RequestMapping("/api/matches/{wyId}/insights")
@CrossOrigin(origins = "*")
public class InsightController {

    private final InsightService insights;

    public InsightController(InsightService insights) {
        this.insights = insights;
    }

    /**
     * All current live insights for a match, grouped by type then team:
     * {
     *   "pressing":        { "9001": {payload}, "9010": {payload} },
     *   "passing-network": { "9001": {payload}, "9010": {payload} },
     *   ...
     * }
     */
    @GetMapping
    public ResponseEntity<Map<String, Map<String, Object>>> all(@PathVariable Long wyId) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (InsightService.Snapshot snap : insights.listForMatch(wyId)) {
            out
                    .computeIfAbsent(toWireType(snap.type()), k -> new HashMap<>())
                    .put(String.valueOf(snap.teamId()), Map.of(
                            "computedAt", snap.computedAt().toString(),
                            "payload", snap.payload()
                    ));
        }
        return ResponseEntity.ok(out);
    }

    /** Insight for a specific team: GET /api/matches/{wyId}/insights/{type}?team_id=9001 */
    @GetMapping("/{type}")
    public ResponseEntity<JsonNode> one(
            @PathVariable Long wyId,
            @PathVariable String type,
            @RequestParam("team_id") Long teamId
    ) {
        InsightService.Type t = parseType(type);
        if (t == null) return ResponseEntity.badRequest().build();
        return insights.get(wyId, t, teamId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private InsightService.Type parseType(String wire) {
        return switch (wire) {
            case "passing-network" -> InsightService.Type.PASSING_NETWORK;
            case "pressing"        -> InsightService.Type.PRESSING;
            case "ball-losses"     -> InsightService.Type.BALL_LOSSES;
            case "line-breaks"     -> InsightService.Type.LINE_BREAKS;
            case "player-profile"  -> InsightService.Type.PLAYER_PROFILE;
            default -> null;
        };
    }

    private String toWireType(InsightService.Type t) {
        return switch (t) {
            case PASSING_NETWORK -> "passing-network";
            case PRESSING        -> "pressing";
            case BALL_LOSSES     -> "ball-losses";
            case LINE_BREAKS     -> "line-breaks";
            case PLAYER_PROFILE  -> "player-profile";
        };
    }
}
