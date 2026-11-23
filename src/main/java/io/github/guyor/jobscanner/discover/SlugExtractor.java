package io.github.guyor.jobscanner.discover;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns an ATS URL (as found in a crawl index) into a company identifier, or nothing. */
public final class SlugExtractor {

    private static final Set<String> RESERVED = Set.of("embed", "jobs", "job", "api", "v1", "static", "assets",
            "robots.txt", "favicon.ico", "sitemap.xml", "careers", "login", "sign_in", "external", "o", "j");
    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern COMEET_UID = Pattern.compile("[0-9A-Fa-f]{2}\\.[0-9A-Fa-f]{3}");

    private SlugExtractor() {
    }

    public static Optional<Candidate> extract(String url, String source) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String[] parts = segments(uri.getRawPath());
        switch (host) {
            case "boards.greenhouse.io", "job-boards.greenhouse.io", "job-boards.eu.greenhouse.io", "boards.eu.greenhouse.io" -> {
                String slug = first(parts);
                if ((slug == null || slug.equals("embed")) && uri.getRawQuery() != null) {
                    slug = null;
                    Matcher m = Pattern.compile("(?:^|&)for=([^&]+)").matcher(uri.getRawQuery());
                    if (m.find()) {
                        slug = decode(m.group(1));
                    }
                }
                return make("greenhouse", slug == null ? null : slug.toLowerCase(Locale.ROOT), source);
            }
            case "jobs.lever.co" -> {
                return make("lever", first(parts), source);
            }
            case "jobs.eu.lever.co" -> {
                String s = first(parts);
                return make("lever", s == null ? null : "eu:" + s, source);
            }
            case "jobs.ashbyhq.com" -> {
                return make("ashby", first(parts), source);
            }
            case "www.comeet.com", "comeet.com" -> {
                if (parts.length >= 3 && parts[0].equals("jobs") && COMEET_UID.matcher(parts[2]).matches()
                        && SLUG.matcher(parts[1]).matches()) {
                    return Optional.of(new Candidate("comeet", parts[1] + "/" + parts[2].toUpperCase(Locale.ROOT),
                            prettify(parts[1]), source));
                }
                return Optional.empty();
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    private static Optional<Candidate> make(String ats, String slug, String source) {
        if (slug == null) {
            return Optional.empty();
        }
        String bare = slug.startsWith("eu:") ? slug.substring(3) : slug;
        if (!SLUG.matcher(bare).matches() || RESERVED.contains(bare.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(ats, slug, prettify(bare), source));
    }

    private static String first(String[] parts) {
        return parts.length == 0 ? null : parts[0];
    }

    private static String[] segments(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return new String[0];
        }
        String p = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (p.isEmpty()) {
            return new String[0];
        }
        String[] raw = p.split("/");
        for (int i = 0; i < raw.length; i++) {
            raw[i] = decode(raw[i]);
        }
        return raw;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /** "acme-labs" -> "Acme Labs". Good enough as a display name until the board says otherwise. */
    static String prettify(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String w : slug.split("[-_.]+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
