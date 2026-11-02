package finance.llm;

import finance.util.Config;
import finance.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Concrete strategy: Anthropic Messages API over java.net.http. The key comes from LLM_API_KEY (.env), never source code. */
public class ClaudeProvider implements LLMProvider {
    private final String apiKey, model, baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public ClaudeProvider(String apiKey, String model, String baseUrl) { this.apiKey = apiKey; this.model = model; this.baseUrl = baseUrl; }

    public static ClaudeProvider fromConfig(Config c) {
        return new ClaudeProvider(c.get("LLM_API_KEY", ""), c.get("LLM_MODEL", "claude-haiku-5-5"), c.get("LLM_BASE_URL", "https://api.anthropic.com"));
    }

    @Override public String name() { return "Claude (" + model + ")"; }

    @Override public String generate(Prompt p) throws LLMException {
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) throw new LLMException("No LLM API key configured. Copy .env.example to .env and set LLM_API_KEY.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1500);
        body.put("system", p.system);
        body.put("messages", List.of(Map.of("role", "user", "content", p.user)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
        HttpResponse<String> resp;
        try { resp = http.send(req, HttpResponse.BodyHandlers.ofString()); }
        catch (IOException e) { throw new LLMException("LLM service unreachable: " + e.getMessage(), e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new LLMException("Interrupted", e); }
        if (resp.statusCode() / 100 != 2) throw new LLMException("LLM error HTTP " + resp.statusCode() + ": " + shorten(resp.body()));
        try {
            Map<String, Object> m = Json.asMap(Json.parse(resp.body()));
            StringBuilder out = new StringBuilder();
            for (Object blk : Json.asList(m.get("content"))) {
                Map<String, Object> b = Json.asMap(blk);
                if ("text".equals(b.get("type"))) out.append(b.get("text"));
            }
            Object usage = m.get("usage");
            if (usage instanceof Map) {
                Map<String, Object> u = Json.asMap(usage);
                UsageTracker.inputTokens.addAndGet(((Number) u.getOrDefault("input_tokens", 0.0)).longValue());
                UsageTracker.outputTokens.addAndGet(((Number) u.getOrDefault("output_tokens", 0.0)).longValue());
            }
            UsageTracker.calls.incrementAndGet();
            return out.toString();
        } catch (RuntimeException e) { throw new LLMException("Unexpected LLM response format", e); }
    }

    private static String shorten(String s) { return s.length() > 200 ? s.substring(0, 200) + "..." : s; }
}
