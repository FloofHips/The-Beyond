package com.thebeyond.client.renderer;

import com.thebeyond.TheBeyond;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** First render failure per site with its stack trace, then a count every 10 s, so a failure each frame cannot flood the log. */
public final class RenderFailureLog {
    private static final long QUIET_MS = 10_000L;
    private static final Map<String, long[]> SEEN = new ConcurrentHashMap<>();

    private RenderFailureLog() {
    }

    public static void error(String site, Throwable t) {
        long now = System.currentTimeMillis();
        long[] seen = SEEN.computeIfAbsent(site, k -> new long[2]);
        seen[0]++;
        if (seen[0] == 1) {
            TheBeyond.LOGGER.error(site, t);
            seen[1] = now;
        } else if (now - seen[1] >= QUIET_MS) {
            TheBeyond.LOGGER.error("{} ({} times so far, latest: {})", site, seen[0], t.toString());
            seen[1] = now;
        }
    }
}
