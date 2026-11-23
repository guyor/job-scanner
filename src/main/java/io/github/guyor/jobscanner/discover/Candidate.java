package io.github.guyor.jobscanner.discover;

/** A company career page found by discovery, not yet checked for Israel jobs. */
public record Candidate(String ats, String slug, String name, String source) {

    public String id() {
        return ats + ":" + slug;
    }
}
