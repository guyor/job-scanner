package io.github.guyor.jobscanner.scan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.state.State;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CompanySeedsTest {

    @Test
    void addsNewCompaniesOnceAndKeepsExistingOnes() throws Exception {
        State s = new State();
        int first = CompanySeeds.apply(s, Path.of("config/companies.yml"));
        assertThat(first).isGreaterThan(10);
        Company c = s.companies.get("comeet:coralogix/06.004");
        assertThat(c.name).isEqualTo("Coralogix");
        assertThat(c.discoveredAt).isEqualTo(Instant.EPOCH);
        c.status = Company.ACTIVE;
        assertThat(CompanySeeds.apply(s, Path.of("config/companies.yml"))).isZero();
        assertThat(s.companies.get("comeet:coralogix/06.004").status).isEqualTo(Company.ACTIVE);
    }
}
