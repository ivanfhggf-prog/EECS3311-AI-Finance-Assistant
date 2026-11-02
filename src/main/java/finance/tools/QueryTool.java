package finance.tools;

import finance.agent.PromptBuilder;
import finance.domain.*;

import java.math.BigDecimal;
import java.util.*;

/** Context-grounded generation for F09 (answer), F10 (summary) and F11 (suggestions); mode picks the instruction. */
public class QueryTool implements Tool {
    public enum Mode { ANSWER, SUMMARY, SUGGEST }
    private final Mode mode;

    public QueryTool(Mode mode) { this.mode = mode; }

    @Override public String name() { return "QueryTool[" + mode + "]"; }

    @Override public Preparation prepare(ToolContext ctx, PromptBuilder pb) {
        StringBuilder d = new StringBuilder();
        d.append("transactions (id|date|amount|merchant|category):\n");
        for (Transaction t : ctx.transactions) d.append(t.getId()).append('|').append(t.getDate()).append('|').append(t.getAmount().toPlainString()).append('|').append(t.getMerchant()).append('|').append(t.getCategory()).append('\n');
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        for (Transaction t : ctx.transactions) if (t.getAmount().signum() >= 0) in = in.add(t.getAmount()); else out = out.add(t.spent());
        d.append("computed totals: income=").append(in).append(" spending=").append(out).append('\n');
        if (ctx.budget != null) {
            d.append("budget limits: ");
            ctx.budget.getLimits().forEach((c, v) -> d.append(c).append('=').append(v).append(" (spent ").append(ctx.budget.getSpent(c)).append(") "));
            d.append('\n');
        }
        for (SavingsGoal g : ctx.goals) d.append("goal: ").append(g.getName()).append(" target ").append(g.getTarget()).append(" by ").append(g.getTargetDate()).append(" contributed ").append(g.getContributed()).append('\n');
        String task;
        switch (mode) {
            case SUMMARY: task = "Write a monthly summary in 5-8 sentences: income, spending, top categories, and budget performance (if a budget is given)."
                    + (ctx.partialMonth ? " The month is NOT complete; say that the summary is partial." : ""); break;
            case SUGGEST: task = "Give 3-5 concrete, personalised ways to reduce spending or save more, each citing numbers from the data. Reference goals and budgets if present."; break;
            default: task = "Answer the question using only the data and computed totals. Be brief and state the figures. If the data does not contain the answer, say what is missing.\nPrevious conversation:\n" + ctx.memory.retrieve() + "\nQuestion: " + ctx.question;
        }
        return Preparation.needsLlm(pb.buildPrompt(name(), task, d.toString()));
    }

    @Override public ToolResult execute(ToolContext ctx, String resp) { return ToolResult.ok(resp.trim()); }
}
