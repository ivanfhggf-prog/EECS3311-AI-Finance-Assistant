package finance.command;

import finance.agent.MemoryManager;
import finance.data.TransactionRepository;
import finance.domain.*;
import java.util.Optional;

/** F03: manual category correction; stored in MemoryManager so future categorisation reuses it. Undoable. */
public class CorrectCategoryCommand implements Command {
    private final String txId, categoryName;
    private final TransactionRepository repo;
    private final MemoryManager memory;
    private Category previous;
    private boolean prevReview;
    private Transaction target;

    public CorrectCategoryCommand(String txId, String categoryName, TransactionRepository repo, MemoryManager memory) {
        this.txId = txId; this.categoryName = categoryName; this.repo = repo; this.memory = memory;
    }

    @Override public CommandResult execute() {
        Category c = Category.parse(categoryName);
        if (c == null || c == Category.UNCATEGORIZED) return CommandResult.fail("Invalid category '" + categoryName + "'. Valid: " + Category.listForPrompt());
        Optional<Transaction> t = repo.findById(txId);
        if (t.isEmpty()) return CommandResult.fail("Transaction not found: " + txId);
        target = t.get();
        previous = target.getCategory();
        prevReview = target.needsReview();
        target.setCategory(c, false);
        repo.save(target);
        memory.rememberCorrection(target.getMerchant(), c);
        return CommandResult.ok(txId + " (" + target.getMerchant() + ") is now " + c + ". Future '" + target.getMerchant() + "' charges will use this.");
    }

    @Override public boolean undo() {
        if (target == null) return false;
        target.setCategory(previous, prevReview);
        repo.save(target);
        memory.forgetCorrection(target.getMerchant());
        return true;
    }

    @Override public String describe() { return "Correct " + txId + " to " + categoryName; }
}
