package finance.agent;

import java.util.*;

public class Request {
    public enum Type { CATEGORIZE, ANALYZE, ASK, SUMMARY, SUGGEST, TRACK_BUDGET, GOALS, EXPORT }
    public final Type type;
    public final Map<String, Object> params = new HashMap<>();
    public Request(Type t) { type = t; }
    public Request with(String k, Object v) { params.put(k, v); return this; }
    @SuppressWarnings("unchecked") public <T> T get(String k) { return (T) params.get(k); }
}
