package io.github.guyor.jobscanner.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tiny robots.txt reader: Disallow/Allow prefixes for "*" and for our own agent name, longest match wins. */
public final class Robots {

    private final List<String[]> rules = new ArrayList<>(); // {"allow"|"disallow", prefix}

    private Robots() {
    }

    public static Robots allowAll() {
        return new Robots();
    }

    public static Robots parse(String text, String agentName) {
        Robots r = new Robots();
        String agent = agentName.toLowerCase(Locale.ROOT);
        boolean applies = false;
        boolean lastWasAgent = false;
        for (String raw : text.split("\\R")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "user-agent" -> {
                    String v = value.toLowerCase(Locale.ROOT);
                    boolean match = v.equals("*") || agent.contains(v);
                    applies = lastWasAgent ? (applies || match) : match;
                    lastWasAgent = true;
                    continue;
                }
                case "allow" -> {
                    if (applies && !value.isEmpty()) {
                        r.rules.add(new String[] {"allow", value});
                    }
                }
                case "disallow" -> {
                    if (applies && !value.isEmpty()) {
                        r.rules.add(new String[] {"disallow", value});
                    }
                }
                default -> { }
            }
            lastWasAgent = false;
        }
        return r;
    }

    public boolean allows(String path) {
        String best = null;
        boolean allowed = true;
        for (String[] rule : rules) {
            if (path.startsWith(rule[1]) && (best == null || rule[1].length() >= best.length())) {
                best = rule[1];
                allowed = rule[0].equals("allow");
            }
        }
        return allowed;
    }
}
