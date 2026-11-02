package finance.command;

import finance.data.TransactionRepository;
import finance.domain.SavingsGoal;
import java.math.BigDecimal;
import java.time.LocalDate;

/** F12: creates or updates a savings goal; optional initial contribution. */
public class SaveGoalCommand implements Command {
    private final String name;
    private final BigDecimal target, contribution;
    private final LocalDate targetDate, today;
    private final TransactionRepository repo;

    public SaveGoalCommand(String name, BigDecimal target, LocalDate targetDate, BigDecimal contribution, LocalDate today, TransactionRepository repo) {
        this.name = name; this.target = target; this.targetDate = targetDate; this.contribution = contribution; this.today = today; this.repo = repo;
    }

    @Override public CommandResult execute() {
        if (name == null || name.isBlank()) return CommandResult.fail("Goal name is required.");
        if (target == null || target.signum() <= 0) return CommandResult.fail("Target amount must be positive.");
        if (targetDate == null || !targetDate.isAfter(today)) return CommandResult.fail("Target date must be in the future.");
        if (contribution != null && contribution.signum() < 0) return CommandResult.fail("Contribution cannot be negative.");
        SavingsGoal g = repo.goals().stream().filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (g == null) g = new SavingsGoal(name, target, targetDate, today); else g.setTarget(target, targetDate);
        if (contribution != null) g.contribute(contribution);
        repo.saveGoal(g);
        return CommandResult.ok("Goal '" + name + "' saved: target $" + target + " by " + targetDate + ".");
    }

    @Override public String describe() { return "Save goal " + name; }
}
