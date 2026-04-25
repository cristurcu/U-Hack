package UHack.Platform.controller;

import UHack.Platform.service.InsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE channel for live insight pushes. Frontend opens
 * {@code GET /sse/matches/{wyId}} and keeps the connection open; whenever a
 * new insight snapshot lands in Redis, the diff-poll tick emits it.
 *
 * No Kafka consumer here — we lean on the cache that LiveInsightConsumer
 * already fills, so SSE is a thin read-and-broadcast loop.
 */
@RestController
@RequestMapping("/sse/matches")
@CrossOrigin(origins = "*")
public class LiveStreamController {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamController.class);
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;
    private static final long HEARTBEAT_MS = 15_000;

    private final InsightService insights;

    /** Per-match active emitters. */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Last-pushed computedAt per (wyId, type, teamId) — only emit on advance. */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** Per-match last heartbeat clock. */
    private final Map<Long, Instant> lastHeartbeat = new ConcurrentHashMap<>();

    public LiveStreamController(InsightService insights) {
        this.insights = insights;
    }

    @GetMapping(path = "/{wyId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long wyId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(wyId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(wyId, emitter));
        emitter.onTimeout(() -> remove(wyId, emitter));
        emitter.onError(t -> remove(wyId, emitter));

        try {
            sendSnapshot(emitter, wyId);
        } catch (IOException e) {
            remove(wyId, emitter);
        }

        return emitter;
    }

    private void remove(Long wyId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(wyId);
        if (list != null) list.remove(emitter);
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        if (emitters.isEmpty()) return;
        Instant now = Instant.now();

        for (Map.Entry<Long, List<SseEmitter>> entry : emitters.entrySet()) {
            Long wyId = entry.getKey();
            List<SseEmitter> list = entry.getValue();
            if (list.isEmpty()) continue;

            for (InsightService.Snapshot snap : insights.listForMatch(wyId)) {
                String key = wyId + "|" + snap.type() + "|" + snap.teamId();
                Instant prev = lastSeen.get(key);
                if (prev == null || snap.computedAt().isAfter(prev)) {
                    lastSeen.put(key, snap.computedAt());
                    broadcast(list, "insight", toBody(wyId, snap));
                }
            }

            Instant lastHb = lastHeartbeat.get(wyId);
            if (lastHb == null || now.toEpochMilli() - lastHb.toEpochMilli() > HEARTBEAT_MS) {
                lastHeartbeat.put(wyId, now);
                broadcast(list, "heartbeat", Map.of("at", now.toString()));
            }
        }
    }

    private void sendSnapshot(SseEmitter emitter, Long wyId) throws IOException {
        for (InsightService.Snapshot snap : insights.listForMatch(wyId)) {
            emitter.send(SseEmitter.event().name("insight").data(toBody(wyId, snap)));
            lastSeen.put(wyId + "|" + snap.type() + "|" + snap.teamId(), snap.computedAt());
        }
        emitter.send(SseEmitter.event().name("ready").data(Map.of("matchId", wyId)));
    }

    private static Map<String, Object> toBody(Long wyId, InsightService.Snapshot snap) {
        Map<String, Object> body = new HashMap<>();
        body.put("matchId", wyId);
        body.put("teamId", snap.teamId());
        body.put("type", wireType(snap.type()));
        body.put("computedAt", snap.computedAt().toString());
        body.put("payload", snap.payload());
        return body;
    }

    private void broadcast(List<SseEmitter> list, String name, Object body) {
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(name).data(body));
            } catch (Exception e) {
                emitter.complete();
            }
        }
    }

    private static String wireType(InsightService.Type t) {
        return switch (t) {
            case PASSING_NETWORK -> "passing-network";
            case PRESSING        -> "pressing";
            case BALL_LOSSES     -> "ball-losses";
            case LINE_BREAKS     -> "line-breaks";
            case PLAYER_PROFILE  -> "player-profile";
        };
    }
}
