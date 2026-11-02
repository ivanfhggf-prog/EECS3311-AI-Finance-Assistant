package finance.tools;

import finance.agent.PromptBuilder;
import finance.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

/** F06 trends (AI narrative over deterministic aggregates). */
public class TrendAnalysisTool implements Tool {
    @Override public String name() { return "TrendAnalysisTool[trend]"; }

    @Override public Preparation prepare(ToolContext ctx, PromptBuilder pb) {
        Map<YearMonth, Map<Category, BigDecimal>> m = SpendingStats.monthlyByCategory(ctx.history);
        if (m.size() < 2) return Preparation.done(ToolResult.fail("Need at least 2 months of history for a trend; found " + m.size() + ". Import more data."));
        StringBuilder d = new StringBuilder("month|category|total_spent\n");
        for (Map.Entry<YearMonth, Map<Category, BigDecimal>> e : m.entrySet())
            for (Map.Entry<Category, BigDecimal> c : e.getValue().entrySet()) d.append(e.getKey()).append('|').append(c.getKey()).append('|').append(c.getValue()).append('\n');
        return Preparation.needsLlm(pb.buildPrompt(name(),
                "Describe the main spending trends month over month in 4-6 short sentences. Mention the biggest increases and decreases by category with the numbers.", d.toString()));
    }

    @Override public ToolResult execute(ToolContext ctx, String resp) {
        Map<YearMonth, Map<Category, BigDecimal>> m = SpendingStats.monthlyByCategory(ctx.history);
        List<String> chart = new ArrayList<>();
        for (Map.Entry<YearMonth, Map<Category, BigDecimal>> e : m.entrySet()) {
            BigDecimal tot = e.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            chart.add(e.getKey() + ": $" + tot);
        }
        return ToolResult.ok(resp.trim()).with("chart", chart).with("monthly", m);
    }
}
