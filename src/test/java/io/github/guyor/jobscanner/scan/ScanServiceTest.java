package io.github.guyor.jobscanner.scan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.ats.AtsAdapter;
import io.github.guyor.jobscanner.filter.FilterConfig;
import io.github.guyor.jobscanner.filter.LocationFilter;
import io.github.guyor.jobscanner.filter.TitleFilter;
import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.Job;
import io.github.guyor.jobscanner.model.RawJob;
import io.github.guyor.jobscanner.state.State;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScanServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    /** Serves whatever {@code jobs} holds; null means 404, {@code failure} is thrown once set. */
    static class FakeAdapter implements AtsAdapter {
        List<RawJob> jobs = new ArrayList<>();
        boolean notFound;
        AtsException failure;
        int calls;

        public String ats() { return "greenhouse"; }

        public Optional<List<RawJob>> fetchJobs(Company c) throws IOException {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return notFound ? Optional.empty() : Optional.of(new ArrayList<>(jobs));
        }
    }

    final MutableClock clock = new MutableClock();
    final FakeAdapter adapter = new FakeAdapter();
    final State state = new State();
    Company acme;
    ScanService service;

    @BeforeEach
    void setUp() throws Exception {
        FilterConfig cfg = FilterConfig.load(Path.of("config/filters.yml"));
        service = new ScanService(List.of(adapter), new TitleFilter(cfg), new LocationFilter(cfg), clock, Duration.ofMinutes(60));
        acme = new Company("greenhouse", "acme", "Acme", "test", clock.now);
        state.companies.put(acme.id(), acme);
    }

    static RawJob job(String id, String title, String location) {
        return new RawJob(id, title, location, "https://example.com/" + id, null);
    }

    void check() throws Exception {
        service.checkCompany(state, acme);
    }

    void advance(Duration d) {
        clock.now = clock.now.plus(d);
    }

    @Test
    void firstCheckMarksOpenJobsAsBaseline() throws Exception {
        adapter.jobs = List.of(job("1", "Senior Backend Engineer", "Tel Aviv"));
        check();
        Job j = state.jobs.get("greenhouse:acme:1");
        assertThat(j.baseline).isTrue();
        assertThat(acme.status).isEqualTo(Company.ACTIVE);
    }

    @Test
    void jobAppearingLaterIsNotBaseline() throws Exception {
        adapter.jobs = List.of(job("1", "Senior Backend Engineer", "Tel Aviv"));
        check();
        advance(Duration.ofHours(1));
        adapter.jobs = List.of(job("1", "Senior Backend Engineer", "Tel Aviv"), job("2", "AI Engineer", "Tel Aviv"));
        check();
        assertThat(state.jobs.get("greenhouse:acme:1").baseline).isTrue();
        Job fresh = state.jobs.get("greenhouse:acme:2");
        assertThat(fresh.baseline).isFalse();
        assertThat(fresh.firstSeenAt).isEqualTo(clock.now);
    }

    @Test
    void filtersByTitleAndLocation() throws Exception {
        adapter.jobs = List.of(
                job("1", "Backend Engineer", "New York"),
                job("2", "Account Executive", "Tel Aviv"),
                job("3", "Backend Team Lead", "Tel Aviv"),
                job("4", "Java Specialist", "Herzliya"),
                job("5", "Backend Engineer", "Tel Aviv"));
        check();
        assertThat(state.jobs.keySet()).containsExactly("greenhouse:acme:5");
        assertThat(state.nearMisses).containsOnlyKeys("greenhouse:acme:4");
    }

    @Test
    void closesOnlyAfterTwoConsecutiveMisses() throws Exception {
        adapter.jobs = List.of(job("1", "Backend Engineer", "Tel Aviv"));
        check();
        adapter.jobs = List.of();
        advance(Duration.ofHours(1));
        check();
        Job j = state.jobs.get("greenhouse:acme:1");
        assertThat(j.isOpen()).isTrue();
        assertThat(j.missingCount).isEqualTo(1);
        advance(Duration.ofHours(1));
        check();
        assertThat(j.isOpen()).isFalse();
    }

    @Test
    void reappearingJobResetsTheMissCount() throws Exception {
        adapter.jobs = List.of(job("1", "Backend Engineer", "Tel Aviv"));
        check();
        adapter.jobs = List.of();
        advance(Duration.ofHours(1));
        check();
        adapter.jobs = List.of(job("1", "Backend Engineer", "Tel Aviv"));
        advance(Duration.ofHours(1));
        check();
        adapter.jobs = List.of();
        advance(Duration.ofHours(1));
        check();
        assertThat(state.jobs.get("greenhouse:acme:1").isOpen()).isTrue();
    }

    @Test
    void failedCheckNeverCountsAsAMiss() throws Exception {
        adapter.jobs = List.of(job("1", "Backend Engineer", "Tel Aviv"));
        check();
        adapter.failure = new AtsException(AtsException.Kind.ERROR, "boom");
        for (int i = 0; i < 4; i++) {
            advance(Duration.ofHours(1));
            check();
        }
        Job j = state.jobs.get("greenhouse:acme:1");
        assertThat(j.isOpen()).isTrue();
        assertThat(j.missingCount).isZero();
        assertThat(acme.errorStreak).isEqualTo(4);
    }

    @Test
    void errorsBackOffWithGrowingDelay() throws Exception {
        adapter.failure = new AtsException(AtsException.Kind.ERROR, "boom");
        check();
        Instant first = acme.nextCheckAt;
        advance(Duration.ofHours(7));
        check();
        assertThat(Duration.between(clock.now, acme.nextCheckAt)).isGreaterThan(Duration.between(clock.now.minus(Duration.ofHours(7)), first));
    }

    @Test
    void notFoundThreeTimesMarksCompanyGone() throws Exception {
        adapter.notFound = true;
        for (int i = 0; i < 3; i++) {
            check();
            advance(Duration.ofDays(2));
        }
        assertThat(acme.status).isEqualTo(Company.GONE);
        assertThat(service.dueQueue(state)).isEmpty();
    }

    @Test
    void companyWithoutIsraelJobsIsCheckedLessOften() throws Exception {
        adapter.jobs = List.of(job("1", "Backend Engineer", "London"));
        check();
        assertThat(acme.status).isEqualTo(Company.NO_ISRAEL);
        assertThat(Duration.between(clock.now, acme.nextCheckAt)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void queueIsOrderedByNextCheckAndSkipsNotDue() {
        Company never = new Company("greenhouse", "never", "Never", "t", clock.now);
        Company old = new Company("greenhouse", "old", "Old", "t", clock.now);
        old.nextCheckAt = clock.now.minusSeconds(600);
        Company later = new Company("greenhouse", "later", "Later", "t", clock.now);
        later.nextCheckAt = clock.now.plusSeconds(600);
        acme.nextCheckAt = clock.now.minusSeconds(60);
        state.companies.put(never.id(), never);
        state.companies.put(old.id(), old);
        state.companies.put(later.id(), later);
        assertThat(service.dueQueue(state)).extracting(c -> c.slug).containsExactly("never", "old", "acme");
    }

    @Test
    void runStopsWhenBudgetIsUsedUp() throws Exception {
        for (int i = 0; i < 5; i++) {
            Company c = new Company("greenhouse", "c" + i, "C" + i, "t", clock.now);
            state.companies.put(c.id(), c);
        }
        // a zero budget means nothing is checked, the rest simply stays due for the next run
        int checked = service.run(state, Duration.ZERO);
        assertThat(checked).isZero();
        assertThat(adapter.calls).isZero();
        assertThat(service.run(state, Duration.ofMinutes(5))).isEqualTo(6);
    }

    @Test
    void frozenHostIsSkippedWithoutRequests() throws Exception {
        var hs = new io.github.guyor.jobscanner.model.HostState(2000);
        hs.frozenUntil = clock.now.plus(Duration.ofHours(1));
        state.hosts.put("boards-api.greenhouse.io", hs);
        assertThat(service.run(state, Duration.ofMinutes(5))).isZero();
        assertThat(adapter.calls).isZero();
        assertThat(acme.nextCheckAt).isEqualTo(hs.frozenUntil);
    }
}
