package io.github.guyor.jobscanner.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.filter.TitleFilter.Verdict;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TitleFilterTest {

    static TitleFilter titles;
    static LocationFilter locations;

    @BeforeAll
    static void load() throws Exception {
        FilterConfig cfg = FilterConfig.load(Path.of("config/filters.yml"));
        titles = new TitleFilter(cfg);
        locations = new LocationFilter(cfg);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Senior Backend Engineer", "AI Engineer", "Backend AI Engineer",
            "Senior Back-end Developer", "Back end Engineer", "SW Engineer", "Sr. SWE",
            "Java Developer", "Software Engineer (Backend)", "SDE II", "mid-level backend dev",
            "BACKEND ENGINEER - Alerting", "Software Engineer, Platform"})
    void matches(String title) {
        assertThat(titles.evaluate(title).verdict()).isEqualTo(Verdict.MATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Backend Team Lead", "Staff Engineer - Office of the CTO", "Principal Software Engineer",
            "Software Architect", "Backend Engineering Manager", "Software Engineer in Test",
            "QA Engineer", "SDET", "Sales Engineer", "Solutions Engineer", "Software Engineer Intern",
            "Account Executive, AI Sales", "Product Designer", "Director of Data"})
    void rejects(String title) {
        assertThat(titles.evaluate(title).verdict()).isNotEqualTo(Verdict.MATCH);
    }

    @Test
    void wholeWordsOnly() {
        // "ai" must not match inside "Retail", "dev" not inside "Devops", "eng" not inside "Engage"
        assertThat(titles.evaluate("Retail Developer").verdict()).isNotEqualTo(Verdict.MATCH);
        assertThat(titles.evaluate("Devops Specialist").verdict()).isEqualTo(Verdict.NO);
    }

    @Test
    void nearMissesHaveOneHalf() {
        assertThat(titles.evaluate("Java Specialist").verdict()).isEqualTo(Verdict.NEAR_MISS);
        assertThat(titles.evaluate("Chef de Cuisine").verdict()).isEqualTo(Verdict.NO);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Junior Backend Engineer|junior", "Jr. Java Developer|junior",
            "Mid-Level Software Engineer|mid", "Mid lvl SW Engineer|mid",
            "Sr Backend Engineer|senior", "Experienced Backend Engineer|experienced"})
    void seniorityTag(String title, String tag) {
        assertThat(titles.evaluate(title).seniority()).isEqualTo(tag);
    }

    @Test
    void missingSeniorityStillMatches() {
        var r = titles.evaluate("Backend Engineer");
        assertThat(r.verdict()).isEqualTo(Verdict.MATCH);
        assertThat(r.seniority()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tel Aviv", "Tel-Aviv, Israel", "Ramat Gan (Hybrid)", "Herzliya",
            "Ra'anana, Israel (IL)", "Israel - TLV is fine israel", "ישראל", ""})
    void inIsrael(String loc) {
        assertThat(locations.inIsrael(loc)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"New York, NY", "London", "Remote, Europe", "Toronto"})
    void notInIsrael(String loc) {
        assertThat(locations.inIsrael(loc)).isFalse();
    }
}
