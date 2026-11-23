package io.github.guyor.jobscanner.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guyor.jobscanner.model.HostState;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PoliteHttpTest {

    final Map<String, HostState> states = new HashMap<>();
    final List<String> requested = new ArrayList<>();
    final List<String> userAgents = new ArrayList<>();
    final Map<String, PoliteHttp.Transport.Raw> responses = new HashMap<>();

    PoliteHttp http() {
        HostPacer pacer = new HostPacer(states, Clock.systemUTC(), ms -> { }, new java.util.Random());
        pacer.setBase("www.comeet.com", 0);
        pacer.setBase("boards-api.greenhouse.io", 0);
        return new PoliteHttp((url, ua) -> {
            requested.add(url);
            userAgents.add(ua);
            return responses.getOrDefault(url, new PoliteHttp.Transport.Raw(404, "", Optional.empty()));
        }, pacer, "job-scanner/0.1 (+https://example.org)");
    }

    @Test
    void sendsIdentifyingUserAgent() throws Exception {
        responses.put("https://boards-api.greenhouse.io/v1/boards/x/jobs", new PoliteHttp.Transport.Raw(200, "{}", Optional.empty()));
        http().get("https://boards-api.greenhouse.io/v1/boards/x/jobs");
        assertThat(userAgents).containsExactly("job-scanner/0.1 (+https://example.org)");
    }

    @Test
    void htmlPagesRespectRobots() throws Exception {
        responses.put("https://www.comeet.com/robots.txt",
                new PoliteHttp.Transport.Raw(200, "User-agent: *\nDisallow: /jobs/secret\n", Optional.empty()));
        responses.put("https://www.comeet.com/jobs/ok/1", new PoliteHttp.Transport.Raw(200, "hi", Optional.empty()));
        PoliteHttp h = http();
        assertThat(h.get("https://www.comeet.com/jobs/ok/1").status()).isEqualTo(200);
        assertThatThrownBy(() -> h.get("https://www.comeet.com/jobs/secret/1"))
                .isInstanceOfSatisfying(AtsException.class, e -> assertThat(e.kind()).isEqualTo(AtsException.Kind.ROBOTS));
        assertThat(requested).doesNotContain("https://www.comeet.com/jobs/secret/1");
    }

    @Test
    void apiHostsDoNotFetchRobots() throws Exception {
        http().get("https://boards-api.greenhouse.io/v1/boards/x/jobs");
        assertThat(requested).doesNotContain("https://boards-api.greenhouse.io/robots.txt");
    }

    @Test
    void blockedIsNotRetriedAndFreezesAfterRepeats() {
        String url = "https://boards-api.greenhouse.io/v1/boards/x/jobs";
        responses.put(url, new PoliteHttp.Transport.Raw(429, "", Optional.of("30")));
        PoliteHttp h = http();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> h.get(url)).isInstanceOfSatisfying(AtsException.class,
                    e -> assertThat(e.kind()).isEqualTo(AtsException.Kind.BLOCKED));
        }
        assertThat(requested).hasSize(3); // exactly one attempt per call, no retry loop
        assertThatThrownBy(() -> h.get(url)).isInstanceOfSatisfying(AtsException.class,
                e -> assertThat(e.kind()).isEqualTo(AtsException.Kind.FROZEN));
        assertThat(requested).hasSize(3); // frozen: nothing sent
    }

    @Test
    void serverErrorsCountAsFailures() {
        String url = "https://boards-api.greenhouse.io/v1/boards/x/jobs";
        responses.put(url, new PoliteHttp.Transport.Raw(503, "", Optional.empty()));
        assertThatThrownBy(() -> http().get(url)).isInstanceOfSatisfying(AtsException.class,
                e -> assertThat(e.kind()).isEqualTo(AtsException.Kind.ERROR));
        assertThat(states.get("boards-api.greenhouse.io").errorStreak).isEqualTo(1);
    }
}
