package io.github.guyor.jobscanner.http;

import java.io.IOException;

/** A fetch that did not produce a usable answer. The kind says how the caller should react. */
public class AtsException extends IOException {

    public enum Kind {
        /** The host is frozen after repeated failures; do not touch it until it thaws. */
        FROZEN,
        /** 403/429: the site pushed back. We slow down and never work around it. */
        BLOCKED,
        /** robots.txt forbids this path. */
        ROBOTS,
        /** Network error, 5xx, or unparseable body. */
        ERROR
    }

    private final Kind kind;

    public AtsException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
