package UHack.Platform.controller;

import UHack.Platform.domain.Match;
import UHack.Platform.domain.MatchEvent;
import UHack.Platform.repository.MatchEventRepository;
import UHack.Platform.service.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = "*")
public class MatchController {

    private final MatchService matches;
    private final MatchEventRepository events;

    public MatchController(MatchService matches, MatchEventRepository events) {
        this.matches = matches;
        this.events = events;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return matches.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{wyId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long wyId) {
        return matches.findByWyId(wyId)
                .map(m -> ResponseEntity.ok(toDto(m)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{wyId}/events")
    public ResponseEntity<List<JsonNode>> events(@PathVariable Long wyId) {
        return matches.findByWyId(wyId)
                .map(m -> ResponseEntity.ok(
                        events.findByMatchIdOrderByMinuteAscSecondAscIdAsc(m.getId()).stream()
                                .map(MatchEvent::getPayload)
                                .collect(Collectors.toList())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(Match m) {
        return Map.of(
                "wyId", m.getWyId(),
                "label", m.getLabel() == null ? "" : m.getLabel(),
                "status", m.getStatus(),
                "homeTeamId", m.getHomeTeamId() == null ? "" : m.getHomeTeamId(),
                "awayTeamId", m.getAwayTeamId() == null ? "" : m.getAwayTeamId(),
                "scoreHome", m.getScoreHome() == null ? 0 : m.getScoreHome(),
                "scoreAway", m.getScoreAway() == null ? 0 : m.getScoreAway(),
                "startedAt", m.getStartedAt() == null ? "" : m.getStartedAt().toString(),
                "endedAt", m.getEndedAt() == null ? "" : m.getEndedAt().toString()
        );
    }
}
