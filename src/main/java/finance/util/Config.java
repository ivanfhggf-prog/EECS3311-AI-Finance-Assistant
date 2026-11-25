package finance.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Loads settings from environment variables, then a local .env file (never committed). */
public final class Config {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> overrides = new HashMap<>();   // command-line options win over everything

    /** Sets a value from a command-line option (for example -model); it takes priority over the environment and .env. */
    public Config set(String key, String value) { overrides.put(key, value); return this; }

    public static Config load() { return load(Paths.get(".env")); }

    public static Config load(Path envFile) {
        Config c = new Config();
        if (Files.isReadable(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                    int k = line.indexOf('=');
                    String v = line.substring(k + 1).trim();
                    if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
                    c.values.put(line.substring(0, k).trim(), v);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + envFile, e);
            }
        }
        return c;
    }

    public String get(String key, String dflt) {
        String o = overrides.get(key);
        if (o != null && !o.isBlank()) return o;
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        String v = values.get(key);
        return v == null || v.isBlank() ? dflt : v;
    }

    public boolean hasRealKey() {
        String k = get("LLM_API_KEY", "");
        return !k.isBlank() && !k.startsWith("YOUR_");
    }
}
