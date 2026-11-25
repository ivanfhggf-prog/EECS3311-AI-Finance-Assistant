package finance.ui;

import finance.agent.Response;
import finance.app.AgentFacade;
import finance.command.CommandResult;
import finance.domain.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Command-line interface. Thin: parses text, calls AgentFacade, prints results. */
public class FinanceCLI {
    private final AgentFacade facade;
    private final PrintStream out;
    private final boolean showTrace;
    private int failures;

    public FinanceCLI(AgentFacade f, PrintStream out, boolean showTrace) { this.facade = f; this.out = out; this.showTrace = showTrace; }

    /** Runs commands until end of input or quit; returns how many commands failed (used as the CLI exit code). */
    public int run(BufferedReader in, boolean interactive) throws IOException {
        if (interactive) out.println("AI Personal Finance Assistant (CLI). Type 'help'. LLM: " + facade.llmName());
        while (true) {
            if (interactive) { out.print("finance> "); out.flush(); }
            String line = in.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!interactive) out.println("finance> " + line);
            if (line.equals("quit") || line.equals("exit")) break;
            try { handle(line); }
            catch (RuntimeException e) { failures++; out.println("Error: " + e.getMessage()); }
        }
        return failures;
    }

    void handle(String line) {
        String[] p = line.split("\\s+");
        String cmd = p[0].toLowerCase();
        String rest = line.length() > cmd.length() ? line.substring(cmd.length()).trim() : "";
        switch (cmd) {
            case "help": help(); break;
            case "import": need(p, 2, "import <file.csv>"); print(facade.importTransactions(Paths.get(rest))); break;
            case "list": facade.transactions().forEach(t -> out.println(t)); break;
            case "categorize": print(facade.categorizeAll()); break;
            case "correct": need(p, 3, "correct <txId> <CATEGORY>"); print(facade.correctCategory(p[1], p[2])); break;
            case "budget": {
                need(p, 3, "budget <YYYY-MM> CATEGORY=amount ...");
                Map<Category, BigDecimal> lim = new EnumMap<>(Category.class);
                for (int i = 2; i < p.length; i++) {
                    String[] kv = p[i].split("=");
                    Category c = kv.length == 2 ? Category.parse(kv[0]) : null;
                    if (c == null) { out.println("Bad limit '" + p[i] + "' (use CATEGORY=amount; categories: " + Category.listForPrompt() + ")"); return; }
                    lim.put(c, money(kv[1]));
                }
                print(facade.createBudget(YearMonth.parse(p[1]), lim)); break;
            }
            case "track": print(facade.trackBudget(p.length > 1 ? YearMonth.parse(p[1]) : YearMonth.from(LocalDate.now()))); break;
            case "trends": print(facade.analyzeSpending("trend")); break;
            case "unusual": print(facade.analyzeSpending("unusual")); break;
            case "recurring": print(facade.analyzeSpending("recurring")); break;
            case "dismiss": need(p, 2, "dismiss <txId>"); out.println(facade.dismissFlag(p[1])); break;
            case "reject-merge": need(p, 3, "reject-merge <merchantA> <merchantB>"); out.println(facade.rejectMerge(p[1], p[2])); break;
            case "ask": if (rest.isEmpty()) { out.println("Usage: ask <question>"); return; } print(facade.askQuery(rest)); break;
            case "summary": need(p, 2, "summary <YYYY-MM>"); print(facade.generateSummary(YearMonth.parse(p[1]))); break;
            case "suggest": print(facade.suggestSavings()); break;
            case "goal": {
                need(p, 4, "goal <name> <target> <YYYY-MM-DD> [contribution]");
                print(facade.saveGoal(p[1], money(p[2]), LocalDate.parse(p[3]), p.length > 4 ? money(p[4]) : null)); break;
            }
            case "goals": print(facade.goalsStatus()); break;
            case "export": {
                need(p, 5, "export <csv|txt|pdf> <from> <to> <file> [summary]");
                print(facade.exportReport(LocalDate.parse(p[2]), LocalDate.parse(p[3]), p.length > 5 ? p[5] : "transactions", p[1], Paths.get(p[4]))); break;
            }
            case "undo": out.println(facade.undo()); break;
            case "log": facade.commandLog().forEach(out::println); break;
            case "usage": out.println("LLM usage this session: " + facade.usage()); break;
            default:
                // A task file may contain plain-language requests: anything that is not a command but reads like a
                // sentence is handed to the agent as a question (F09), so "How much did I spend on dining?" works.
                if (p.length >= 3) { out.println("  (not a command: sent to the agent as 'ask')"); print(facade.askQuery(line)); }
                else { failures++; out.println("Unknown command '" + cmd + "'. Type 'help'."); }
        }
    }

    private static BigDecimal money(String s) {
        try { return new BigDecimal(s.replace("$", "")); } catch (NumberFormatException e) { throw new IllegalArgumentException("Not a number: " + s); }
    }

    private void need(String[] p, int n, String usage) { if (p.length < n) throw new IllegalArgumentException("Usage: " + usage); }

    private void print(CommandResult r) { if (!r.ok) failures++; out.println((r.ok ? "" : "FAILED: ") + r); }

    private void print(Response r) {
        if (!r.ok) failures++;
        out.println((r.ok ? "" : "FAILED: ") + r);
        if (r.data.get("chart") != null) out.println("  chart data: " + r.data.get("chart"));
        if (showTrace && !r.trace.isEmpty()) out.println("  [plan: " + String.join(" -> ", r.trace) + "]");
    }

    private void help() {
        out.println(String.join("\n",
                "import <file.csv>                         F01 import transactions",
                "list                                      show transactions",
                "categorize                                F02 AI categorization",
                "correct <txId> <CATEGORY>                 F03 manual correction",
                "budget <YYYY-MM> CAT=amount ...           F04 create budget",
                "track [YYYY-MM]                           F05 budget progress",
                "trends | unusual | recurring              F06 F07 F08 analysis",
                "dismiss <txId> | reject-merge <A> <B>     feedback for F07 / F08",
                "ask <question>                            F09 natural-language query",
                "summary <YYYY-MM>                         F10 monthly summary",
                "suggest                                   F11 saving suggestions",
                "goal <name> <target> <YYYY-MM-DD> [amt]   F12 savings goal | goals = progress",
                "export <csv|txt|pdf> <from> <to> <file> [summary]   F13 export",
                "undo | log | usage | quit",
                "Any other line of three or more words is treated as a question (ask)."));
    }
}
