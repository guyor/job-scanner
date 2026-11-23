package io.github.guyor.jobscanner.discover;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.http.Fetcher;
import io.github.guyor.jobscanner.state.JsonStore;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds company ids in the Common Crawl URL index: which ATS board URLs exist under each ATS domain.
 * Queries run one after another, sharded by the first character of the path so each answer stays small.
 * Common Crawl is only used to discover names; it never tells us anything about jobs.
 */
public class CommonCrawlSource {

    private static final Logger log = LoggerFactory.getLogger(CommonCrawlSource.class);
    static final String HOST = "https://index.commoncrawl.org";
    static final int LIMIT_PER_QUERY = 10_000;
    static final String SHARDS = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** url prefix to query (the shard character is appended). */
    static final List<String> PREFIXES = List.of(
            "boards.greenhouse.io/", "job-boards.greenhouse.io/", "job-boards.eu.greenhouse.io/",
            "jobs.lever.co/", "jobs.eu.lever.co/", "jobs.ashbyhq.com/", "www.comeet.com/jobs/");

    private final Fetcher http;
    private final Clock clock;

    public CommonCrawlSource(Fetcher http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    /** The id of the newest crawl, e.g. CC-MAIN-2026-40. */
    public String latestCrawl() throws IOException, InterruptedException {
        Fetcher.Response res = http.get(HOST + "/collinfo.json");
        if (res.status() != 200) {
            throw new AtsException(AtsException.Kind.ERROR, "collinfo.json: HTTP " + res.status());
        }
        JsonNode root = JsonStore.MAPPER.readTree(res.body());
        if (!root.isArray() || root.isEmpty() || !root.get(0).hasNonNull("id")) {
            throw new AtsException(AtsException.Kind.ERROR, "collinfo.json has no crawls");
        }
        return root.get(0).get("id").asText();
    }

    /**
     * Collects candidates until the shards run out or the budget ends. A failed shard is skipped; when the
     * host gets frozen (repeated failures) the run stops with what it has.
     */
    public Map<String, Candidate> collect(Duration budget) throws InterruptedException {
        Map<String, Candidate> found = new LinkedHashMap<>();
        Instant deadline = clock.instant().plus(budget);
        String crawl;
        try {
            crawl = latestCrawl();
        } catch (IOException e) {
            log.warn("Cannot read the crawl list: {}", e.getMessage());
            return found;
        }
        log.info("Using crawl {}", crawl);
        for (String prefix : PREFIXES) {
            for (char c : SHARDS.toCharArray()) {
                if (!clock.instant().isBefore(deadline)) {
                    log.info("Discovery budget used up, {} candidates", found.size());
                    return found;
                }
                try {
                    int before = found.size();
                    query(crawl, prefix + c + "*", found);
                    log.debug("{}{}*: +{}", prefix, c, found.size() - before);
                } catch (AtsException e) {
                    log.warn("Shard {}{} failed ({}): {}", prefix, c, e.kind(), e.getMessage());
                    if (e.kind() == AtsException.Kind.FROZEN) {
                        return found;
                    }
                } catch (IOException e) {
                    log.warn("Shard {}{} failed: {}", prefix, c, e.getMessage());
                }
            }
        }
        return found;
    }

    private void query(String crawl, String pattern, Map<String, Candidate> into) throws IOException, InterruptedException {
        String url = HOST + "/" + crawl + "-index?url=" + pattern + "&output=json&fl=url&filter=status:200&limit=" + LIMIT_PER_QUERY;
        Fetcher.Response res = http.get(url);
        if (res.status() != 200) {
            return; // 404 means no captures for this shard
        }
        for (String line : res.body().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode n;
            try {
                n = JsonStore.MAPPER.readTree(line);
            } catch (IOException e) {
                continue; // a half line at the end of a cut-off answer
            }
            String u = n.hasNonNull("url") ? n.get("url").asText() : null;
            if (u != null) {
                SlugExtractor.extract(u, "common-crawl").ifPresent(c -> into.putIfAbsent(c.id(), c));
            }
        }
    }
}
