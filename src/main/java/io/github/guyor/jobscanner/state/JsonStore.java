package io.github.guyor.jobscanner.state;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes JSON files; writes are atomic (temp file + rename) so a killed run never leaves half a file. */
public final class JsonStore {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private JsonStore() {
    }

    /** Returns the parsed file, or {@code fallback} when it does not exist yet. */
    public static <T> T read(Path file, TypeReference<T> type, T fallback) throws IOException {
        if (!Files.exists(file)) {
            return fallback;
        }
        return MAPPER.readValue(file.toFile(), type);
    }

    public static void write(Path file, Object value) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            MAPPER.writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
