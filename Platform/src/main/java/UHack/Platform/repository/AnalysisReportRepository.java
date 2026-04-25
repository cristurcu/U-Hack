package UHack.Platform.repository;

import UHack.Platform.domain.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    Optional<AnalysisReport> findByMatchIdAndType(Long matchId, AnalysisReport.Type type);
    List<AnalysisReport> findByMatchId(Long matchId);
}
