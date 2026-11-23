package io.github.guyor.jobscanner.scan;

import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.state.State;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Adds the companies from config/companies.yml that the state does not know yet. */
public final class CompanySeeds {

    private CompanySeeds() {
    }

    public static int apply(State state, Path file) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return apply(state, in);
        }
    }

    @SuppressWarnings("unchecked")
    public static int apply(State state, InputStream in) {
        Object root = new Yaml().load(in);
        if (!(root instanceof Map<?, ?> m) || !(m.get("companies") instanceof List<?> list)) {
            return 0;
        }
        int added = 0;
        for (Object o : list) {
            Map<String, Object> e = (Map<String, Object>) o;
            String ats = String.valueOf(e.get("ats"));
            String slug = String.valueOf(e.get("slug"));
            String name = e.get("name") == null ? slug : String.valueOf(e.get("name"));
            // EPOCH, not "now": seeds must never count as newly discovered companies in the feed.
            Company c = new Company(ats, slug, name, "seed", Instant.EPOCH);
            if (state.companies.putIfAbsent(c.id(), c) == null) {
                added++;
            }
        }
        return added;
    }
}
