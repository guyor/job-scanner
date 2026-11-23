package io.github.guyor.jobscanner.state;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.HostState;
import io.github.guyor.jobscanner.model.Job;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Everything the scanner remembers between runs; each part lives in its own JSON file under state/. */
public final class State {

    public final Map<String, Company> companies = new LinkedHashMap<>();
    public final Map<String, Job> jobs = new LinkedHashMap<>();
    public final Map<String, HostState> hosts = new TreeMap<>();
    /** Titles that had only half of a match, for periodic human review. Key = job key. */
    public final Map<String, NearMiss> nearMisses = new TreeMap<>();

    public record NearMiss(String company, String title, String location, String url) {
    }

    public static State load(Path dir) throws IOException {
        State s = new State();
        List<Company> cs = JsonStore.read(dir.resolve("companies.json"), new TypeReference<List<Company>>() { }, new ArrayList<>());
        cs.forEach(c -> s.companies.put(c.id(), c));
        List<Job> js = JsonStore.read(dir.resolve("jobs.json"), new TypeReference<List<Job>>() { }, new ArrayList<>());
        js.forEach(j -> s.jobs.put(j.key, j));
        s.hosts.putAll(JsonStore.read(dir.resolve("hosts.json"), new TypeReference<Map<String, HostState>>() { }, Map.of()));
        s.nearMisses.putAll(JsonStore.read(dir.resolve("near-misses.json"), new TypeReference<Map<String, NearMiss>>() { }, Map.of()));
        return s;
    }

    public void save(Path dir) throws IOException {
        JsonStore.write(dir.resolve("companies.json"), new ArrayList<>(companies.values()));
        JsonStore.write(dir.resolve("jobs.json"), new ArrayList<>(jobs.values()));
        JsonStore.write(dir.resolve("hosts.json"), hosts);
        JsonStore.write(dir.resolve("near-misses.json"), nearMisses);
    }
}
