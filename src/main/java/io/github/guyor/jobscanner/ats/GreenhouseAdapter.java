package io.github.guyor.jobscanner.ats;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.http.Fetcher;
import io.github.guyor.jobscanner.model.Company;
import io.github.guyor.jobscanner.model.RawJob;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** https://boards-api.greenhouse.io/v1/boards/{token}/jobs */
public class GreenhouseAdapter implements AtsAdapter {

    private final Fetcher http;

    public GreenhouseAdapter(Fetcher http) {
        this.http = http;
    }

    @Override
    public String ats() {
        return "greenhouse";
    }

    @Override
    public Optional<List<RawJob>> fetchJobs(Company c) throws IOException, InterruptedException {
        String url = "https://boards-api.greenhouse.io/v1/boards/" + c.slug + "/jobs";
        Fetcher.Response res = http.get(url);
        if (res.status() == 404) {
            return Optional.empty();
        }
        JsonNode root = Json.parse(res.body(), url);
        JsonNode jobs = root.get("jobs");
        if (jobs == null || !jobs.isArray()) {
            throw new AtsException(AtsException.Kind.ERROR, "No jobs array from " + url);
        }
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : jobs) {
            String id = Json.text(j, "id");
            String title = Json.text(j, "title");
            if (id == null || title == null) {
                continue;
            }
            JsonNode loc = j.get("location");
            out.add(new RawJob(id, title, loc == null ? null : Json.text(loc, "name"),
                    Json.text(j, "absolute_url"), Json.iso(Json.text(j, "first_published"))));
        }
        return Optional.of(out);
    }
}
