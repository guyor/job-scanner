package io.github.guyor.jobscanner.ats;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.guyor.jobscanner.http.AtsException;
import io.github.guyor.jobscanner.state.JsonStore;
import java.time.Instant;
import java.time.OffsetDateTime;

final class Json {

    private Json() {
    }

    static JsonNode parse(String body, String what) throws AtsException {
        try {
            return JsonStore.MAPPER.readTree(body);
        } catch (Exception e) {
            throw new AtsException(AtsException.Kind.ERROR, "Unparseable JSON from " + what);
        }
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    static Instant iso(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
