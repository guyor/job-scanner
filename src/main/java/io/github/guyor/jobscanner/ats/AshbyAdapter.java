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

/** https://api.ashbyhq.com/posting-api/job-board/{name} */
public class AshbyAdapter implements AtsAdapter {

    private final Fetcher http;

    public AshbyAdapter(Fetcher http) {
        this.http = http;
    }

    @Override
    public String ats() {
        return "ashby";
    }

    @Override
    public Optional<List<RawJob>> fetchJobs(Company c) throws IOException, InterruptedException {
        String url = "https://api.ashbyhq.com/posting-api/job-board/" + c.slug;
        Fetcher.Response res = http.get(url);
        if (res.status() == 404) {
            return Optional.empty();
        }
        JsonNode jobs = Json.parse(res.body(), url).get("jobs");
        if (jobs == null || !jobs.isArray()) {
            throw new AtsException(AtsException.Kind.ERROR, "No jobs array from " + url);
        }
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : jobs) {
            String id = Json.text(j, "id");
            String title = Json.text(j, "title");
            if (id == null || title == null || (j.has("isListed") && !j.get("isListed").asBoolean(true))) {
                continue;
            }
            out.add(new RawJob(id, title, Json.text(j, "location"), Json.text(j, "jobUrl"),
                    Json.iso(Json.text(j, "publishedAt"))));
        }
        return Optional.of(out);
    }
}
