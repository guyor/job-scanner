package io.github.guyor.jobscanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** Pacing state of one host, kept between runs so a freeze survives. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostState {
    public long intervalMs;
    public Instant frozenUntil;
    public int errorStreak;

    public HostState() {
    }

    public HostState(long intervalMs) {
        this.intervalMs = intervalMs;
    }
}
