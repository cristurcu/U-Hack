package UHack.Platform.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One row per Wyscout event. Matches the raw shape from the wyscout-events
 * Kafka topic — payload is the full event JSON (jsonb).
 */
@Entity
@Table(name = "match_event", indexes = {
        @Index(name = "idx_event_match_minute", columnList = "match_id, minute, second"),
        @Index(name = "idx_event_wyid", columnList = "wy_event_id")
})
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "wy_event_id")
    private Long wyEventId;

    @Column(name = "minute")
    private Integer minute;

    @Column(name = "second")
    private Integer second;

    @Column(name = "period", length = 8)
    private String period;

    @Column(name = "primary_type", length = 32)
    private String primaryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public MatchEvent() { }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Long getWyEventId() { return wyEventId; }
    public void setWyEventId(Long wyEventId) { this.wyEventId = wyEventId; }
    public Integer getMinute() { return minute; }
    public void setMinute(Integer minute) { this.minute = minute; }
    public Integer getSecond() { return second; }
    public void setSecond(Integer second) { this.second = second; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getPrimaryType() { return primaryType; }
    public void setPrimaryType(String primaryType) { this.primaryType = primaryType; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
}
