package io.github.guyor.jobscanner.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The only way the scanner touches the network. Identifies itself, paces requests per host, backs off
 * and freezes on pushback, and checks robots.txt for HTML pages. It never retries around a block.
 */
public class PoliteHttp implements Fetcher {

    /** Raw transport, replaceable in tests. */
    public interface Transport {
        record Raw(int status, String body, Optional<String> retryAfter) {
        }

        Raw get(String url, String userAgent) throws IOException, InterruptedException;
    }

    public static Transport javaTransport() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        return (url, ua) -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", ua)
                    .header("Accept", "application/json, text/html;q=0.8")
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new Raw(res.statusCode(), res.body(), res.headers().firstValue("Retry-After"));
        };
    }

    private final Transport transport;
    private final HostPacer pacer;
    private final String userAgent;
    private final String agentName;
    private final Map<String, Robots> robots = new HashMap<>();

    public PoliteHttp(Transport transport, HostPacer pacer, String userAgent) {
        this.transport = transport;
        this.pacer = pacer;
        this.userAgent = userAgent;
        this.agentName = userAgent.contains("/") ? userAgent.substring(0, userAgent.indexOf('/')) : userAgent;
    }

    @Override
    public Response get(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (pacer.isFrozen(host)) {
            throw new AtsException(AtsException.Kind.FROZEN, host + " is frozen");
        }
        if (isHtmlPage(uri) && !robotsFor(uri).allows(uri.getRawPath() == null ? "/" : uri.getRawPath())) {
            throw new AtsException(AtsException.Kind.ROBOTS, "robots.txt disallows " + url);
        }
        return request(url, host);
    }

    private Response request(String url, String host) throws IOException, InterruptedException {
        pacer.await(host);
        Transport.Raw raw;
        try {
            raw = transport.get(url, userAgent);
        } catch (IOException e) {
            pacer.failure(host);
            throw new AtsException(AtsException.Kind.ERROR, "I/O error for " + url + ": " + e.getMessage());
        }
        int s = raw.status();
        if (s >= 200 && s < 300 || s == 404) {
            pacer.success(host);
            return new Response(s, raw.body());
        }
        if (s == 403 || s == 429) {
            pacer.throttled(host, parseRetryAfter(raw.retryAfter()));
            throw new AtsException(AtsException.Kind.BLOCKED, "HTTP " + s + " from " + host);
        }
        pacer.failure(host);
        throw new AtsException(AtsException.Kind.ERROR, "HTTP " + s + " from " + url);
    }

    private Robots robotsFor(URI uri) throws IOException, InterruptedException {
        String host = uri.getHost();
        Robots cached = robots.get(host);
        if (cached != null) {
            return cached;
        }
        Robots r;
        try {
            Response res = request(uri.getScheme() + "://" + host + "/robots.txt", host);
            r = res.status() == 200 ? Robots.parse(res.body(), agentName) : Robots.allowAll();
        } catch (AtsException e) {
            if (e.kind() == AtsException.Kind.BLOCKED) {
                throw e;
            }
            r = Robots.allowAll();
        }
        robots.put(host, r);
        return r;
    }

    private static boolean isHtmlPage(URI uri) {
        String h = uri.getHost();
        return !(h.startsWith("api.") || h.startsWith("boards-api."));
    }

    static Duration parseRetryAfter(Optional<String> v) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(v.get().trim()));
        } catch (NumberFormatException e) {
            return null; // HTTP-date form is not worth parsing; the doubling gap already applies
        }
    }
}
