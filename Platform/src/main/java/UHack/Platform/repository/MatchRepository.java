package UHack.Platform.repository;

import UHack.Platform.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {
    Optional<Match> findByWyId(Long wyId);
}
