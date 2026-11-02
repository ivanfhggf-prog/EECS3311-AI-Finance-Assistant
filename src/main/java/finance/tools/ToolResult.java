package finance.tools;

import java.util.*;

public class ToolResult {
    public final boolean ok;
    public final String text;
    public final Map<String, Object> data = new LinkedHashMap<>();
    public final List<String> notes = new ArrayList<>();

    public ToolResult(boolean ok, String text) { this.ok = ok; this.text = text; }
    public static ToolResult ok(String t) { return new ToolResult(true, t); }
    public static ToolResult fail(String t) { return new ToolResult(false, t); }
    public ToolResult with(String k, Object v) { data.put(k, v); return this; }
}
