package UHack.Platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Live insights are kept in Redis with a 24h TTL — never written to Postgres.
 *
 * Why Redis (not Postgres):
 *   - Recomputed every 3s by ai-service ⇒ ~18k upserts per 90-min match.
 *   - Only the latest snapshot ever matters; no history value.
 *   - Read on the hot path by SSE / REST.
 *
 * Key format: {@code insight:{wyId}:{TYPE}:{teamId}}
 * Value:      {@code {"computedAt": "...", "payload": {...}}}
 */
@Service
public class InsightService {

    public enum Type {
        PASSING_NETWORK,
        PRESSING,
        BALL_LOSSES,
        LINE_BREAKS,
        PLAYER_PROFILE
    }

    /** A single Redis-backed insight snapshot, mirroring what came in over Kafka. */
    public record Snapshot(Long wyId, Long teamId, Type type, Instant computedAt, JsonNode payload) {}

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "insight:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public InsightService(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /** Latest-wins write of one insight type+team for a match. */
    public void upsert(Long wyId, Long teamId, Type type, JsonNode payload) {
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("computedAt", Instant.now().toString());
        wrapper.set("payload", payload);
        try {
            String json = mapper.writeValueAsString(wrapper);
            redis.opsForValue().set(key(wyId, type, teamId), json, TTL);
        } catch (Exception e) {
            log.warn("insight upsert failed wyId={} type={} team={}: {}", wyId, type, teamId, e.getMessage());
        }
    }

    /** Single insight payload (no wrapper). */
    public Optional<JsonNode> get(Long wyId, Type type, Long teamId) {
        String v = redis.opsForValue().get(key(wyId, type, teamId));
        if (v == null) return Optional.empty();
        try {
            return Optional.of(mapper.readTree(v).path("payload"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** All current snapshots for a match across all (type, team) combinations. */
    public List<Snapshot> listForMatch(Long wyId) {
        Set<String> keys = redis.keys(KEY_PREFIX + wyId + ":*");
        if (keys == null || keys.isEmpty()) return List.of();
        List<Snapshot> out = new ArrayList<>(keys.size());
        for (String k : keys) {
            String v = redis.opsForValue().get(k);
            if (v == null) continue;
            try {
                JsonNode wrapper = mapper.readTree(v);
                String[] parts = k.split(":"); // insight, wyId, TYPE, teamId
                if (parts.length != 4) continue;
                Type t = Type.valueOf(parts[2]);
                Long teamId = Long.valueOf(parts[3]);
                Instant computedAt;
                try {
                    computedAt = Instant.parse(wrapper.path("computedAt").asText());
                } catch (Exception ignore) {
                    computedAt = Instant.EPOCH;
                }
                out.add(new Snapshot(wyId, teamId, t, computedAt, wrapper.path("payload")));
            } catch (Exception e) {
                log.debug("skipping malformed insight key {}: {}", k, e.getMessage());
            }
        }
        return out;
    }

    private static String key(Long wyId, Type type, Long teamId) {
        return KEY_PREFIX + wyId + ":" + type.name() + ":" + teamId;
    }
}
