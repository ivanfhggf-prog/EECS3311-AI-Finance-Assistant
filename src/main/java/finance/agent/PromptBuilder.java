package finance.agent;

import finance.llm.Prompt;

/** Builds prompts so tools and managers never assemble raw strings (separation of concerns). */
public class PromptBuilder {
    private static final String BASE = "You are the reasoning component of a personal finance assistant. "
            + "Use ONLY the data provided. Never invent transactions or figures. If the data is insufficient, say so. "
            + "Amounts: negative = money spent, positive = money received. Currency is dollars.";

    public Prompt buildPrompt(String toolName, String task, String data) {
        return new Prompt(BASE + " Task type: " + toolName + ".", task + "\n\nDATA:\n" + data);
    }

    public Prompt buildJsonPrompt(String toolName, String task, String data) {
        return new Prompt(BASE + " Task type: " + toolName + ". Respond with valid JSON only, no prose and no code fences.", task + "\n\nDATA:\n" + data);
    }
}
