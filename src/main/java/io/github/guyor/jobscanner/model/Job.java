package io.github.guyor.jobscanner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** A job in Israel that we have seen on a career page. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Job {

    /** {ats}:{company slug}:{job id} */
    public String key;
    /** Company.id() of the owner. */
    public String companyId;
    public String title;
    public String location;
    public String url;
    public Instant publishedAt;
    public Instant firstSeenAt;
    public Instant closedAt;
    /** True when it was already open the first time we checked its company. */
    public boolean baseline;
    /** Consecutive successful checks in which the job was missing. */
    public int missingCount;

    public boolean isOpen() {
        return closedAt == null;
    }
}
