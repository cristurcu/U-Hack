package UHack.Platform.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(long matchId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(matchId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> remove(matchId, emitter));
        emitter.onTimeout(() -> remove(matchId, emitter));
        emitter.onError(ex -> remove(matchId, emitter));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException ignored) {
            remove(matchId, emitter);
        }
        log.debug("SSE register match={} (active={})", matchId, list.size());
        return emitter;
    }

    public void broadcast(long matchId, String eventName, String json) {
        List<SseEmitter> list = emitters.get(matchId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (Exception ex) {
                emitter.completeWithError(ex);
                remove(matchId, emitter);
            }
        }
    }

    @Scheduled(fixedRate = 15000)
    public void keepAlive() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> e : emitters.entrySet()) {
            for (SseEmitter emitter : e.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ka"));
                } catch (Exception ex) {
                    remove(e.getKey(), emitter);
                }
            }
        }
    }

    private void remove(long matchId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(matchId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(matchId, list);
            }
        }
    }
}
