package finance.agent;

import finance.data.TransactionRepository;
import finance.tools.ToolContext;
import java.time.LocalDate;
import java.util.*;

public class PlanContext {
    public final Request request;
    public final TransactionRepository repo;
    public final MemoryManager memory;
    public final ToolManager tools;
    public final LocalDate today;
    public final ToolContext tc = new ToolContext();
    public final Map<String, Object> scratch = new HashMap<>();
    public final List<String> trace = new ArrayList<>();
    public final Map<String, Object> out = new LinkedHashMap<>();
    public final List<String> notes = new ArrayList<>();

    public PlanContext(Request r, TransactionRepository repo, MemoryManager mem, ToolManager tools, LocalDate today) {
        this.request = r; this.repo = repo; this.memory = mem; this.tools = tools; this.today = today;
        tc.memory = mem; tc.today = today;
    }
}
