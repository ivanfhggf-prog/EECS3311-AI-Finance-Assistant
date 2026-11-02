package finance.tools;

import finance.llm.Prompt;

/** A tool's decision: either it already has an answer (deterministic) or it needs the LLM with this prompt. */
public class Preparation {
    public final Prompt prompt;
    public final ToolResult immediate;
    private Preparation(Prompt p, ToolResult r) { prompt = p; immediate = r; }
    public static Preparation needsLlm(Prompt p) { return new Preparation(p, null); }
    public static Preparation done(ToolResult r) { return new Preparation(null, r); }
    public boolean needsLlm() { return prompt != null; }
}
