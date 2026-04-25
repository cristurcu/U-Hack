package UHack.Platform.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One row per known match. Created lazily the first time we see an event for a
 * new {@code wyId}. Score / status fields update as the match progresses;
 * {@code endedAt} flips once the match-end sentinel is processed.
 */
@Entity
@Table(name = "match", indexes = {
        @Index(name = "idx_match_wyid", columnList = "wy_id", unique = true)
})
public class Match {

    public enum Status {
        LIVE,
        FINISHED,    // last whistle, ai-service published match_end
        ANALYSED     // DQ + TI reports stored
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wy_id", nullable = false, unique = true)
    private Long wyId;

    @Column(name = "label")
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.LIVE;

    @Column(name = "competition_id")
    private Long competitionId;

    @Column(name = "season_id")
    private Long seasonId;

    @Column(name = "home_team_id")
    private Long homeTeamId;

    @Column(name = "away_team_id")
    private Long awayTeamId;

    @Column(name = "score_home")
    private Integer scoreHome;

    @Column(name = "score_away")
    private Integer scoreAway;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public Match() { }

    public Match(Long wyId) {
        this.wyId = wyId;
        this.startedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWyId() { return wyId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Long getCompetitionId() { return competitionId; }
    public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }
    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }
    public Integer getScoreHome() { return scoreHome; }
    public void setScoreHome(Integer scoreHome) { this.scoreHome = scoreHome; }
    public Integer getScoreAway() { return scoreAway; }
    public void setScoreAway(Integer scoreAway) { this.scoreAway = scoreAway; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
