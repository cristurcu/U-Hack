package UHack.Platform.repository;

import UHack.Platform.domain.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {
    List<MatchEvent> findByMatchIdOrderByMinuteAscSecondAscIdAsc(Long matchId);
    long countByMatchId(Long matchId);
    boolean existsByWyEventId(Long wyEventId);
}
