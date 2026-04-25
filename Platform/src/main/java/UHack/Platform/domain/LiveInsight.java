package UHack.Platform.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Latest snapshot of a live insight per (match, type). Upserted on every
 * recompute pushed by ai-service to a Kafka {@code insights-*} topic.
 *
 * The key (matchId, type) is unique — we keep the *current* state, not history.
 */
@Entity
@Table(name = "live_insight",
        uniqueConstraints = @UniqueConstraint(name = "uk_live_insight", columnNames = {"match_id", "type"}),
        indexes = @Index(name = "idx_live_insight_match", columnList = "match_id"))
public class LiveInsight {

    public enum Type {
        PASSING_NETWORK,
        PRESSING,
        BALL_LOSSES,
        LINE_BREAKS,
        PLAYER_PROFILE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private Type type;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    public LiveInsight() { }

    public LiveInsight(Long matchId, Type type, JsonNode payload) {
        this.matchId = matchId;
        this.type = type;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Instant getComputedAt() { return computedAt; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
