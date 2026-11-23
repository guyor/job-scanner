package io.github.guyor.jobscanner.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.model.HostState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class HostPacerTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    final MutableClock clock = new MutableClock();
    final Map<String, HostState> states = new HashMap<>();
    long slept;
    // random.nextDouble() == 0.5 -> factor exactly 1.0
    final RandomGenerator fixed = new java.util.Random() {
        @Override public double nextDouble() { return 0.5; }
    };
    final HostPacer pacer = new HostPacer(states, clock, ms -> { slept += ms; clock.now = clock.now.plusMillis(ms); }, fixed);

    HostPacerTest() {
        pacer.setBase("h", 2000);
    }

    @Test
    void waitsTheIntervalBetweenRequests() throws Exception {
        pacer.await("h");
        assertThat(slept).isZero();
        pacer.await("h");
        assertThat(slept).isEqualTo(2000);
    }

    @Test
    void throttleDoublesAndSuccessShavesBack() {
        pacer.throttled("h", null);
        assertThat(states.get("h").intervalMs).isEqualTo(4000);
        pacer.throttled("h", null);
        assertThat(states.get("h").intervalMs).isEqualTo(8000);
        pacer.success("h");
        assertThat(states.get("h").intervalMs).isEqualTo(7750);
    }

    @Test
    void retryAfterWinsWhenLonger() {
        pacer.throttled("h", Duration.ofSeconds(60));
        assertThat(states.get("h").intervalMs).isEqualTo(60_000);
    }

    @Test
    void intervalNeverGoesBelowBase() {
        pacer.success("h");
        assertThat(states.get("h").intervalMs).isEqualTo(2000);
    }

    @Test
    void freezesAfterThreeFailuresAndThawsAfterTwoHours() {
        pacer.failure("h");
        pacer.failure("h");
        assertThat(pacer.isFrozen("h")).isFalse();
        pacer.failure("h");
        assertThat(pacer.isFrozen("h")).isTrue();
        clock.now = clock.now.plus(Duration.ofHours(2)).plusSeconds(1);
        assertThat(pacer.isFrozen("h")).isFalse();
    }

    @Test
    void successResetsFailureStreak() {
        pacer.failure("h");
        pacer.failure("h");
        pacer.success("h");
        pacer.failure("h");
        assertThat(pacer.isFrozen("h")).isFalse();
    }
}
