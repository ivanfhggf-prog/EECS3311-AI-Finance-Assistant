package finance.command;

import finance.data.TransactionRepository;
import finance.domain.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** F04: validates limits (positive, one budget per month) and creates the Budget. Undoable. */
public class CreateBudgetCommand implements Command {
    private final YearMonth month;
    private final Map<Category, BigDecimal> limits;
    private final TransactionRepository repo;
    private final List<BudgetObserver> observers;
    private Budget created;

    public CreateBudgetCommand(YearMonth month, Map<Category, BigDecimal> limits, TransactionRepository repo, List<BudgetObserver> observers) {
        this.month = month; this.limits = limits; this.repo = repo; this.observers = observers;
    }

    @Override public CommandResult execute() {
        if (limits.isEmpty()) return CommandResult.fail("Provide at least one category limit.");
        for (Map.Entry<Category, BigDecimal> e : limits.entrySet())
            if (e.getValue() == null || e.getValue().signum() <= 0) return CommandResult.fail("Limit for " + e.getKey() + " must be greater than zero.");
        if (repo.findBudget(month).isPresent()) return CommandResult.fail("A budget for " + month + " already exists (duplicate rejected).");
        created = new Budget(month);
        for (BudgetObserver o : observers) created.addObserver(o);
        repo.saveBudget(created);
        created.setLimits(limits);
        return CommandResult.ok("Budget for " + month + " created with " + limits.size() + " categor" + (limits.size() == 1 ? "y" : "ies") + ", total $" + created.totalLimit() + ".");
    }

    @Override public boolean undo() {
        if (created == null) return false;
        repo.deleteBudget(month);
        created = null;
        return true;
    }

    @Override public String describe() { return "Create budget " + month; }
}
