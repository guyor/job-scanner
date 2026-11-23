package io.github.guyor.jobscanner.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RobotsTest {

    @Test
    void longestMatchWinsAndOtherAgentsAreIgnored() {
        Robots r = Robots.parse("""
                User-agent: badbot
                Disallow: /
                User-agent: *
                Disallow: /private
                Allow: /private/public
                """, "job-scanner");
        assertThat(r.allows("/jobs/x")).isTrue();
        assertThat(r.allows("/private/a")).isFalse();
        assertThat(r.allows("/private/public/a")).isTrue();
    }

    @Test
    void ownAgentSectionApplies() {
        Robots r = Robots.parse("User-agent: job-scanner\nDisallow: /\n", "job-scanner");
        assertThat(r.allows("/anything")).isFalse();
    }

    @Test
    void emptyDisallowAllowsEverything() {
        assertThat(Robots.parse("User-agent: *\nDisallow:\n", "job-scanner").allows("/x")).isTrue();
    }
}
