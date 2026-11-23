package io.github.guyor.jobscanner.ats;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.http.Fetcher;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.RawJob;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** https://api.lever.co/v0/postings/{site}?mode=json (EU sites: api.eu.lever.co, slug prefixed "eu:"). */
public class LeverAdapter implements AtsAdapter {

    private final Fetcher http;

    public LeverAdapter(Fetcher http) {
        this.http = http;
    }

    @Override
    public String ats() {
        return "lever";
    }

    @Override
    public Optional<List<RawJob>> fetchJobs(Company c) throws IOException, InterruptedException {
        boolean eu = c.slug.startsWith("eu:");
        String site = eu ? c.slug.substring(3) : c.slug;
        String url = "https://" + (eu ? "api.eu.lever.co" : "api.lever.co") + "/v0/postings/" + site + "?mode=json";
        Fetcher.Response res = http.get(url);
        if (res.status() == 404) {
            return Optional.empty();
        }
        JsonNode root = Json.parse(res.body(), url);
        if (!root.isArray()) {
            throw new AtsException(AtsException.Kind.ERROR, "Expected an array from " + url);
        }
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root) {
            String id = Json.text(j, "id");
            String title = Json.text(j, "text");
            if (id == null || title == null) {
                continue;
            }
            JsonNode cat = j.get("categories");
            String loc = cat == null ? null : Json.text(cat, "location");
            JsonNode created = j.get("createdAt");
            Instant at = created != null && created.canConvertToLong() ? Instant.ofEpochMilli(created.asLong()) : null;
            out.add(new RawJob(id, title, loc, Json.text(j, "hostedUrl"), at));
        }
        return Optional.of(out);
    }
}
