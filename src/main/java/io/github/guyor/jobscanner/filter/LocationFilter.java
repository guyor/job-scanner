package io.github.guyor.jobscanner.filter;

import java.util.List;

/** Keeps jobs located in Israel; an empty location is kept when keep_unknown is set. */
public final class LocationFilter {

    private final FilterConfig cfg;

    public LocationFilter(FilterConfig cfg) {
        this.cfg = cfg;
    }

    public boolean inIsrael(String location) {
        List<String> tokens = Text.tokens(location);
        if (tokens.isEmpty()) {
            return cfg.keepUnknownLocation;
        }
        for (List<String> p : cfg.locations) {
            if (Text.indexOf(tokens, p) >= 0) {
                return true;
            }
        }
        return false;
    }
}
