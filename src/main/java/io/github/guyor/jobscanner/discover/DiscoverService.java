package io.github.guyor.jobscanner.discover;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.guyor.jobscanner.state.JsonStore;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Weekly discovery: gathers candidates and keeps them in candidates.json (the "candidates" branch). */
public class DiscoverService {

    public static final String FILE = "candidates.json";

    private final CommonCrawlSource commonCrawl;

    public DiscoverService(CommonCrawlSource commonCrawl) {
        this.commonCrawl = commonCrawl;
    }

    /** Merges newly found candidates into the file; existing ones stay. Returns how many are new. */
    public int run(Path dir, Duration budget) throws IOException, InterruptedException {
        Path file = dir.resolve(FILE);
        List<Candidate> known = JsonStore.read(file, new TypeReference<List<Candidate>>() { }, new ArrayList<>());
        Map<String, Candidate> all = new LinkedHashMap<>();
        known.forEach(c -> all.put(c.id(), c));
        int before = all.size();
        commonCrawl.collect(budget).forEach(all::putIfAbsent);
        List<Candidate> sorted = new ArrayList<>(all.values());
        sorted.sort(Comparator.comparing(Candidate::id));
        JsonStore.write(file, sorted);
        return all.size() - before;
    }

    public static List<Candidate> load(Path dir) throws IOException {
        return JsonStore.read(dir.resolve(FILE), new TypeReference<List<Candidate>>() { }, new ArrayList<>());
    }
}
