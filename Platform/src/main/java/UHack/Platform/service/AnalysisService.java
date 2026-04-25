package UHack.Platform.service;

import UHack.Platform.domain.AnalysisReport;
import UHack.Platform.repository.AnalysisReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AnalysisService {

    private final AnalysisReportRepository repo;

    public AnalysisService(AnalysisReportRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public AnalysisReport upsert(Long matchId, AnalysisReport.Type type, JsonNode payload) {
        AnalysisReport row = repo.findByMatchIdAndType(matchId, type)
                .orElseGet(() -> new AnalysisReport(matchId, type, payload));
        row.setPayload(payload);
        row.setGeneratedAt(Instant.now());
        return repo.save(row);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisReport> get(Long matchId, AnalysisReport.Type type) {
        return repo.findByMatchIdAndType(matchId, type);
    }

    @Transactional(readOnly = true)
    public List<AnalysisReport> listForMatch(Long matchId) {
        return repo.findByMatchId(matchId);
    }
}
