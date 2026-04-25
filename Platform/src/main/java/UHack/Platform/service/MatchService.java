package UHack.Platform.service;

import UHack.Platform.domain.Match;
import UHack.Platform.repository.MatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MatchService {

    private final MatchRepository matches;

    public MatchService(MatchRepository matches) {
        this.matches = matches;
    }

    /**
     * Returns the existing match row for {@code wyId}, or creates a new one.
     * Used by the wyscout-events consumer on every event so we always have a
     * row for the match the moment its first event lands.
     *
     * Concurrency-safe: multiple Kafka listener threads call this in parallel.
     * If two threads race to create the same wyId, the loser catches the
     * unique-constraint violation and re-fetches. We use REQUIRES_NEW for the
     * insert attempt so a failed save's rollback doesn't poison the caller's
     * outer transaction.
     */
    public Match findOrCreate(Long wyId) {
        return matches.findByWyId(wyId).orElseGet(() -> tryCreate(wyId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Match tryCreate(Long wyId) {
        try {
            return matches.saveAndFlush(new Match(wyId));
        } catch (DataIntegrityViolationException race) {
            // Another thread won — the row exists now, fetch it.
            return matches.findByWyId(wyId).orElseThrow(() -> new IllegalStateException(
                    "findOrCreate race recovery failed for wyId=" + wyId, race));
        }
    }

    @Transactional(readOnly = true)
    public Optional<Match> findByWyId(Long wyId) {
        return matches.findByWyId(wyId);
    }

    @Transactional(readOnly = true)
    public List<Match> findAll() {
        return matches.findAll();
    }

    /** Apply mutable fields from a Wyscout event's "match" sub-document if present. */
    @Transactional
    public void applyEventMetadata(Match match, JsonNode event) {
        JsonNode teamsData = event.path("match").path("teamsData");
        if (teamsData.isObject() && match.getHomeTeamId() == null) {
            JsonNode home = teamsData.path("home");
            JsonNode away = teamsData.path("away");
            if (home.has("teamId")) match.setHomeTeamId(home.get("teamId").asLong());
            if (away.has("teamId")) match.setAwayTeamId(away.get("teamId").asLong());
        }
        if (match.getLabel() == null && event.path("match").has("label")) {
            match.setLabel(event.path("match").get("label").asText());
        }
        // accept score updates if the event payload carries them
        JsonNode score = event.path("score");
        if (score.isObject()) {
            if (score.has("home")) match.setScoreHome(score.get("home").asInt());
            if (score.has("away")) match.setScoreAway(score.get("away").asInt());
        }
        matches.save(match);
    }

    @Transactional
    public void markFinished(Long wyId) {
        matches.findByWyId(wyId).ifPresent(m -> {
            m.setStatus(Match.Status.FINISHED);
            m.setEndedAt(java.time.Instant.now());
            matches.save(m);
        });
    }

    @Transactional
    public void markAnalysed(Long wyId) {
        matches.findByWyId(wyId).ifPresent(m -> {
            m.setStatus(Match.Status.ANALYSED);
            matches.save(m);
        });
    }
}
