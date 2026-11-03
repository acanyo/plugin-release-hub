package com.xhhao.com.releasehub.security;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequestThrottle {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private record Window(long start, int count) { }
    public void check(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        if (windows.size() > 10000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() > 3600000);
            if (windows.size() > 10000 && !windows.containsKey(key)) throw limited();
        }
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.start() > windowMillis) return new Window(now, 1);
            if (w.count() >= limit) throw limited();
            return new Window(w.start(), w.count() + 1);
        });
    }
    private ResponseStatusException limited() { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"); }
}
