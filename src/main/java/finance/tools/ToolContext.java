package finance.tools;

import finance.agent.MemoryManager;
import finance.domain.*;
import java.time.LocalDate;
import java.util.*;

/** Everything a tool needs. Tools never touch the repository or the network directly. */
public class ToolContext {
    public List<Transaction> transactions = new ArrayList<>();   // rows relevant to this call
    public List<Transaction> history = new ArrayList<>();        // all rows (for statistics)
    public Budget budget;
    public List<SavingsGoal> goals = new ArrayList<>();
    public MemoryManager memory = new MemoryManager();
    public String question = "";
    public String analysisType = "";
    public LocalDate today = LocalDate.now();
    public boolean partialMonth;
}
