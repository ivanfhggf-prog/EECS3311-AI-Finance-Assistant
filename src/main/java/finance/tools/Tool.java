package finance.tools;

import finance.agent.PromptBuilder;

/** Product interface of the Factory Method pattern. Tools hold no network code (dependency inversion). */
public interface Tool {
    String name();
    /** Runs any deterministic part and, if needed, returns the prompt for the LLM. */
    Preparation prepare(ToolContext ctx, PromptBuilder pb);
    /** Interprets the LLM response (never null when prepare() asked for the LLM). */
    ToolResult execute(ToolContext ctx, String llmResponse);
    /** Fallback when the LLM could not be reached; default: report the problem, fabricate nothing. */
    default ToolResult onLlmFailure(ToolContext ctx, String error) { return ToolResult.fail("The AI service is unavailable (" + error + "). No answer was generated."); }
}
