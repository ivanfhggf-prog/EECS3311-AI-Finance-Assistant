package finance.tools;

import finance.agent.PromptBuilder;
import finance.domain.*;
import finance.util.Json;

import java.util.*;
import java.util.stream.Collectors;

/**
 * F08 hybrid: group by normalised merchant and test for regular interval + stable amount (deterministic).
 * Different-but-similar merchant names (e.g. "Spotify" / "Spotify AB") are only merged if the LLM confirms; the user can reject a merge.
 */
public class RecurringPaymentTool implements Tool {
    private Map<String, List<Transaction>> groups = new TreeMap<>();
    private final List<String[]> candidates = new ArrayList<>();

    @Override public String name() { return "TrendAnalysisTool[recurring]"; }

    @Override public Preparation prepare(ToolContext ctx, PromptBuilder pb) {
        groups = new TreeMap<>();
        candidates.clear();
        for (Transaction t : ctx.history) if (t.isExpense()) groups.computeIfAbsent(SpendingStats.normalizeMerchant(t.getMerchant()), k -> new ArrayList<>()).add(t);
        List<String> keys = new ArrayList<>(groups.keySet());
        for (int i = 0; i < keys.size(); i++) for (int j = i + 1; j < keys.size(); j++) {
            String a = keys.get(i), b = keys.get(j);
            if (a.length() >= 5 && b.length() >= 5 && (b.startsWith(a) || a.startsWith(b))
                    && !ctx.memory.isMergeRejected(a, b)) candidates.add(new String[]{a, b});
        }
        if (candidates.isEmpty()) return Preparation.done(build(ctx, List.of()));
        StringBuilder d = new StringBuilder("pair|merchant_a_examples|merchant_b_examples\n");
        for (String[] c : candidates) d.append(c[0]).append('+').append(c[1]).append('|').append(example(c[0])).append('|').append(example(c[1])).append('\n');
        return Preparation.needsLlm(pb.buildJsonPrompt(name(),
                "For each pair decide if both merchant names are the same business (e.g. billing-name variants). Return JSON [{\"a\":\"...\",\"b\":\"...\",\"same\":true|false}] using the normalised names exactly.", d.toString()));
    }

    private String example(String key) { return groups.get(key).get(0).getMerchant(); }

    @Override public ToolResult execute(ToolContext ctx, String resp) {
        List<String[]> merges = new ArrayList<>();
        try {
            for (Object o : Json.asList(Json.parseLenient(resp))) {
                Map<String, Object> m = Json.asMap(o);
                if (Boolean.TRUE.equals(m.get("same"))) merges.add(new String[]{String.valueOf(m.get("a")), String.valueOf(m.get("b"))});
            }
        } catch (RuntimeException e) {
            ToolResult r = build(ctx, List.of());
            r.notes.add("AI merge suggestions were unreadable; similar merchant names were not merged.");
            return r;
        }
        merges.removeIf(p -> candidates.stream().noneMatch(c -> c[0].equals(p[0]) && c[1].equals(p[1])));
        return build(ctx, merges);
    }

    @Override public ToolResult onLlmFailure(ToolContext ctx, String error) {
        ToolResult r = build(ctx, List.of());
        r.notes.add("AI unavailable (" + error + "); similar merchant names were not merged.");
        return r;
    }

    private ToolResult build(ToolContext ctx, List<String[]> merges) {
        Map<String, List<Transaction>> g = new TreeMap<>();
        for (Map.Entry<String, List<Transaction>> e : groups.entrySet()) g.put(e.getKey(), new ArrayList<>(e.getValue()));
        List<String> mergeNotes = new ArrayList<>();
        for (String[] p : merges) {
            if (!g.containsKey(p[0]) || !g.containsKey(p[1])) continue;
            g.get(p[0]).addAll(g.remove(p[1]));
            mergeNotes.add(p[0] + " + " + p[1]);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> e : g.entrySet()) {
            String f = SpendingStats.frequency(e.getValue());
            if (f == null) continue;
            List<Transaction> s = e.getValue().stream().sorted(Comparator.comparing(Transaction::getDate)).collect(Collectors.toList());
            Transaction last = s.get(s.size() - 1);
            lines.add(String.format("%s: %s, about $%s each, %d charges, last on %s", s.get(0).getMerchant(), f, last.spent(), s.size(), last.getDate()));
        }
        ToolResult r = ToolResult.ok(lines.isEmpty() ? "No recurring payments found." : lines.size() + " recurring payment(s) found.");
        r.with("recurring", lines).with("merges", mergeNotes);
        for (String m : mergeNotes) r.notes.add("Merged (AI-suggested, reject with 'reject-merge'): " + m);
        return r;
    }
}
