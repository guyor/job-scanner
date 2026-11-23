package io.github.guyor.jobscanner.filter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** The parsed config/filters.yml. */
public final class FilterConfig {

    public final Map<String, List<List<String>>> field;
    public final Map<String, List<List<String>>> role;
    public final List<List<String>> both;
    /** Insertion order is the tag order: junior, mid, senior, experienced. */
    public final Map<String, List<List<String>>> seniority;
    public final List<List<String>> exclude;
    public final List<List<String>> locations;
    public final boolean keepUnknownLocation;
    public final List<String> excludedCompanies;

    private FilterConfig(Map<String, Object> root) {
        Map<String, Object> titles = map(root.get("titles"));
        this.field = groups(titles.get("field"));
        this.role = groups(titles.get("role"));
        this.both = phrases(titles.get("both"));
        this.seniority = groups(titles.get("seniority"));
        this.exclude = phrases(titles.get("exclude"));
        Map<String, Object> loc = map(root.get("locations"));
        this.locations = phrases(loc.get("israel"));
        this.keepUnknownLocation = !Boolean.FALSE.equals(loc.get("keep_unknown"));
        Map<String, Object> comp = map(root.get("companies"));
        List<String> ex = new ArrayList<>();
        if (comp.get("exclude") instanceof List<?> l) {
            l.forEach(o -> ex.add(String.valueOf(o).toLowerCase()));
        }
        this.excludedCompanies = ex;
    }

    public static FilterConfig load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    public static FilterConfig parse(InputStream in) {
        Object o = new Yaml().load(in);
        return new FilterConfig(map(o));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    private static List<List<String>> phrases(Object o) {
        List<List<String>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) {
                List<String> t = Text.tokens(String.valueOf(e));
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static Map<String, List<List<String>>> groups(Object o) {
        Map<String, List<List<String>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(o).entrySet()) {
            out.put(e.getKey(), phrases(e.getValue()));
        }
        return out;
    }
}
