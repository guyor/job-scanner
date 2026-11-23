package io.github.guyor.jobscanner.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Text normalisation shared by the filters. */
final class Text {

    private Text() {
    }

    /** Lowercase and turn everything that is not a letter or digit into a space. */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            } else if (c == '\'' || c == '’') {
                // ra'anana -> raanana
                continue;
            } else if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** True when {@code phrase} occurs in {@code tokens} as a whole-word sequence; returns index or -1. */
    static int indexOf(List<String> tokens, List<String> phrase) {
        if (phrase.isEmpty()) {
            return -1;
        }
        for (int i = 0; i + phrase.size() <= tokens.size(); i++) {
            if (tokens.subList(i, i + phrase.size()).equals(phrase)) {
                return i;
            }
        }
        return -1;
    }
}
