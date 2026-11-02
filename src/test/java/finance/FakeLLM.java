package finance;

import finance.llm.*;
import finance.util.Json;

import java.util.*;

/**
 * Test double for the LLMProvider Strategy: a deterministic keyword-based "model" used by tests and by
 * offline demos of the pipeline. It is NOT part of the shipped application.
 */
public class FakeLLM implements LLMProvider {
    public final List<String> prompts = new ArrayList<>();
    public boolean failing;
    public String forcedResponse;

    @Override public String name() { return "FakeLLM"; }

    @Override public String generate(Prompt p) throws LLMException {
        prompts.add(p.system + "\n" + p.user);
        if (failing) throw new LLMException("simulated outage");
        if (forcedResponse != null) return forcedResponse;
        String task = p.system.substring(p.system.indexOf("Task type: ") + 11);
        String data = p.user.substring(p.user.indexOf("DATA:\n") + 6);
        if (task.startsWith("CategorizerTool")) return categorize(data);
        if (task.startsWith("QueryInterpreterTool")) return interpret(data);
        if (task.contains("[unusual]")) return unusual(data);
        if (task.contains("[recurring]")) return "[]";
        return "Narrative for " + task + " (" + data.split("\n").length + " data lines).";
    }

    private String categorize(String data) {
        List<Object> out = new ArrayList<>();
        String[] lines = data.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split("\\|");
            String m = f[1].toLowerCase(), c; double conf = 0.95;
            if (m.contains("loblaws") || m.contains("metro") || m.contains("no frills")) c = "GROCERIES";
            else if (m.contains("starbucks") || m.contains("pizza") || m.contains("sushi") || m.contains("tim")) c = "DINING";
            else if (m.contains("property")) c = "RENT";
            else if (m.contains("payroll")) c = "INCOME";
            else if (m.contains("netflix") || m.contains("spotify")) c = "SUBSCRIPTIONS";
            else if (m.contains("rogers") || m.contains("hydro")) c = "UTILITIES";
            else if (m.contains("ttc") || m.contains("uber")) c = "TRANSPORT";
            else if (m.contains("best buy") || m.contains("bauer")) c = "SHOPPING";
            else if (m.contains("shoppers")) c = "HEALTH";
            else if (m.contains("cineplex")) c = "ENTERTAINMENT";
            else if (m.contains("savings")) c = "SAVINGS";
            else if (m.contains("mystery")) { c = "GARBAGE_CATEGORY"; }
            else if (m.contains("unsure")) { c = "OTHER"; conf = 0.2; }
            else c = "OTHER";
            out.add(new LinkedHashMap<>(Map.of("id", f[0], "category", c, "confidence", conf)));
        }
        return Json.write(out);
    }

    private String interpret(String data) {
        String q = data.substring(data.lastIndexOf("question: ") + 10).toLowerCase();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", q.contains("october") ? "2026-10" : q.contains("september") ? "2026-09" : q.contains("december") ? "2027-12" : null);
        m.put("category", q.contains("food") || q.contains("dining") ? "DINING" : q.contains("grocer") ? "GROCERIES" : null);
        m.put("clarification", q.trim().equals("how much?") ? "Which category or month do you mean?" : null);
        return Json.write(m);
    }

    private String unusual(String data) {
        List<Object> out = new ArrayList<>();
        String[] lines = data.split("\n");
        for (int i = 1; i < lines.length; i++) out.add(new LinkedHashMap<>(Map.of("id", lines[i].split("\\|")[0], "unusual", true, "reason", "Noticeably above your usual spend here.")));
        return Json.write(out);
    }
}
