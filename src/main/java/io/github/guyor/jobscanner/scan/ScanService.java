package io.github.guyor.jobscanner.scan;

import io.github.guyor.jobscanner.ats.AtsAdapter;
import io.github.guyor.jobscanner.filter.LocationFilter;
import io.github.guyor.jobscanner.filter.TitleFilter;
import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.HostState;
import io.github.guyor.jobscanner.model.Job;
import io.github.guyor.jobscanner.model.RawJob;
import io.github.guyor.jobscanner.state.State;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks due companies and updates jobs: new, baseline, still open, closed. */
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    /** A job is closed only after it is missing in this many consecutive successful checks. */
    static final int MISSES_TO_CLOSE = 2;
    static final int NOT_FOUND_TO_GONE = 3;

    private final Map<String, AtsAdapter> adapters;
    private final TitleFilter titles;
    private final LocationFilter locations;
    private final Clock clock;
    private final Duration recheck;
    private final Duration noIsraelRecheck = Duration.ofHours(24);

    public ScanService(List<AtsAdapter> adapters, TitleFilter titles, LocationFilter locations,
                       Clock clock, Duration recheck) {
        this.adapters = new java.util.HashMap<>();
        adapters.forEach(a -> this.adapters.put(a.ats(), a));
        this.titles = titles;
        this.locations = locations;
        this.clock = clock;
        this.recheck = recheck;
    }

    /** Companies that are due now: known ones first, most overdue first; unverified candidates last. */
    public List<Company> dueQueue(State state) {
        Instant now = clock.instant();
        return state.companies.values().stream()
                .filter(c -> !Company.GONE.equals(c.status))
                .filter(c -> c.nextCheckAt == null || !c.nextCheckAt.isAfter(now))
                // Known companies first, so freshly discovered (unverified) ones only use the leftover budget.
                .sorted(Comparator.comparingInt((Company c) -> Company.UNVERIFIED.equals(c.status) ? 1 : 0)
                        .thenComparing(c -> c.nextCheckAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /** Works through the queue until it is empty or the time budget is used up. Returns companies checked. */
    public int run(State state, Duration budget) throws InterruptedException {
        Instant deadline = clock.instant().plus(budget);
        int checked = 0;
        for (Company c : dueQueue(state)) {
            if (!clock.instant().isBefore(deadline)) {
                log.info("Time budget used up after {} companies", checked);
                break;
            }
            String host = hostOf(c);
            HostState hs = state.hosts.get(host);
            if (hs != null && hs.frozenUntil != null && hs.frozenUntil.isAfter(clock.instant())) {
                c.nextCheckAt = hs.frozenUntil;
                continue;
            }
            checkCompany(state, c);
            checked++;
        }
        return checked;
    }

    static String hostOf(Company c) {
        return switch (c.ats) {
            case "greenhouse" -> "boards-api.greenhouse.io";
            case "lever" -> c.slug.startsWith("eu:") ? "api.eu.lever.co" : "api.lever.co";
            case "ashby" -> "api.ashbyhq.com";
            case "comeet" -> "www.comeet.com";
            default -> c.ats;
        };
    }

    public void checkCompany(State state, Company c) throws InterruptedException {
        Instant now = clock.instant();
        AtsAdapter adapter = adapters.get(c.ats);
        if (adapter == null) {
            c.nextCheckAt = now.plus(Duration.ofDays(1));
            log.warn("No adapter for {}", c.id());
            return;
        }
        Optional<List<RawJob>> fetched;
        try {
            fetched = adapter.fetchJobs(c);
        } catch (AtsException e) {
            onFailure(c, e, now);
            return;
        } catch (IOException e) {
            onFailure(c, new AtsException(AtsException.Kind.ERROR, e.getMessage()), now);
            return;
        }
        if (fetched.isEmpty()) {
            c.lastCheckedAt = now;
            c.notFoundStreak++;
            if (c.notFoundStreak >= NOT_FOUND_TO_GONE) {
                c.status = Company.GONE;
                log.info("{} not found {} times, marked gone", c.id(), c.notFoundStreak);
            }
            c.nextCheckAt = now.plus(Duration.ofDays(1));
            return;
        }
        c.notFoundStreak = 0;
        c.errorStreak = 0;
        apply(state, c, fetched.get(), now);
    }

    private void apply(State state, Company c, List<RawJob> raw, Instant now) {
        boolean firstCheck = c.lastCheckedAt == null;
        boolean anyInIsrael = false;
        Set<String> seen = new HashSet<>();
        for (RawJob r : raw) {
            if (!locations.inIsrael(r.location())) {
                continue;
            }
            anyInIsrael = true;
            String key = c.ats + ":" + c.slug + ":" + r.id();
            TitleFilter.Result res = titles.evaluate(r.title());
            if (res.verdict() == TitleFilter.Verdict.NEAR_MISS) {
                state.nearMisses.put(key, new State.NearMiss(c.name, r.title(), r.location(), r.url()));
            }
            if (res.verdict() != TitleFilter.Verdict.MATCH) {
                continue;
            }
            seen.add(key);
            Job j = state.jobs.get(key);
            if (j == null) {
                j = new Job();
                j.key = key;
                j.companyId = c.id();
                j.firstSeenAt = now;
                j.baseline = firstCheck;
                state.jobs.put(key, j);
            }
            j.title = r.title();
            j.location = r.location();
            j.url = r.url();
            if (r.publishedAt() != null) {
                j.publishedAt = r.publishedAt();
            }
            j.missingCount = 0;
            j.closedAt = null;
        }
        for (Job j : state.jobs.values()) {
            if (j.companyId.equals(c.id()) && j.isOpen() && !seen.contains(j.key)) {
                if (++j.missingCount >= MISSES_TO_CLOSE) {
                    j.closedAt = now;
                }
            }
        }
        c.status = anyInIsrael ? Company.ACTIVE : Company.NO_ISRAEL;
        c.lastCheckedAt = now;
        c.nextCheckAt = now.plus(anyInIsrael ? recheck : noIsraelRecheck);
    }

    private void onFailure(Company c, AtsException e, Instant now) {
        log.warn("{} failed ({}): {}", c.id(), e.kind(), e.getMessage());
        switch (e.kind()) {
            case FROZEN -> c.nextCheckAt = now.plus(Duration.ofMinutes(30));
            case ROBOTS -> c.nextCheckAt = now.plus(Duration.ofDays(1));
            default -> {
                c.errorStreak++;
                long minutes = Math.min(360, 15L << Math.min(c.errorStreak, 5));
                c.nextCheckAt = now.plus(Duration.ofMinutes(minutes));
            }
        }
    }
}
