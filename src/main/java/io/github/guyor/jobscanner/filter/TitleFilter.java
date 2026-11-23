package io.github.guyor.jobscanner.filter;

import java.util.List;
import java.util.Map;

/** Decides whether a job title is one Guy wants. See config/filters.yml for the rules. */
public final class TitleFilter {

    public enum Verdict { MATCH, NEAR_MISS, NO }

    public record Result(Verdict verdict, String seniority) {
    }

    private final FilterConfig cfg;

    public TitleFilter(FilterConfig cfg) {
        this.cfg = cfg;
    }

    public Result evaluate(String title) {
        List<String> tokens = Text.tokens(title);
        if (tokens.isEmpty() || anyIn(tokens, cfg.exclude)) {
            return new Result(Verdict.NO, null);
        }
        boolean both = anyIn(tokens, cfg.both);
        boolean field = both || anyGroup(tokens, cfg.field);
        boolean role = both || anyGroup(tokens, cfg.role);
        String seniority = seniority(tokens);
        if (field && role) {
            return new Result(Verdict.MATCH, seniority);
        }
        if (field || role) {
            // Only a field word ("Java Specialist") or only a role word ("Engineer") is worth a human look
            // when the other half is missing but the title is not obviously unrelated.
            return new Result(field ? Verdict.NEAR_MISS : Verdict.NO, seniority);
        }
        return new Result(Verdict.NO, null);
    }

    private static boolean anyIn(List<String> tokens, List<List<String>> phrases) {
        for (List<String> p : phrases) {
            if (Text.indexOf(tokens, p) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyGroup(List<String> tokens, Map<String, List<List<String>>> groups) {
        for (List<List<String>> g : groups.values()) {
            if (anyIn(tokens, g)) {
                return true;
            }
        }
        return false;
    }

    /** The seniority word that appears first in the title, or null. */
    private String seniority(List<String> tokens) {
        String best = null;
        int bestPos = Integer.MAX_VALUE;
        for (Map.Entry<String, List<List<String>>> e : cfg.seniority.entrySet()) {
            for (List<String> p : e.getValue()) {
                int i = Text.indexOf(tokens, p);
                if (i >= 0 && i < bestPos) {
                    bestPos = i;
                    best = e.getKey();
                }
            }
        }
        return best;
    }
}
