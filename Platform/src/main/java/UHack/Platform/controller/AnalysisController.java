package UHack.Platform.controller;

import UHack.Platform.domain.AnalysisReport;
import UHack.Platform.service.AnalysisService;
import UHack.Platform.service.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only API for post-match analysis reports (decision-quality and
 * tactical-intelligence). Populated by AnalysisReportConsumer.
 */
@RestController
@RequestMapping("/api/matches/{wyId}/analysis")
@CrossOrigin(origins = "*")
public class AnalysisController {

    private final AnalysisService analyses;
    private final MatchService matches;

    public AnalysisController(AnalysisService analyses, MatchService matches) {
        this.analyses = analyses;
        this.matches = matches;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> all(@PathVariable Long wyId) {
        return matches.findByWyId(wyId)
                .map(m -> {
                    Map<String, Object> out = new HashMap<>();
                    for (AnalysisReport row : analyses.listForMatch(m.getId())) {
                        out.put(toWireType(row.getType()), Map.of(
                                "generatedAt", row.getGeneratedAt().toString(),
                                "payload", row.getPayload()
                        ));
                    }
                    return ResponseEntity.ok(out);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{type}")
    public ResponseEntity<JsonNode> one(@PathVariable Long wyId, @PathVariable String type) {
        AnalysisReport.Type t = parseType(type);
        if (t == null) return ResponseEntity.badRequest().build();
        return matches.findByWyId(wyId)
                .flatMap(m -> analyses.get(m.getId(), t))
                .map(row -> ResponseEntity.ok(row.getPayload()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private AnalysisReport.Type parseType(String wire) {
        return switch (wire) {
            case "decision-quality"      -> AnalysisReport.Type.DECISION_QUALITY;
            case "tactical-intelligence" -> AnalysisReport.Type.TACTICAL_INTELLIGENCE;
            case "tactical-fusion"       -> AnalysisReport.Type.TACTICAL_FUSION;
            default -> null;
        };
    }

    private String toWireType(AnalysisReport.Type t) {
        return switch (t) {
            case DECISION_QUALITY      -> "decision-quality";
            case TACTICAL_INTELLIGENCE -> "tactical-intelligence";
            case TACTICAL_FUSION       -> "tactical-fusion";
        };
    }
}
