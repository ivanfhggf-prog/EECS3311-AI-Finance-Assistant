package finance.agent;

import finance.command.*;
import finance.data.TransactionRepository;
import finance.domain.*;
import finance.report.ReportGenerator;
import finance.tools.Assignment;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Coordinates the agent. State-changing actions arrive as Commands (handle); AI/read requests go through processRequest. */
public class AgentController {
    private final TransactionRepository repo;
    private final MemoryManager memory;
    private final Planner planner;
    private final ToolManager tools;
    private final ReportGenerator reports = new ReportGenerator();
    private final CommandHistory history = new CommandHistory();
    private final List<BudgetObserver> observers = new ArrayList<>();
    private final LocalDate today;

    public AgentController(TransactionRepository repo, MemoryManager memory, Planner planner, ToolManager tools, LocalDate today) {
        this.repo = repo; this.memory = memory; this.planner = planner; this.tools = tools; this.today = today;
    }

    public TransactionRepository repo() { return repo; }
    public MemoryManager memory() { return memory; }
    public ToolManager tools() { return tools; }
    public CommandHistory history() { return history; }
    public List<BudgetObserver> observers() { return observers; }
    public LocalDate today() { return today; }

    public void addBudgetObserver(BudgetObserver o) {
        observers.add(o);
        for (YearMonth m : budgetMonths()) repo.findBudget(m).ifPresent(b -> b.addObserver(o));
    }

    private List<YearMonth> budgetMonths() {
        List<YearMonth> l = new ArrayList<>();
        for (YearMonth m : repo.months()) if (repo.findBudget(m).isPresent()) l.add(m);
        return l;
    }

    // ---- commands -----------------------------------------------------------------------------------------
    public CommandResult handle(Command c) {
        CommandResult r = c.execute();
        history.record(c, r.ok);
        if (r.ok) refreshAllBudgets();
        return r;
    }

    public String undo() { String s = history.undoLast(); refreshAllBudgets(); return s; }

    private void refreshAllBudgets() {
        // budgets for months without transactions are tracked too (zero spending)
        Set<YearMonth> ms = new TreeSet<>(repo.months());
        for (YearMonth m : new ArrayList<>(ms)) if (repo.findBudget(m).isPresent()) trackBudget(m);
    }

    // ---- requests -----------------------------------------------------------------------------------------
    public Response processRequest(Request r) {
        switch (r.type) {
            case TRACK_BUDGET: return trackBudget((YearMonth) r.get("month"));
            case GOALS: return goalsStatus();
            case EXPORT: return export(r);
            default: break;
        }
        PlanContext pc = new PlanContext(r, repo, memory, tools, today);
        Plan plan = planner.decompose(r, pc);
        Response resp;
        try { resp = planner.executePlan(plan, pc); }
        catch (RuntimeException e) { return new Response(false, "Request failed: " + e.getMessage(), pc.trace); }
        if (r.type == Request.Type.CATEGORIZE && resp.data.get("assignments") != null) {
            @SuppressWarnings("unchecked") List<Assignment> list = (List<Assignment>) resp.data.get("assignments");
            for (Assignment a : list) repo.findById(a.txId).ifPresent(t -> { t.setCategory(a.category, a.needsReview); repo.save(t); });
            refreshAllBudgets();
        }
        if (r.type == Request.Type.SUMMARY && resp.ok) {
            YearMonth m = r.get("month");
            boolean partial = Boolean.TRUE.equals(resp.data.get("partial"));
            return withData(new Response(true, reports.formatSummary(m, repo.findByMonth(m), repo.findBudget(m).orElse(null), resp.text, partial), resp.trace), resp);
        }
        return resp;
    }

    private Response withData(Response to, Response from) { to.data.putAll(from.data); to.details.addAll(from.details); return to; }

