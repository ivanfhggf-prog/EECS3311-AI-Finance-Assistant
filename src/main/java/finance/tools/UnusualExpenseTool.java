package finance.tools;

import finance.agent.PromptBuilder;
import finance.domain.*;
import finance.util.Json;

import java.util.*;

/**
 * F07 hybrid: deterministic ratio vs. category average. >= CLEAR flags outright; between BORDER and CLEAR goes to the LLM
 * for a contextual judgment. Merchants the user dismissed before need a much larger ratio (fewer false positives).
 */
public class UnusualExpenseTool implements Tool {
    static final double CLEAR = 3.0, BORDER = 1.8, DISMISSED_CLEAR = 5.0, FALLBACK_CLEAR = 8.0;
    private final Map<String, Double> ratios = new LinkedHashMap<>();
    private final List<Transaction> clear = new ArrayList<>(), borderline = new ArrayList<>();
    private final Map<String, Double> avgs = new HashMap<>();

    @Override public String name() { return "TrendAnalysisTool[unusual]"; }

    @Override public Preparation prepare(ToolContext ctx, PromptBuilder pb) {
        ratios.clear(); clear.clear(); borderline.clear(); avgs.clear();
        Double fallbackMedian = SpendingStats.fallbackMedian(ctx.history);
        for (Transaction t : ctx.transactions) {
            if (!t.isExpense() || !t.getCategory().isSpending() || t.getCategory() == Category.UNCATEGORIZED || t.isDismissedFlag()) continue;
            Double avg = SpendingStats.categoryAverageExcluding(ctx.history, t);
            boolean fallback = false;
            if (avg == null) { avg = fallbackMedian; fallback = true; }   // too few comparables in the category
            if (avg == null || avg <= 0) continue;
            double r = t.spent().doubleValue() / avg;
            boolean dismissed = ctx.memory.isDismissed(t.getMerchant());
            if (fallback) {
                if (r >= FALLBACK_CLEAR && !dismissed) { ratios.put(t.getId(), r); avgs.put(t.getId(), avg); clear.add(t); }
                continue;
            }
            ratios.put(t.getId(), r); avgs.put(t.getId(), avg);
            if (r >= (dismissed ? DISMISSED_CLEAR : CLEAR)) clear.add(t);
            else if (r >= BORDER && !dismissed) borderline.add(t);
        }
        if (borderline.isEmpty()) return Preparation.done(build(ctx, List.of(), Map.of()));
        StringBuilder d = new StringBuilder("id|merchant|category|amount|category_average|ratio\n");
        for (Transaction t : borderline) d.append(t.getId()).append('|').append(t.getMerchant()).append('|').append(t.getCategory()).append('|')
                .append(t.spent()).append('|').append(String.format("%.2f", avgs.get(t.getId()))).append('|').append(String.format("%.2f", ratios.get(t.getId()))).append('\n');
        return Preparation.needsLlm(pb.buildJsonPrompt(name(),
                "These expenses are somewhat above their category average. Decide for each whether it is genuinely unusual for a person's spending. "
                        + "Return a JSON array [{\"id\":\"...\",\"unusual\":true|false,\"reason\":\"one short sentence\"}].", d.toString()));
    }

    @Override public ToolResult execute(ToolContext ctx, String resp) {
        Map<String, String> reasons = new HashMap<>();
        List<Transaction> flagged = new ArrayList<>();
        try {
            for (Object o : Json.asList(Json.parseLenient(resp))) {
                Map<String, Object> m = Json.asMap(o);
                String id = String.valueOf(m.get("id"));
                if (Boolean.TRUE.equals(m.get("unusual"))) { reasons.put(id, String.valueOf(m.get("reason"))); }
            }
            for (Transaction t : borderline) if (reasons.containsKey(t.getId())) flagged.add(t);
        } catch (RuntimeException e) {
            ToolResult r = build(ctx, List.of(), Map.of());
            r.notes.add("AI judgment for borderline expenses was unreadable; only clear outliers are shown.");
            return r;
        }
        return build(ctx, flagged, reasons);
    }

    @Override public ToolResult onLlmFailure(ToolContext ctx, String error) {
        ToolResult r = build(ctx, List.of(), Map.of());
        r.notes.add("AI unavailable (" + error + "); borderline expenses were not judged, clear outliers only.");
        return r;
    }

    private ToolResult build(ToolContext ctx, List<Transaction> aiFlagged, Map<String, String> reasons) {
        List<String> lines = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Transaction t : clear) {
            lines.add(String.format("%s %s $%s at %s: %.1fx the typical %s expense ($%.2f).", t.getId(), t.getDate(), t.spent(), t.getMerchant(), ratios.get(t.getId()), t.getCategory(), avgs.get(t.getId())));
            ids.add(t.getId());
        }
        for (Transaction t : aiFlagged) {
            lines.add(String.format("%s %s $%s at %s: %.1fx the %s average - %s", t.getId(), t.getDate(), t.spent(), t.getMerchant(), ratios.get(t.getId()), t.getCategory(), reasons.get(t.getId())));
            ids.add(t.getId());
        }
        String text = lines.isEmpty() ? "No unusual expenses found." : lines.size() + " unusual expense(s) found.";
        return ToolResult.ok(text).with("flags", lines).with("flaggedIds", ids);
    }
}
