package finance.agent;

import finance.domain.*;
import finance.tools.*;

import java.time.YearMonth;
import java.util.*;

/**
 * Decomposes a request into steps and executes them. Execution is observation-driven: after each step the
 * result decides whether to continue, stop with an explanation (no fabricated answer), or insert more steps.
 */
public class Planner {
    static final int BATCH = 25;
    static final int MIN_TX_FOR_SUGGESTIONS = 8;

    public Plan decompose(Request r, PlanContext pc) {
        switch (r.type) {
            case CATEGORIZE: return categorizePlan();
            case ANALYZE: return analyzePlan((String) r.get("type"));
            case ASK: return askPlan();
            case SUMMARY: return summaryPlan((YearMonth) r.get("month"));
            case SUGGEST: return suggestPlan();
            default: throw new IllegalArgumentException("Planner does not handle " + r.type);
        }
    }

    public Response executePlan(Plan plan, PlanContext pc) {
        int guard = 0;
        while (!plan.isEmpty()) {
            if (++guard > 200) return new Response(false, "Plan aborted: too many steps.", pc.trace);
            Plan.Step s = plan.pop();
            pc.trace.add(s.name);
            Plan.Outcome o = s.action.run(pc);
            if (o.stop) { Response resp = o.response; resp.details.addAll(pc.notes); resp.data.putAll(pc.out); return resp; }
            plan.pushFront(o.insert);
        }
        return new Response(true, "Done.", pc.trace);
    }

    // ---- F02 -------------------------------------------------------------------------------------------
    private Plan categorizePlan() {
        return new Plan().add("fetch uncategorized transactions", pc -> {
            List<Transaction> u = pc.repo.findUncategorized();
            if (u.isEmpty()) return Plan.Outcome.stop(new Response(true, "Nothing to categorize: every transaction already has a category.", pc.trace));
            pc.scratch.put("todo", new ArrayDeque<>(u));
            pc.scratch.put("assignments", new ArrayList<Assignment>());
            return Plan.Outcome.insert(batchStep(pc));
        });
    }

    @SuppressWarnings("unchecked")
    private Plan.Step batchStep(PlanContext pc) {
        return new Plan.Step("categorize batch (CategorizerTool)", p -> {
            Deque<Transaction> todo = (Deque<Transaction>) p.scratch.get("todo");
            List<Transaction> batch = new ArrayList<>();
            while (!todo.isEmpty() && batch.size() < BATCH) batch.add(todo.poll());
            p.tc.transactions = batch;
            ToolResult r = p.tools.executeTool("categorize", p.tc);
            List<Assignment> all = (List<Assignment>) p.scratch.get("assignments");
            @SuppressWarnings("unchecked") List<Assignment> got = (List<Assignment>) r.data.get("assignments");
            if (got != null) all.addAll(got);
            p.notes.addAll(r.notes);
            // observation: if the AI is unavailable the rest cannot be categorized, so stop instead of failing per batch
            boolean aiDown = r.notes.stream().anyMatch(n -> n.startsWith("AI unavailable"));
            if (!r.ok) p.notes.add(r.text);
            if ((aiDown || !r.ok) && !todo.isEmpty()) {
                p.notes.add(todo.size() + " transaction(s) not attempted; run categorize again when the AI service is back.");
                todo.clear();
            }
            if (!todo.isEmpty()) return Plan.Outcome.insert(batchStep(p));
            p.out.put("assignments", all);
            long review = all.stream().filter(a -> a.needsReview).count();
            return Plan.Outcome.stop(new Response(true, String.format("Categorized %d transaction(s); %d need manual review.", all.size() - review, review), p.trace));
        });
    }

    // ---- F06, F07, F08 -----------------------------------------------------------------------------------
    private Plan analyzePlan(String type) {
        if (!Set.of("trend", "unusual", "recurring").contains(type)) throw new IllegalArgumentException("analysis type must be trend, unusual or recurring");
        return new Plan().add("load history", pc -> {
            pc.tc.history = pc.repo.findAll();
            pc.tc.transactions = pc.tc.history;
            if (pc.tc.history.isEmpty()) return Plan.Outcome.stop(new Response(false, "No transactions yet. Import a CSV first.", pc.trace));
            // observation: unusual-expense detection compares within a category, so uncategorised data cannot be analysed
            if (type.equals("unusual") && pc.tc.history.stream().allMatch(t -> t.getCategory() == Category.UNCATEGORIZED))
                return Plan.Outcome.stop(new Response(false, "Nothing is categorized yet. Run categorize first; unusual-expense detection compares each expense with its category average.", pc.trace));
            return Plan.Outcome.next();
        }).add("run analysis tool (" + type + ")", pc -> {
            ToolResult r = pc.tools.executeTool(type, pc.tc);
            pc.notes.addAll(r.notes);
            Response resp = new Response(r.ok, r.text, pc.trace);
            pc.out.putAll(r.data);
            return Plan.Outcome.stop(resp);
        });
    }

