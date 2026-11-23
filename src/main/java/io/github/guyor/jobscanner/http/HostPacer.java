package io.github.guyor.jobscanner.http;

import io.github.guyor.jobscanner.model.HostState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Per-host politeness: one request at a time (the caller is single threaded), a random gap between
 * requests, AIMD on the gap (halve-speed on 429/403, shave a little off on success) and a freeze after
 * repeated failures. The persisted part is {@link HostState}; it survives between runs.
 */
public class HostPacer {

    public static final int FREEZE_AFTER_FAILURES = 3;
    public static final Duration FREEZE_FOR = Duration.ofHours(2);
    static final long MAX_INTERVAL_MS = Duration.ofMinutes(5).toMillis();
    static final long DEFAULT_BASE_MS = 5_000;
    static final long DECREASE_STEP_MS = 250;

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Map<String, HostState> states;
    private final Map<String, Long> baseMs = new HashMap<>();
    private final Map<String, Instant> lastRequest = new HashMap<>();
    private final Clock clock;
    private final Sleeper sleeper;
    private final RandomGenerator random;

    public HostPacer(Map<String, HostState> states, Clock clock, Sleeper sleeper, RandomGenerator random) {
        this.states = states;
        this.clock = clock;
        this.sleeper = sleeper;
        this.random = random;
    }

    public void setBase(String host, long millis) {
        baseMs.put(host, millis);
    }

    private long base(String host) {
        return baseMs.getOrDefault(host, DEFAULT_BASE_MS);
    }

    private HostState state(String host) {
        return states.computeIfAbsent(host, h -> new HostState(base(h)));
    }

    public boolean isFrozen(String host) {
        HostState s = state(host);
        return s.frozenUntil != null && s.frozenUntil.isAfter(clock.instant());
    }

    /** Waits until the next request to this host is allowed. */
    public void await(String host) throws InterruptedException {
        HostState s = state(host);
        long interval = Math.max(s.intervalMs, base(host));
        // 0.75x .. 1.25x so requests do not land on a fixed beat
        long gap = (long) (interval * (0.75 + random.nextDouble() * 0.5));
        Instant last = lastRequest.get(host);
        if (last != null) {
            long wait = gap - Duration.between(last, clock.instant()).toMillis();
            if (wait > 0) {
                sleeper.sleep(wait);
            }
        }
        lastRequest.put(host, clock.instant());
    }

    public void success(String host) {
        HostState s = state(host);
        s.errorStreak = 0;
        s.frozenUntil = null;
        s.intervalMs = Math.max(base(host), s.intervalMs - DECREASE_STEP_MS);
    }

    /** 429/403: the site wants us to slow down. Doubles the gap and honours Retry-After if it is longer. */
    public void throttled(String host, Duration retryAfter) {
        HostState s = state(host);
        long doubled = Math.min(MAX_INTERVAL_MS, Math.max(s.intervalMs, base(host)) * 2);
        if (retryAfter != null) {
            doubled = Math.max(doubled, Math.min(MAX_INTERVAL_MS, retryAfter.toMillis()));
        }
        s.intervalMs = doubled;
        failure(host);
    }

    public void failure(String host) {
        HostState s = state(host);
        s.errorStreak++;
        if (s.errorStreak >= FREEZE_AFTER_FAILURES) {
            s.frozenUntil = clock.instant().plus(FREEZE_FOR);
        }
    }
}
