package UHack.Platform.dashboard.kafka;

import UHack.Platform.dashboard.SseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DashboardListeners {

    private static final Logger log = LoggerFactory.getLogger(DashboardListeners.class);

    private final SseHub hub;

    public DashboardListeners(SseHub hub) {
        this.hub = hub;
    }

    @KafkaListener(topics = "${dashboard.topic.match-stats}", groupId = "spring-dashboard-stats")
    public void onMatchStats(String json) {
        Long matchId = MatchIdExtractor.extract(json, "metadata.matchId", "matchId");
        if (matchId == null) {
            log.debug("match-stats without matchId, skipping");
            return;
        }
        hub.broadcast(matchId, "match-stats", json);
    }

    @KafkaListener(topics = "${dashboard.topic.event-scores}", groupId = "spring-dashboard-scores")
    public void onEventScore(String json) {
        Long matchId = MatchIdExtractor.extract(json, "matchId");
        if (matchId == null) return;
        hub.broadcast(matchId, "event-score", json);
    }

    @KafkaListener(topics = "${dashboard.topic.passing-network}", groupId = "spring-dashboard-passing")
    public void onPassingNetwork(String json) {
        Long matchId = MatchIdExtractor.extract(json, "matchId");
        if (matchId == null) return;
        hub.broadcast(matchId, "passing-network", json);
    }

    @KafkaListener(topics = "${dashboard.topic.pressing-stats}", groupId = "spring-dashboard-pressing")
    public void onPressingStats(String json) {
        Long matchId = MatchIdExtractor.extract(json, "matchId");
        if (matchId == null) return;
        hub.broadcast(matchId, "pressing-stats", json);
    }
}