    // ---- F09 ---------------------------------------------------------------------------------------------
    private Plan askPlan() {
        return new Plan().add("interpret question (QueryInterpreterTool)", pc -> {
            String q = pc.request.get("question");
            pc.tc.question = q;
            pc.tc.history = pc.repo.findAll();
            if (pc.tc.history.isEmpty()) return Plan.Outcome.stop(new Response(false, "No transactions yet. Import a CSV first.", pc.trace));
            ToolResult r = pc.tools.executeTool("interpret", pc.tc);
            if (!r.ok) return Plan.Outcome.stop(new Response(false, r.text, pc.trace));
            if (r.data.containsKey("clarification")) return Plan.Outcome.stop(new Response(true, "I need one detail first: " + r.data.get("clarification"), pc.trace));
            pc.scratch.put("month", r.data.get("month"));
            pc.scratch.put("category", r.data.get("category"));
            return Plan.Outcome.next();
        }).add("retrieve matching transactions", pc -> {
            YearMonth m = (YearMonth) pc.scratch.get("month");
            Category c = (Category) pc.scratch.get("category");
            List<Transaction> rows = new ArrayList<>();
            for (Transaction t : pc.tc.history) if ((m == null || t.getMonth().equals(m)) && (c == null || t.getCategory() == c)) rows.add(t);
            // observation: nothing matches -> do not ask the LLM to answer from nothing
            if (rows.isEmpty()) return Plan.Outcome.stop(new Response(true, "I have no transactions matching that (" + (m == null ? "any month" : m) + (c == null ? "" : ", " + c) + "), so I cannot answer. Try another month or category, or import more data.", pc.trace));
            pc.tc.transactions = rows;
            pc.tc.budget = m == null ? null : pc.repo.findBudget(m).orElse(null);
            return Plan.Outcome.next();
        }).add("answer with retrieved context (QueryTool)", pc -> {
            ToolResult r = pc.tools.executeTool("answer", pc.tc);
            if (r.ok) pc.memory.store(pc.tc.question, r.text);
            return Plan.Outcome.stop(new Response(r.ok, r.text, pc.trace));
        });
    }

    // ---- F10 ---------------------------------------------------------------------------------------------
    private Plan summaryPlan(YearMonth month) {
        return new Plan().add("gather month data", pc -> {
            List<Transaction> rows = pc.repo.findByMonth(month);
            if (rows.isEmpty()) return Plan.Outcome.stop(new Response(false, "No transactions for " + month + ".", pc.trace));
            pc.tc.transactions = rows;
            pc.tc.history = pc.repo.findAll();
            pc.tc.budget = pc.repo.findBudget(month).orElse(null);
            pc.tc.partialMonth = !YearMonth.from(pc.today).isAfter(month);
            pc.out.put("partial", pc.tc.partialMonth);
            return Plan.Outcome.next();
        }).add("generate narrative (QueryTool)", pc -> {
            ToolResult r = pc.tools.executeTool("summary", pc.tc);
            return Plan.Outcome.stop(new Response(r.ok, r.text, pc.trace));
        });
    }

    // ---- F11 ---------------------------------------------------------------------------------------------
    private Plan suggestPlan() {
        return new Plan().add("gather spending, budgets and goals", pc -> {
            pc.tc.history = pc.repo.findAll();
            pc.tc.transactions = pc.tc.history;
            long exp = pc.tc.history.stream().filter(Transaction::isExpense).count();
            if (exp < MIN_TX_FOR_SUGGESTIONS) return Plan.Outcome.stop(new Response(false, "Not enough data for useful suggestions (" + exp + " expense transactions, need at least " + MIN_TX_FOR_SUGGESTIONS + "). Import more history first.", pc.trace));
            pc.tc.goals = pc.repo.goals();
            Optional<YearMonth> last = pc.repo.months().stream().max(Comparator.naturalOrder());
            pc.tc.budget = last.flatMap(pc.repo::findBudget).orElse(null);
            return Plan.Outcome.next();
        }).add("generate suggestions (QueryTool)", pc -> {
            ToolResult r = pc.tools.executeTool("suggest", pc.tc);
            return Plan.Outcome.stop(new Response(r.ok, r.text, pc.trace));
        });
    }
}