    public Response trackBudget(YearMonth month) {
        Optional<Budget> ob = repo.findBudget(month);
        if (ob.isEmpty()) return new Response(false, "No budget set for " + month + ". Create one first (budget command).", List.of());
        Budget b = ob.get();
        Map<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        for (Transaction t : repo.findByMonth(month)) if (t.isExpense() && t.getCategory().isSpending()) totals.merge(t.getCategory(), t.spent(), BigDecimal::add);
        b.updateSpending(totals);
        Response r = new Response(true, "Budget " + month + " (total limit $" + b.totalLimit() + ")", List.of("track budget"));
        b.getLimits().forEach((c, lim) -> r.details.add(String.format("%-14s spent $%s of $%s, remaining $%s%s", c, b.getSpent(c), lim, b.getRemaining(c), b.isOver(c) ? "  OVER BUDGET" : "")));
        Set<Category> unbudgeted = new TreeSet<>(totals.keySet()); unbudgeted.removeAll(b.getLimits().keySet());
        for (Category c : unbudgeted) r.details.add(String.format("%-14s spent $%s (no limit set)", c, totals.get(c)));
        return r;
    }

    public Response goalsStatus() {
        if (repo.goals().isEmpty()) return new Response(true, "No savings goals yet.", List.of());
        Response r = new Response(true, "Savings goals", List.of());
        for (SavingsGoal g : repo.goals()) {
            BigDecimal fromTx = savedFromTransactions(g);
            r.details.add(String.format("%s: %.1f%% of $%s by %s (contributed $%s + $%s from SAVINGS transactions)", g.getName(), g.progressPercent(fromTx), g.getTarget(), g.getTargetDate(), g.getContributed(), fromTx));
        }
        return r;
    }

    public BigDecimal savedFromTransactions(SavingsGoal g) {
        BigDecimal s = BigDecimal.ZERO;
        for (Transaction t : repo.findAll()) if (t.getCategory() == Category.SAVINGS && !t.getDate().isBefore(g.getCreated())) s = s.add(t.getAmount().abs());
        return s;
    }

    private Response export(Request r) {
        LocalDate from = r.get("from"), to = r.get("to");
        String format = r.get("format"), type = r.get("type");
        Path out = r.get("out");
        List<Transaction> rows = new ArrayList<>();
        for (Transaction t : repo.findAll()) if (!t.getDate().isBefore(from) && !t.getDate().isAfter(to)) rows.add(t);
        if (rows.isEmpty()) return new Response(false, "No transactions between " + from + " and " + to + "; no file was created.", List.of());
        if (!Set.of("csv", "txt", "pdf").contains(format.toLowerCase())) return new Response(false, "Unsupported format '" + format + "' (use csv, txt or pdf).", List.of());
        String content;
        if ("summary".equals(type)) {
            YearMonth m = YearMonth.from(from);
            Response s = processRequest(new Request(Request.Type.SUMMARY).with("month", m));
            if (!s.ok) return s;
            content = s.text;
        } else content = format.equalsIgnoreCase("csv") ? reports.transactionsCsv(rows) : reports.transactionsText(rows, from, to);
        try { reports.export(content, out, format); }
        catch (IOException e) { return new Response(false, "Could not write " + out + ": " + e.getMessage(), List.of()); }
        return new Response(true, "Exported " + rows.size() + " transaction(s) to " + out, List.of());
    }

    // ---- small deterministic actions ---------------------------------------------------------------------------------
    public String dismissFlag(String txId) {
        Optional<Transaction> t = repo.findById(txId);
        if (t.isEmpty()) return "Transaction not found: " + txId;
        t.get().dismissFlag();
        memory.dismissMerchant(t.get().getMerchant());
        return "Flag dismissed for " + t.get().getMerchant() + "; similar charges will need a bigger deviation to be flagged.";
    }

    public String rejectMerge(String a, String b) { memory.rejectMerge(a, b); return "Merge of '" + a + "' and '" + b + "' rejected; they will stay separate."; }
}
