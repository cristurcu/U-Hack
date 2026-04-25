package UHack.Platform.repository;

import UHack.Platform.domain.LiveInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveInsightRepository extends JpaRepository<LiveInsight, Long> {
    Optional<LiveInsight> findByMatchIdAndTypeAndTeamId(Long matchId, LiveInsight.Type type, Long teamId);
    List<LiveInsight> findByMatchIdAndType(Long matchId, LiveInsight.Type type);
    List<LiveInsight> findByMatchId(Long matchId);
}
