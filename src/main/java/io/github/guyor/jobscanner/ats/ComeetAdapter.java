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
            String link = firstText(p, "urlActivePage", "urlComeetHostedPage", "url_active_page", "url_comeet_hosted_page");
            // Comeet exposes only time_updated, which is not a publish date, so publishedAt stays unknown.
            out.add(new RawJob(id, name, location, link, null));
        }
        return Optional.of(out);
    }

    private static final java.util.regex.Pattern ASSIGNMENT =
            java.util.regex.Pattern.compile(MARKER + "\\s*=\\s*\\[");

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            String v = Json.text(n, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * The JSON array literal assigned as {@code COMPANY_POSITIONS_DATA = [...]}, or null. The page also
     * mentions the name in a bare declaration ("var COMPANY_POSITIONS_DATA;") first, which must be skipped.
     */
    static String extractArray(String html) {
        java.util.regex.Matcher m = ASSIGNMENT.matcher(html);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
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
