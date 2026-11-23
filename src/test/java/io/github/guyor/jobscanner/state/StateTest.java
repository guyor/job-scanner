package io.github.guyor.jobscanner.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.HostState;
import io.github.guyor.jobscanner.model.Job;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StateTest {

    @Test
    void roundTripsAndLeavesNoTempFiles(@TempDir Path dir) throws Exception {
        State s = new State();
        Company c = new Company("comeet", "acme/00.001", "Acme", "seed", Instant.parse("2026-11-01T00:00:00Z"));
        s.companies.put(c.id(), c);
        Job j = new Job();
        j.key = "comeet:acme/00.001:A1";
        j.companyId = c.id();
        j.title = "Backend Engineer";
        j.firstSeenAt = Instant.parse("2026-11-23T10:00:00Z");
        s.jobs.put(j.key, j);
        HostState hs = new HostState(2000);
        hs.frozenUntil = Instant.parse("2026-11-23T12:00:00Z");
        s.hosts.put("www.comeet.com", hs);
        s.save(dir);

        State back = State.load(dir);
        assertThat(back.companies.get("comeet:acme/00.001").name).isEqualTo("Acme");
        assertThat(back.jobs.get(j.key).firstSeenAt).isEqualTo(j.firstSeenAt);
        assertThat(back.jobs.get(j.key).isOpen()).isTrue();
        assertThat(back.hosts.get("www.comeet.com").frozenUntil).isEqualTo(hs.frozenUntil);
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(".tmp"));
        }
    }

    @Test
    void missingFilesGiveEmptyState(@TempDir Path dir) throws Exception {
        State s = State.load(dir.resolve("nothing-here"));
        assertThat(s.companies).isEmpty();
        assertThat(s.jobs).isEmpty();
    }
}
