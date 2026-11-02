package finance.agent;

import java.util.*;

public class Response {
    public final boolean ok;
    public final String text;
    public final List<String> trace;      // plan steps that actually ran (shows the agent's multi-step behaviour)
    public final List<String> details = new ArrayList<>();
    public final Map<String, Object> data = new LinkedHashMap<>();

    public Response(boolean ok, String text, List<String> trace) { this.ok = ok; this.text = text; this.trace = trace; }

    @Override public String toString() {
        StringBuilder b = new StringBuilder(text);
        for (String d : details) b.append("\n  - ").append(d);
        return b.toString();
    }
}
