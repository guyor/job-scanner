package io.github.guyor.jobscanner.scan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.filter.FilterConfig;
import io.github.guyor.jobscanner.filter.TitleFilter;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.Job;
import io.github.guyor.jobscanner.state.State;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeedBuilderTest {

    static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @SuppressWarnings("unchecked")
    @Test
    void buildsFeedFromOpenNonBaselineJobs() throws Exception {
        TitleFilter titles = new TitleFilter(FilterConfig.load(Path.of("config/filters.yml")));
        FeedBuilder fb = new FeedBuilder(titles, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(14));
        State s = new State();
        Company known = new Company("greenhouse", "known", "Known", "t", NOW.minus(Duration.ofDays(100)));
        known.status = Company.ACTIVE;
        Company fresh = new Company("greenhouse", "fresh", "Fresh", "t", NOW.minus(Duration.ofDays(2)));
        fresh.status = Company.ACTIVE;
        s.companies.put(known.id(), known);
        s.companies.put(fresh.id(), fresh);
        add(s, known, "1", "Senior Backend Engineer", NOW.minusSeconds(3600), false, false);
        add(s, known, "2", "Backend Engineer", NOW.minusSeconds(60), true, false);   // baseline: hidden
        add(s, known, "3", "AI Engineer", NOW.minusSeconds(120), false, true);       // closed: hidden
        add(s, fresh, "4", "Junior Java Developer", NOW.minusSeconds(1800), false, false);

        Map<String, Object> feed = fb.build(s, NOW);
        List<Map<String, Object>> jobs = (List<Map<String, Object>>) feed.get("jobs");
        assertThat(jobs).extracting(j -> j.get("key")).containsExactly("greenhouse:fresh:4", "greenhouse:known:1");
        assertThat(jobs.get(0).get("tab")).isEqualTo("companies");
        assertThat(jobs.get(0).get("seniority")).isEqualTo("junior");
        assertThat(jobs.get(1).get("tab")).isEqualTo("new");
        Map<String, Object> stats = (Map<String, Object>) feed.get("stats");
        assertThat(stats.get("companies")).isEqualTo(2L);
        assertThat(stats.get("companiesNew")).isEqualTo(1L);
        assertThat(stats.get("jobsNewToday")).isEqualTo(2);
        assertThat(feed.get("lastScanAt")).isEqualTo(NOW);
    }

    static void add(State s, Company c, String id, String title, Instant seen, boolean baseline, boolean closed) {
        Job j = new Job();
        j.key = c.ats + ":" + c.slug + ":" + id;
        j.companyId = c.id();
        j.title = title;
        j.location = "Tel Aviv";
        j.url = "https://example.com/" + id;
        j.firstSeenAt = seen;
        j.baseline = baseline;
        j.closedAt = closed ? NOW : null;
        s.jobs.put(j.key, j);
    }
}
