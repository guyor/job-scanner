package io.github.guyor.jobscanner.scan;

import io.github.guyor.jobscanner.filter.FilterConfig;
import io.github.guyor.jobscanner.filter.TitleFilter;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.Job;
import io.github.guyor.jobscanner.state.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the contents of site/feed.json. Baseline jobs (already open when we first looked at a company)
 * never appear. Jobs of a recently discovered company go to the "companies" tab, the rest to "new".
 */
public class FeedBuilder {

    private static final int MAX_JOBS = 1500;
    private static final Duration KEEP = Duration.ofDays(30);

    private final TitleFilter titles;
    private final Clock clock;
    private final Duration recentCompany;

    public FeedBuilder(TitleFilter titles, Clock clock, Duration recentCompany) {
        this.titles = titles;
        this.clock = clock;
        this.recentCompany = recentCompany;
    }

    public Map<String, Object> build(State state, Instant lastScanAt) {
        Instant now = clock.instant();
        List<Map<String, Object>> jobs = new ArrayList<>();
        int newToday = 0;
        long dayStart = now.atZone(ZoneId.of("Asia/Jerusalem")).toLocalDate()
                .atStartOfDay(ZoneId.of("Asia/Jerusalem")).toInstant().toEpochMilli();
        List<Job> ordered = state.jobs.values().stream()
                .filter(j -> j.isOpen() && !j.baseline && j.firstSeenAt.isAfter(now.minus(KEEP)))
                .sorted(Comparator.comparing((Job j) -> j.firstSeenAt).reversed())
                .limit(MAX_JOBS)
                .toList();
        for (Job j : ordered) {
            Company c = state.companies.get(j.companyId);
            if (c == null) {
                continue;
            }
            boolean fresh = c.discoveredAt != null && c.discoveredAt.isAfter(now.minus(recentCompany));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", j.key);
            m.put("title", j.title);
            m.put("company", c.name);
            m.put("location", j.location == null ? "" : j.location);
            m.put("url", j.url);
            m.put("publishedAt", j.publishedAt);
            m.put("firstSeenAt", j.firstSeenAt);
            m.put("seniority", titles.evaluate(j.title).seniority());
            m.put("tab", fresh ? "companies" : "new");
            jobs.add(m);
            if (j.firstSeenAt.toEpochMilli() >= dayStart) {
                newToday++;
            }
        }
        long companies = state.companies.values().stream().filter(c -> Company.ACTIVE.equals(c.status)).count();
        long companiesNew = state.companies.values().stream()
                .filter(c -> c.discoveredAt != null && c.discoveredAt.isAfter(now.minus(recentCompany))).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("companies", companies);
        stats.put("companiesNew", companiesNew);
        stats.put("jobsNewToday", newToday);
        Map<String, Object> feed = new LinkedHashMap<>();
        feed.put("generatedAt", now);
        feed.put("lastScanAt", lastScanAt);
        feed.put("stats", stats);
        feed.put("jobs", jobs);
        return feed;
    }
}
