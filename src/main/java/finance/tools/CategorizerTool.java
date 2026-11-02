package finance.tools;

import finance.agent.PromptBuilder;
import finance.domain.*;
import finance.util.Json;

import java.util.*;

/** F02: remembered corrections first (deterministic), the LLM for the rest; bad/low-confidence answers go to review. */
public class CategorizerTool implements Tool {
    static final double MIN_CONFIDENCE = 0.6;
    private final List<Assignment> pre = new ArrayList<>();

    @Override public String name() { return "CategorizerTool"; }

    @Override public Preparation prepare(ToolContext ctx, PromptBuilder pb) {
        pre.clear();
        List<Transaction> rest = new ArrayList<>();
        for (Transaction t : ctx.transactions) {
            Optional<Category> m = ctx.memory.lookup(t.getMerchant());
            if (m.isPresent()) pre.add(new Assignment(t.getId(), m.get(), false, "memory")); else rest.add(t);
        }
        if (rest.isEmpty()) return Preparation.done(result(ctx, pre, "All matched user corrections from memory; no AI call needed."));
        StringBuilder d = new StringBuilder("id|merchant|description|amount\n");
        for (Transaction t : rest) d.append(t.getId()).append('|').append(t.getMerchant()).append('|').append(t.getDescription()).append('|').append(t.getAmount().toPlainString()).append('\n');
        return Preparation.needsLlm(pb.buildJsonPrompt(name(),
                "Assign each transaction exactly one category from: " + Category.listForPrompt() + ". "
                        + "Return a JSON array: [{\"id\":\"T0001\",\"category\":\"DINING\",\"confidence\":0.0-1.0}] with one entry per id.", d.toString()));
    }

    @Override public ToolResult execute(ToolContext ctx, String resp) {
        List<Assignment> out = new ArrayList<>(pre);
        Set<String> seen = new HashSet<>();
        Set<String> want = new HashSet<>();
        for (Transaction t : ctx.transactions) want.add(t.getId());
        for (Assignment a : pre) seen.add(a.txId);
        try {
            for (Object o : Json.asList(Json.parseLenient(resp))) {
                Map<String, Object> m = Json.asMap(o);
                String id = String.valueOf(m.get("id"));
                if (!want.contains(id) || !seen.add(id)) continue;
                Category c = Category.parse(String.valueOf(m.get("category")));
                double conf = m.get("confidence") instanceof Number ? ((Number) m.get("confidence")).doubleValue() : 0;
                if (c == null || c == Category.UNCATEGORIZED || conf < MIN_CONFIDENCE) out.add(new Assignment(id, Category.UNCATEGORIZED, true, "ai-rejected"));
                else out.add(new Assignment(id, c, false, "ai"));
            }
        } catch (RuntimeException e) {
            return ToolResult.fail("AI returned an unreadable categorisation response; transactions left uncategorised for review.");
        }
        for (String id : want) if (seen.add(id)) out.add(new Assignment(id, Category.UNCATEGORIZED, true, "ai-missing"));
        return result(ctx, out, null);
    }

    private ToolResult result(ToolContext ctx, List<Assignment> list, String note) {
        long review = list.stream().filter(a -> a.needsReview).count();
        ToolResult r = ToolResult.ok(String.format("Categorised %d transaction(s); %d need manual review.", list.size() - review, review));
        r.with("assignments", list);
        if (note != null) r.notes.add(note);
        return r;
    }

    @Override public ToolResult onLlmFailure(ToolContext ctx, String error) {
        ToolResult r = result(ctx, pre, "AI unavailable (" + error + "); only remembered corrections were applied.");
        return r;
    }
}
