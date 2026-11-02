package finance;

import com.sun.net.httpserver.HttpServer;
import finance.llm.Prompt;
import finance.util.Json;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local stand-in for the Anthropic Messages API (backed by FakeLLM) to exercise the real HTTP path offline. Dev/verification only. */
public class StubAnthropicServer {
    public static void main(String[] a) throws Exception {
        int port = a.length > 0 ? Integer.parseInt(a[0]) : 8089;
        FakeLLM fake = new FakeLLM();
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        s.createContext("/v1/messages", ex -> {
            Map<String, Object> req = Json.asMap(Json.parse(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String user = String.valueOf(Json.asMap(Json.asList(req.get("messages")).get(0)).get("content"));
            String text;
            try { text = fake.generate(new Prompt(String.valueOf(req.get("system")), user)); } catch (Exception e) { text = "[]"; }
            byte[] b = Json.write(Map.of("content", List.of(Map.of("type", "text", "text", text)), "usage", Map.of("input_tokens", user.length() / 4, "output_tokens", text.length() / 4))).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close();
        });
        s.start();
        System.out.println("stub listening on " + port);
    }
}
