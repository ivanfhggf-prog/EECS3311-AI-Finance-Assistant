package finance.agent;

import finance.llm.*;
import finance.tools.*;

/** Gets a tool from the factory, builds the prompt, calls the LLM through the Strategy interface, hands the response to the tool. */
public class ToolManager {
    private final ToolFactory factory;
    private final PromptBuilder promptBuilder;
    private LLMProvider llm;

    public ToolManager(ToolFactory factory, PromptBuilder pb, LLMProvider llm) { this.factory = factory; this.promptBuilder = pb; this.llm = llm; }

    public void setProvider(LLMProvider p) { this.llm = p; }
    public LLMProvider getProvider() { return llm; }

    public ToolResult executeTool(String type, ToolContext ctx) {
        Tool tool = factory.createTool(type);
        Preparation p = tool.prepare(ctx, promptBuilder);
        if (!p.needsLlm()) return p.immediate;
        String response;
        try { response = llm.generate(p.prompt); }
        catch (LLMException e) { return tool.onLlmFailure(ctx, e.getMessage()); }
        ToolResult r = tool.execute(ctx, response);
        r.data.put("llm", llm.name());
        return r;
    }
}
