package finance.tools;

import java.util.*;

/** Registry of concrete creators; ToolManager asks it for a tool by type and never names a concrete tool class. */
public class ToolFactory {
    private final Map<String, ToolCreator> creators = new LinkedHashMap<>();

    public ToolFactory() {
        register("categorize", new ToolCreator() { @Override protected Tool factoryMethod() { return new CategorizerTool(); } });
        register("trend", new ToolCreator() { @Override protected Tool factoryMethod() { return new TrendAnalysisTool(); } });
        register("unusual", new ToolCreator() { @Override protected Tool factoryMethod() { return new UnusualExpenseTool(); } });
        register("recurring", new ToolCreator() { @Override protected Tool factoryMethod() { return new RecurringPaymentTool(); } });
        register("interpret", new ToolCreator() { @Override protected Tool factoryMethod() { return new QueryInterpreterTool(); } });
        register("answer", new ToolCreator() { @Override protected Tool factoryMethod() { return new QueryTool(QueryTool.Mode.ANSWER); } });
        register("summary", new ToolCreator() { @Override protected Tool factoryMethod() { return new QueryTool(QueryTool.Mode.SUMMARY); } });
        register("suggest", new ToolCreator() { @Override protected Tool factoryMethod() { return new QueryTool(QueryTool.Mode.SUGGEST); } });
    }

    public void register(String type, ToolCreator c) { creators.put(type, c); }

    public Tool createTool(String type) {
        ToolCreator c = creators.get(type);
        if (c == null) throw new IllegalArgumentException("Unknown tool type: " + type);
        return c.newTool();
    }

    public Set<String> types() { return creators.keySet(); }
}
