package io.github.guyor.jobscanner.discover;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.http.Fetcher;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommonCrawlSourceTest {

    final List<String> requested = new ArrayList<>();

    Fetcher fake() {
        return url -> {
            requested.add(url);
            if (url.endsWith("/collinfo.json")) {
                return new Fetcher.Response(200, "[{\"id\":\"CC-MAIN-2026-40\"},{\"id\":\"CC-MAIN-2026-33\"}]");
            }
            if (url.contains("url=jobs.lever.co/a*")) {
                return new Fetcher.Response(200, "{\"url\": \"https://jobs.lever.co/acme/1\"}\n{\"url\": \"https://jobs.lever.co/acme/2\"}\n{\"url\": \"https://jobs.lever.co/aaa/9\"}\n{\"url\": \"https://jobs.lever.co/cut");
            }
            if (url.contains("url=www.comeet.com/jobs/c*")) {
                return new Fetcher.Response(200, "{\"url\": \"https://www.comeet.com/jobs/coralogix/06.004\"}");
            }
            return new Fetcher.Response(404, "");
        };
    }

    @Test
    void collectsDedupedCandidatesAndUsesNewestCrawl() throws Exception {
        var found = new CommonCrawlSource(fake(), Clock.systemUTC()).collect(Duration.ofMinutes(5));
        assertThat(found.keySet()).containsExactlyInAnyOrder("lever:acme", "lever:aaa", "comeet:coralogix/06.004");
        assertThat(requested).anyMatch(u -> u.contains("/CC-MAIN-2026-40-index?"));
        assertThat(requested).noneMatch(u -> u.contains("CC-MAIN-2026-33"));
    }

    @Test
    void failedShardsAreSkippedButFrozenStopsTheRun() throws Exception {
        int[] calls = {0};
        Fetcher flaky = url -> {
            if (url.endsWith("/collinfo.json")) {
                return new Fetcher.Response(200, "[{\"id\":\"CC-MAIN-2026-40\"}]");
            }
            calls[0]++;
            if (calls[0] == 1) {
                throw new AtsException(AtsException.Kind.ERROR, "503");
            }
            throw new AtsException(AtsException.Kind.FROZEN, "frozen");
        };
        var found = new CommonCrawlSource(flaky, Clock.systemUTC()).collect(Duration.ofMinutes(5));
        assertThat(found).isEmpty();
        assertThat(calls[0]).isEqualTo(2); // one failure skipped, then the freeze ends the run
    }

    @Test
    void zeroBudgetQueriesNothing() throws Exception {
        var found = new CommonCrawlSource(fake(), Clock.systemUTC()).collect(Duration.ZERO);
        assertThat(found).isEmpty();
        assertThat(requested).noneMatch(u -> u.contains("-index?"));
    }

    @Test
    void discoverServiceKeepsKnownCandidatesAndAddsNew(@TempDir Path dir) throws Exception {
        DiscoverService s = new DiscoverService(new CommonCrawlSource(fake(), Clock.systemUTC()));
        assertThat(s.run(dir, Duration.ofMinutes(5))).isEqualTo(3);
        assertThat(s.run(dir, Duration.ofMinutes(5))).isZero();
        assertThat(DiscoverService.load(dir)).extracting(Candidate::id)
                .containsExactly("comeet:coralogix/06.004", "lever:aaa", "lever:acme");
    }
}
