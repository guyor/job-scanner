package io.github.guyor.jobscanner.ats;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.http.Fetcher;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.RawJob;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptersTest {

    static String fixture(String name) throws IOException {
        try (var in = AdaptersTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Fetcher serving(String body) {
        return url -> new Fetcher.Response(200, body);
    }

    static Company company(String ats, String slug) {
        return new Company(ats, slug, slug, "test", Instant.EPOCH);
    }

    @Test
    void greenhouse() throws Exception {
        var jobs = new GreenhouseAdapter(serving(fixture("greenhouse.json"))).fetchJobs(company("greenhouse", "acme")).orElseThrow();
        assertThat(jobs).hasSize(4);
        RawJob first = jobs.get(0);
        assertThat(first.id()).isEqualTo("8172510");
        assertThat(first.title()).isEqualTo("Senior Backend Engineer");
        assertThat(first.location()).isEqualTo("Tel Aviv");
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-09-09T14:50:29Z"));
        assertThat(jobs.get(2).publishedAt()).isNull();
    }

    @Test
    void greenhouseNotFound() throws Exception {
        var res = new GreenhouseAdapter(url -> new Fetcher.Response(404, "")).fetchJobs(company("greenhouse", "nope"));
        assertThat(res).isEmpty();
    }

    @Test
    void lever() throws Exception {
        var jobs = new LeverAdapter(serving(fixture("lever.json"))).fetchJobs(company("lever", "acme")).orElseThrow();
        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).title()).isEqualTo("Junior Software Engineer");
        assertThat(jobs.get(0).location()).isEqualTo("Tel Aviv, Israel");
        assertThat(jobs.get(0).publishedAt()).isEqualTo(Instant.ofEpochMilli(1790000000000L));
    }

    @Test
    void leverEuUsesEuHost() throws Exception {
        String[] seen = new String[1];
        new LeverAdapter(url -> { seen[0] = url; return new Fetcher.Response(200, "[]"); })
                .fetchJobs(company("lever", "eu:acme"));
        assertThat(seen[0]).startsWith("https://api.eu.lever.co/v0/postings/acme");
    }

    @Test
    void ashbySkipsUnlisted() throws Exception {
        var jobs = new AshbyAdapter(serving(fixture("ashby.json"))).fetchJobs(company("ashby", "acme")).orElseThrow();
        assertThat(jobs).extracting(RawJob::id).containsExactly("u1");
        assertThat(jobs.get(0).publishedAt()).isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
    }

    @Test
    void comeetReadsEmbeddedPositions() throws Exception {
        List<RawJob> jobs = new ComeetAdapter(serving(fixture("comeet.html"))).fetchJobs(company("comeet", "acme/00.001")).orElseThrow();
        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).title()).isEqualTo("Backend Developer [Go]");
        assertThat(jobs.get(0).location()).isEqualTo("Tel Aviv, Israel");
        assertThat(jobs.get(1).title()).isEqualTo("Senior \"Full Stack\" Engineer");
        assertThat(jobs.get(1).location()).isEqualTo("Haifa, IL");
        assertThat(jobs.get(1).url()).endsWith("A1.002");
    }

    @Test
    void comeetWithoutDataIsAnError() {
        org.junit.jupiter.api.Assertions.assertThrows(io.github.guyor.jobscanner.http.AtsException.class,
                () -> new ComeetAdapter(serving("<html></html>")).fetchJobs(company("comeet", "a/b")));
    }
}
