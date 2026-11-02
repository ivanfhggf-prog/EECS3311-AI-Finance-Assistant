package finance.tools;

import finance.agent.PromptBuilder;
import finance.domain.Category;
import finance.util.Json;

import java.time.YearMonth;
import java.util.Map;

/** First step of a natural-language query: the LLM turns the question into a structured filter or asks to clarify. */
public class QueryInterpreterTool implements Tool {
    @Override public String name() { return "QueryInterpreterTool"; }

    @Override public Preparation prepare(ToolContext ctx, PromptBuilder pb) {
        String d = "today: " + ctx.today + "\nmonths with data: " + ctx.history.stream().map(t -> t.getMonth().toString()).distinct().sorted().toList()
                + "\nrecent conversation:\n" + ctx.memory.retrieve() + "\nquestion: " + ctx.question;
        return Preparation.needsLlm(pb.buildJsonPrompt(name(),
                "Translate the question into a filter. Return JSON {\"month\":\"YYYY-MM\" or null,\"category\":one of [" + Category.listForPrompt()
                        + "] or null,\"clarification\":null or one short question if the question cannot be answered without it}. "
                        + "Use the conversation to resolve references like 'that month'. If no month is given, month is null (all data).", d));
    }

    @Override public ToolResult execute(ToolContext ctx, String resp) {
        try {
            Map<String, Object> m = Json.asMap(Json.parseLenient(resp));
            ToolResult r = ToolResult.ok("interpreted");
            Object cl = m.get("clarification");
            if (cl != null && !String.valueOf(cl).isBlank() && !"null".equals(String.valueOf(cl))) r.with("clarification", String.valueOf(cl));
            Object mo = m.get("month");
            if (mo != null && !"null".equals(String.valueOf(mo))) r.with("month", YearMonth.parse(String.valueOf(mo)));
            Category c = m.get("category") == null ? null : Category.parse(String.valueOf(m.get("category")));
            if (c != null) r.with("category", c);
            return r;
        } catch (RuntimeException e) {
            return ToolResult.fail("Could not interpret the question; please rephrase it.");
        }
    }
}
