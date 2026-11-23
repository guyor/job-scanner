package io.github.guyor.jobscanner.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** A company career page we track. Plain mutable data, persisted as JSON. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Company {

    public static final String UNVERIFIED = "unverified";
    public static final String ACTIVE = "active";
    public static final String NO_ISRAEL = "no_israel";
    public static final String GONE = "gone";

    public String ats;
    public String slug;
    public String name;
    public String status = UNVERIFIED;
    public String source;
    public Instant discoveredAt;
    public Instant lastCheckedAt;
    public Instant nextCheckAt;
    public int errorStreak;
    public int notFoundStreak;

    public Company() {
    }

    public Company(String ats, String slug, String name, String source, Instant discoveredAt) {
        this.ats = ats;
        this.slug = slug;
        this.name = name;
        this.source = source;
        this.discoveredAt = discoveredAt;
    }

    @JsonIgnore
    public String id() {
        return ats + ":" + slug;
    }
}
