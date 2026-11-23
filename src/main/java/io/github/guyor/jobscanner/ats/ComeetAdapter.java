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

/**
 * Comeet has no open API without a token, so this reads the public career page
 * (https://www.comeet.com/jobs/{name}/{uid}) and takes the positions list the page embeds as
 * {@code COMPANY_POSITIONS_DATA = [...]}. Slug is "{name}/{uid}". robots.txt is enforced by the HTTP layer.
 * Gate 1 of the plan: confirm from GitHub Actions that this page is served and the list is present.
 */
public class ComeetAdapter implements AtsAdapter {

    static final String MARKER = "COMPANY_POSITIONS_DATA";

    private final Fetcher http;

    public ComeetAdapter(Fetcher http) {
        this.http = http;
    }

    @Override
    public String ats() {
        return "comeet";
    }

    @Override
    public Optional<List<RawJob>> fetchJobs(Company c) throws IOException, InterruptedException {
        String url = "https://www.comeet.com/jobs/" + c.slug;
        Fetcher.Response res = http.get(url);
        if (res.status() == 404) {
            return Optional.empty();
        }
        String array = extractArray(res.body());
        if (array == null) {
            throw new AtsException(AtsException.Kind.ERROR, "No " + MARKER + " in " + url);
        }
        JsonNode positions = Json.parse(array, url);
        List<RawJob> out = new ArrayList<>();
        for (JsonNode p : positions) {
            String id = Json.text(p, "uid");
            String name = Json.text(p, "name");
            if (id == null || name == null) {
                continue;
            }
            JsonNode loc = p.get("location");
            String location = null;
            if (loc != null && loc.isObject()) {
                location = Json.text(loc, "name");
                if (location == null) {
                    String city = Json.text(loc, "city");
                    String country = Json.text(loc, "country");
                    location = city == null ? country : country == null ? city : city + ", " + country;
                }
            }
            String link = Json.text(p, "url_active_page");
            if (link == null) {
                link = Json.text(p, "url_comeet_hosted_page");
            }
            // Comeet exposes only time_updated, which is not a publish date, so publishedAt stays unknown.
            out.add(new RawJob(id, name, location, link, null));
        }
        return Optional.of(out);
    }

    /** The JSON array literal that follows {@code COMPANY_POSITIONS_DATA =}, or null. */
    static String extractArray(String html) {
        int m = html.indexOf(MARKER);
        if (m < 0) {
            return null;
        }
        int start = html.indexOf('[', m);
        int eq = html.indexOf('=', m);
        if (start < 0 || eq < 0 || start < eq) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < html.length(); i++) {
            char ch = html.charAt(i);
            if (inString) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']' && --depth == 0) {
                return html.substring(start, i + 1);
            }
        }
        return null;
    }
}
