package finance.app;

import finance.agent.*;
import finance.command.*;
import finance.data.*;
import finance.domain.*;
import finance.llm.*;
import finance.tools.ToolFactory;
import finance.util.Config;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Facade: the single API used by both FinanceGUI and FinanceCLI. Hides controller, planner, tools and LLM wiring. */
public class AgentFacade {
    private final AgentController controller;

    public AgentFacade(AgentController c) { this.controller = c; }

    /** Normal wiring: Claude behind a retrying decorator. */
    public static AgentFacade create(Config config) { return create(new RetryingLLMProvider(ClaudeProvider.fromConfig(config), 3, 800), LocalDate.now()); }

    public static AgentFacade create(LLMProvider provider, LocalDate today) {
        TransactionRepository repo = new InMemoryTransactionRepository();
        MemoryManager mem = new MemoryManager();
        ToolManager tm = new ToolManager(new ToolFactory(), new PromptBuilder(), provider);
        return new AgentFacade(new AgentController(repo, mem, new Planner(), tm, today));
    }

    public AgentController controller() { return controller; }

    // F01
    public CommandResult importTransactions(Path file) { return controller.handle(new ImportTransactionsCommand(file, controller.repo())); }
    // F02
    public Response categorizeAll() { return controller.processRequest(new Request(Request.Type.CATEGORIZE)); }
    // F03
    public CommandResult correctCategory(String txId, String category) { return controller.handle(new CorrectCategoryCommand(txId, category, controller.repo(), controller.memory())); }
    // F04
    public CommandResult createBudget(YearMonth month, Map<Category, BigDecimal> limits) { return controller.handle(new CreateBudgetCommand(month, limits, controller.repo(), controller.observers())); }
    // F05
    public Response trackBudget(YearMonth month) { return controller.processRequest(new Request(Request.Type.TRACK_BUDGET).with("month", month)); }
    // F06-F08: type = trend | unusual | recurring
    public Response analyzeSpending(String type) { return controller.processRequest(new Request(Request.Type.ANALYZE).with("type", type)); }
    // F09
    public Response askQuery(String text) { return controller.processRequest(new Request(Request.Type.ASK).with("question", text)); }
    // F10
    public Response generateSummary(YearMonth month) { return controller.processRequest(new Request(Request.Type.SUMMARY).with("month", month)); }
    // F11
    public Response suggestSavings() { return controller.processRequest(new Request(Request.Type.SUGGEST)); }
    // F12
    public CommandResult saveGoal(String name, BigDecimal target, LocalDate date, BigDecimal contribution) {
        return controller.handle(new SaveGoalCommand(name, target, date, contribution, controller.today(), controller.repo()));
    }
    public Response goalsStatus() { return controller.processRequest(new Request(Request.Type.GOALS)); }
    // F13: type = transactions | summary, format = csv | txt | pdf
    public Response exportReport(LocalDate from, LocalDate to, String type, String format, Path out) {
        return controller.processRequest(new Request(Request.Type.EXPORT).with("from", from).with("to", to).with("type", type).with("format", format).with("out", out));
    }

    // helpers used by both UIs
    public List<Transaction> transactions() { return controller.repo().findAll(); }
    public String dismissFlag(String id) { return controller.dismissFlag(id); }
    public String rejectMerge(String a, String b) { return controller.rejectMerge(a, b); }
    public String undo() { return controller.undo(); }
    public List<String> commandLog() { return controller.history().log(); }
    public void addBudgetObserver(BudgetObserver o) { controller.addBudgetObserver(o); }
    public String llmName() { return controller.tools().getProvider().name(); }
    public String usage() { return UsageTracker.summary(); }
}
