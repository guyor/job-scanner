package io.github.guyor.jobscanner.discover;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugExtractorTest {

    static String id(String url) {
        return SlugExtractor.extract(url, "t").map(Candidate::id).orElse(null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://boards.greenhouse.io/Acme/jobs/123|greenhouse:acme",
            "https://job-boards.greenhouse.io/acme|greenhouse:acme",
            "https://job-boards.eu.greenhouse.io/optimove/jobs/4937887101|greenhouse:optimove",
            "https://boards.greenhouse.io/embed/job_board?for=Acme-Labs|greenhouse:acme-labs",
            "https://jobs.lever.co/acme/0f0c-aaaa|lever:acme",
            "https://jobs.eu.lever.co/acme|lever:eu:acme",
            "https://jobs.ashbyhq.com/Acme-Co/abc|ashby:Acme-Co",
            "https://www.comeet.com/jobs/coralogix/06.004|comeet:coralogix/06.004",
            "https://www.comeet.com/jobs/biocatch/03.00e/senior-dev/AB.123|comeet:biocatch/03.00E"})
    void extracts(String url, String expected) {
        assertThat(id(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://boards.greenhouse.io/", "https://boards.greenhouse.io/embed/job_board",
            "https://jobs.lever.co/", "https://www.comeet.com/jobs/acme", "https://www.comeet.com/about/us/12.345",
            "https://example.com/jobs/acme/06.004", "not a url", "https://jobs.ashbyhq.com/robots.txt", "https://jobs.ashbyhq.com/Acme%20Co/abc"})
    void ignores(String url) {
        assertThat(SlugExtractor.extract(url, "t")).isEqualTo(Optional.empty());
    }

    @Test
    void prettifiesNames() {
        assertThat(SlugExtractor.extract("https://jobs.lever.co/acme-labs/x", "t").orElseThrow().name()).isEqualTo("Acme Labs");
    }
}
