package UHack.Platform.service;

import UHack.Platform.domain.LiveInsight;
import UHack.Platform.repository.LiveInsightRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class InsightService {

    private final LiveInsightRepository repo;

    public InsightService(LiveInsightRepository repo) {
        this.repo = repo;
    }

    /** Latest-wins upsert by (matchId, type, teamId). */
    @Transactional
    public LiveInsight upsert(Long matchId, Long teamId, LiveInsight.Type type, JsonNode payload) {
        LiveInsight row = repo.findByMatchIdAndTypeAndTeamId(matchId, type, teamId)
                .orElseGet(() -> new LiveInsight(matchId, teamId, type, payload));
        row.setPayload(payload);
        row.setComputedAt(Instant.now());
        return repo.save(row);
    }

    @Transactional(readOnly = true)
    public Optional<LiveInsight> get(Long matchId, LiveInsight.Type type, Long teamId) {
        return repo.findByMatchIdAndTypeAndTeamId(matchId, type, teamId);
    }

    @Transactional(readOnly = true)
    public List<LiveInsight> listForMatchAndType(Long matchId, LiveInsight.Type type) {
        return repo.findByMatchIdAndType(matchId, type);
    }

    @Transactional(readOnly = true)
    public List<LiveInsight> listForMatch(Long matchId) {
        return repo.findByMatchId(matchId);
    }
}
