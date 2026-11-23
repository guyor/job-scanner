package io.github.guyor.jobscanner.model;

import java.time.Instant;

/** A job exactly as an ATS reports it, before any filtering. publishedAt may be null. */
public record RawJob(String id, String title, String location, String url, Instant publishedAt) {
}
