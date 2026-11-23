package io.github.guyor.jobscanner.http;

import java.io.IOException;

/** Minimal GET abstraction so adapters can be tested with canned responses. */
public interface Fetcher {

    record Response(int status, String body) {
    }

    /** Returns 2xx and 404 responses; throws {@link AtsException} for blocks, freezes and failures. */
    Response get(String url) throws IOException, InterruptedException;
}
