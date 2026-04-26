package UHack.Platform.controller;

import UHack.Platform.service.PostMatchPipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/post-match")
@CrossOrigin(origins = "*")
public class PostMatchUploadController {

    private final PostMatchPipelineService pipeline;
    private final ObjectMapper mapper;

    public PostMatchUploadController(PostMatchPipelineService pipeline, ObjectMapper mapper) {
        this.pipeline = pipeline;
        this.mapper = mapper;
    }

    @PostMapping("/analyze")
    public ResponseEntity<JsonNode> analyzeUploadedMatch(
            @RequestParam Map<String, MultipartFile> files,
            @RequestParam(value = "matchId", required = false) Long matchId,
            @RequestParam(value = "homeTeamName", required = false) String homeTeamName,
            @RequestParam(value = "awayTeamName", required = false) String awayTeamName,
            @RequestParam(value = "homeScore", required = false) Integer homeScore,
            @RequestParam(value = "awayScore", required = false) Integer awayScore,
            @RequestParam(value = "focusTeamName", required = false) String focusTeamName
    ) {
        try {
            MultipartFile eventsFile = firstFile(files, "events", "matchEvents", "match_events");
            MultipartFile playersStatsFile = firstFile(files, "playersStats", "players_stats", "_players_stats");
            JsonNode eventsPayload = mapper.readTree(eventsFile.getInputStream());
            JsonNode playersStats = mapper.readTree(playersStatsFile.getInputStream());
            ObjectNode result = pipeline.runUploadedPipeline(
                    eventsPayload,
                    playersStats,
                    matchId,
                    homeTeamName,
                    awayTeamName,
                    homeScore,
                    awayScore,
                    focusTeamName
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "post-match pipeline failed");
            error.put("details", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private MultipartFile firstFile(Map<String, MultipartFile> files, String... names) {
        List<String> accepted = List.of(names);
        for (String name : names) {
            MultipartFile file = files.get(name);
            if (file != null && !file.isEmpty()) {
                return file;
            }
        }
        throw new IllegalArgumentException("Missing multipart file. Accepted field names: " + accepted);
    }
}
