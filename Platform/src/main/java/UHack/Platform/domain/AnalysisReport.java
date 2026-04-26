package UHack.Platform.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Post-match analysis report. Produced by ai-decision-quality or
 * ai-tactical-intelligence after the match-end sentinel.
 *
 * One row per (match, type). Latest-wins on type collision.
 */
@Entity
@Table(name = "analysis_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_analysis_report", columnNames = {"match_id", "type"}),
        indexes = @Index(name = "idx_analysis_report_match", columnList = "match_id"))
public class AnalysisReport {

    public enum Type {
        DECISION_QUALITY,
        TACTICAL_INTELLIGENCE,
        TACTICAL_FUSION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private Type type;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    public AnalysisReport() { }

    public AnalysisReport(Long matchId, Type type, JsonNode payload) {
        this.matchId = matchId;
        this.type = type;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
