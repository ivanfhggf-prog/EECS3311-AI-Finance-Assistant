package finance;

import com.sun.net.httpserver.HttpServer;
import finance.agent.*;
import finance.app.AgentFacade;
import finance.command.CommandResult;
import finance.domain.*;
import finance.llm.*;
import finance.tools.SpendingStats;
import finance.util.*;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Stage 2 basic verification (plain Java, no framework available offline). Exit code 1 if any check fails.
 * Run: ./run-tests.sh   (Stage 3 will add the full deterministic suite and KUMA agent tests.)
 */
public class TestRunner {
    static int passed, failed;
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final Path SAMPLE = Paths.get("sample-data/transactions.csv");

    interface Body { void run() throws Exception; }

    static void test(String name, Body b) {
        try { b.run(); passed++; System.out.println("PASS  " + name); }
        catch (Throwable e) { failed++; System.out.println("FAIL  " + name + "  -> " + e); }
    }
    static void check(boolean c, String msg) { if (!c) throw new AssertionError(msg); }
    static void eq(Object a, Object b, String msg) { if (!Objects.equals(a, b)) throw new AssertionError(msg + " expected <" + b + "> but was <" + a + ">"); }

    static String lines(Object o) { StringBuilder b = new StringBuilder(); for (Object x : (List<?>) o) b.append(x).append('\n'); return b.toString(); }

    static AgentFacade app(FakeLLM llm) { return AgentFacade.create(llm, TODAY); }
    static AgentFacade loaded(FakeLLM llm) { AgentFacade f = app(llm); f.importTransactions(SAMPLE); return f; }
    static AgentFacade categorized(FakeLLM llm) { AgentFacade f = loaded(llm); f.categorizeAll(); return f; }

    public static void main(String[] a) throws Exception {
        // ---------- utilities
        test("Json parses nested structures and escapes", () -> {
            Map<String, Object> m = Json.asMap(Json.parse("{\"a\":[1,2,{\"b\":\"x\\ny\"}],\"c\":null,\"d\":true}"));
            eq(Json.asList(m.get("a")).size(), 3, "array size");
            check(Json.write(m).contains("\"b\":\"x\\ny\""), "round trip escapes");
        });
        test("Json.parseLenient extracts JSON from prose / code fences", () -> eq(Json.asList(Json.parseLenient("Sure!\n```json\n[{\"id\":\"1\"}]\n```")).size(), 1, "items"));
        test("Json rejects malformed input", () -> { try { Json.parse("{\"a\":"); check(false, "should throw"); } catch (RuntimeException e) { /* ok */ } });
        test("Csv handles quotes, commas and doubled quotes", () -> eq(Csv.parseLine("a,\"b,c\",\"d\"\"e\""), List.of("a", "b,c", "d\"e"), "fields"));
        test("Category.parse is case/space tolerant and rejects junk", () -> { eq(Category.parse("dining"), Category.DINING, "dining"); eq(Category.parse("nonsense"), null, "junk"); });

        // ---------- F01 import
        test("F01 import loads sample CSV", () -> { AgentFacade f = app(new FakeLLM()); CommandResult r = f.importTransactions(SAMPLE); check(r.ok, r.message); eq(f.transactions().size(), 59, "rows"); });
        test("F01 import skips duplicates and reports bad rows with line numbers", () -> {
            AgentFacade f = app(new FakeLLM());
            CommandResult r = f.importTransactions(Paths.get("sample-data/transactions-with-errors.csv"));
            check(r.message.contains("Imported 2 transaction(s); 1 duplicate(s) skipped; 3 bad row(s)."), r.message);
            check(r.details.get(0).startsWith("line 4:"), "line number " + r.details);
            CommandResult again = f.importTransactions(Paths.get("sample-data/transactions-with-errors.csv"));
            check(again.message.contains("Imported 0"), "re-import adds nothing: " + again.message);
        });
        test("F01 import of missing file fails cleanly", () -> check(!app(new FakeLLM()).importTransactions(Paths.get("nope.csv")).ok, "must fail"));

        // ---------- F02 / F03 categorisation + memory
        test("F02 categorize assigns categories via the LLM", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = loaded(llm); Response r = f.categorizeAll();
            check(r.ok, r.text);
            check(f.transactions().stream().noneMatch(t -> t.getCategory() == Category.UNCATEGORIZED), "all categorised");
            check(r.trace.stream().anyMatch(s -> s.contains("CategorizerTool")), "plan trace shows tool step");
            check(llm.prompts.size() == 3, "59 rows batched in 25s -> 3 LLM calls, got " + llm.prompts.size());
        });
        test("F02 unrecognised or low-confidence AI categories go to review", () -> {
            AgentFacade f = app(new FakeLLM());
            Path p = Files.createTempFile("tx", ".csv");
            Files.writeString(p, "date,amount,merchant,description\n2026-10-01,-5,Mystery Shop,x\n2026-10-02,-6,Unsure Place,y\n2026-10-03,-7,Loblaws,z\n");
            f.importTransactions(p); f.categorizeAll();
            Map<String, Transaction> m = new HashMap<>(); for (Transaction t : f.transactions()) m.put(t.getMerchant(), t);
            eq(m.get("Mystery Shop").getCategory(), Category.UNCATEGORIZED, "garbage category"); check(m.get("Mystery Shop").needsReview(), "flagged");
            eq(m.get("Unsure Place").getCategory(), Category.UNCATEGORIZED, "low confidence"); eq(m.get("Loblaws").getCategory(), Category.GROCERIES, "good one");
        });
        test("F02 LLM outage: no crash, nothing fabricated, clear message", () -> {
            FakeLLM llm = new FakeLLM(); llm.failing = true; AgentFacade f = loaded(llm); Response r = f.categorizeAll();
            check(f.transactions().stream().allMatch(t -> t.getCategory() == Category.UNCATEGORIZED), "left uncategorised");
            check(String.join(" ", r.details).contains("AI unavailable"), "explains: " + r.details);
            eq(llm.prompts.size(), 1, "stops after first failed batch instead of hammering the service");
        });
        test("F03 correction updates category and memory; next categorisation skips the LLM for that merchant", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = loaded(llm); f.categorizeAll();
            Transaction best = f.transactions().stream().filter(t -> t.getMerchant().equals("Best Buy")).findFirst().get();
            check(f.correctCategory(best.getId(), "entertainment").ok, "correct");
            eq(best.getCategory(), Category.ENTERTAINMENT, "updated");
            Path p = Files.createTempFile("tx", ".csv"); Files.writeString(p, "date,amount,merchant,description\n2026-10-30,-100,Best Buy,again\n");
            f.importTransactions(p); int before = llm.prompts.size(); f.categorizeAll();
            eq(llm.prompts.size(), before, "no LLM call needed");
            eq(f.transactions().stream().filter(t -> t.getDescription().equals("again")).findFirst().get().getCategory(), Category.ENTERTAINMENT, "memory applied");
        });
        test("F03 rejects invalid category / unknown transaction; undo restores", () -> {
            AgentFacade f = categorized(new FakeLLM());
            check(!f.correctCategory("T0001", "bogus").ok, "bad category"); check(!f.correctCategory("T9999", "dining").ok, "bad id");
            Transaction t = f.transactions().get(0); Category before = t.getCategory();
            f.correctCategory(t.getId(), before == Category.DINING ? "shopping" : "dining"); f.undo();
            eq(t.getCategory(), before, "restored by undo");
        });

        // ---------- F04 / F05 budget + Observer
        test("F04 budget validation: positive limits, no duplicate month", () -> {
            AgentFacade f = app(new FakeLLM());
            check(!f.createBudget(YearMonth.of(2026, 10), Map.of(Category.DINING, BigDecimal.ZERO)).ok, "zero rejected");
            check(!f.createBudget(YearMonth.of(2026, 10), Map.of(Category.DINING, new BigDecimal("-5"))).ok, "negative rejected");
            check(f.createBudget(YearMonth.of(2026, 10), Map.of(Category.DINING, new BigDecimal("100"))).ok, "valid");
            check(!f.createBudget(YearMonth.of(2026, 10), Map.of(Category.DINING, new BigDecimal("100"))).ok, "duplicate rejected");
        });
        test("F05 tracking computes spent/remaining and notifies observers (Observer pattern)", () -> {
            AgentFacade f = loaded(new FakeLLM()); int[] calls = {0}; BudgetObserver o = b -> calls[0]++;
            f.addBudgetObserver(o);
            f.createBudget(YearMonth.of(2026, 10), new EnumMap<>(Map.of(Category.GROCERIES, new BigDecimal("100"), Category.DINING, new BigDecimal("500"))));
            int afterCreate = calls[0]; f.categorizeAll();
            check(calls[0] > afterCreate, "observer notified when categorisation changed spending");
            Response r = f.trackBudget(YearMonth.of(2026, 10));
            check(r.ok && String.join("\n", r.details).contains("GROCERIES") && String.join("\n", r.details).contains("OVER BUDGET"), "groceries over a $100 limit: " + r.details);
        });
        test("F05 no budget for month asks the user to create one", () -> { Response r = loaded(new FakeLLM()).trackBudget(YearMonth.of(2026, 10)); check(!r.ok && r.text.contains("No budget"), r.text); });

        // ---------- F06 / F07 / F08
        test("F06 trend needs >= 2 months, then uses the LLM and returns chart data", () -> {
            AgentFacade one = app(new FakeLLM()); Path p = Files.createTempFile("tx", ".csv"); Files.writeString(p, "date,amount,merchant,description\n2026-10-30,-100,Loblaws,x\n");
            one.importTransactions(p); one.categorizeAll(); check(!one.analyzeSpending("trend").ok, "1 month insufficient");
            Response r = categorized(new FakeLLM()).analyzeSpending("trend");
            check(r.ok && r.text.startsWith("Narrative") && ((List<?>) r.data.get("chart")).size() == 3, "3 months of chart data: " + r.data);
        });
        test("F07 hybrid: clear outlier flagged deterministically (no LLM), borderline goes to LLM", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = categorized(llm); int before = llm.prompts.size();
            Response r = f.analyzeSpending("unusual");
            check(lines(r.data.get("flags")).contains("Best Buy"), "laptop flagged: " + r.data);
            check(llm.prompts.size() >= before, "ran");
        });
        test("F07 clear outlier needs no LLM at all (works during an outage)", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = categorized(llm); llm.failing = true; int n = llm.prompts.size(); Response r = f.analyzeSpending("unusual");
            check(r.ok && lines(r.data.get("flags")).contains("Best Buy"), "still flags clear outlier"); eq(llm.prompts.size(), n, "LLM not called");
        });
        test("F07 borderline expense: LLM judges it; during an outage the user is told it was not judged", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = app(llm); Path p = Files.createTempFile("tx", ".csv");
            Files.writeString(p, "date,amount,merchant,description\n2026-10-01,-20,Starbucks,a\n2026-10-03,-21,Starbucks,b\n2026-10-05,-19,Starbucks,c\n2026-10-07,-20,Starbucks,d\n2026-10-09,-46,Sushi Yama,e\n");
            f.importTransactions(p); f.categorizeAll();
            Response r = f.analyzeSpending("unusual"); check(lines(r.data.get("flags")).contains("Sushi Yama") && lines(r.data.get("flags")).contains("Noticeably"), "AI-confirmed: " + r.data);
            llm.failing = true; Response r2 = f.analyzeSpending("unusual");
            check(r2.ok && lines(r2.data.get("flags")).isBlank() && String.join(" ", r2.details).contains("AI unavailable"), "not judged + told: " + r2.details);
        });
        test("F07/F08 CLI-facing response lists the findings themselves, not just a count", () -> {
            AgentFacade f = categorized(new FakeLLM());
            check(f.analyzeSpending("unusual").toString().contains("Best Buy"), "flag text in response");
            check(f.analyzeSpending("recurring").toString().contains("Netflix: monthly"), "recurring text in response");
        });
        test("F07 unusual needs categorised data", () -> { Response r = loaded(new FakeLLM()).analyzeSpending("unusual"); check(!r.ok && r.text.contains("categorize"), r.text); });
        test("F07 dismissing a flag removes it", () -> {
            AgentFacade f = categorized(new FakeLLM()); Transaction t = f.transactions().stream().filter(x -> x.getMerchant().equals("Best Buy")).findFirst().get();
            f.dismissFlag(t.getId()); check(!lines(f.analyzeSpending("unusual").data.get("flags")).contains("Best Buy"), "gone");
        });
        test("F08 recurring: Netflix (incl. NETFLIX.COM variant), Spotify, rent, etc. found deterministically", () -> {
            Response r = categorized(new FakeLLM()).analyzeSpending("recurring");
            String all = lines(r.data.get("recurring"));
            check(all.contains("Netflix: monthly") && all.contains("Spotify: monthly") && all.contains("Maple Property Mgmt: monthly"), all);
            check(!all.contains("Loblaws") && !all.contains("Starbucks"), "irregular merchants not listed: " + all);
        });
        test("F08 similar names merged only when the LLM confirms; user can reject", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = app(llm); Path p = Files.createTempFile("tx", ".csv");
            Files.writeString(p, "date,amount,merchant,description\n2026-08-03,-9.99,Spotify,m\n2026-09-02,-9.99,Spotify AB,m\n2026-10-04,-9.99,Spotify,m\n");
            f.importTransactions(p);
            llm.forcedResponse = "[{\"a\":\"spotify\",\"b\":\"spotifyab\",\"same\":true}]";
            Response r = f.analyzeSpending("recurring"); check(lines(r.data.get("recurring")).contains("Spotify: monthly"), "merged: " + r.data);
            f.rejectMerge("Spotify", "Spotify AB"); int calls = llm.prompts.size(); Response r2 = f.analyzeSpending("recurring");
            eq(llm.prompts.size(), calls, "rejected pair no longer asked"); check(r2.text.startsWith("No recurring"), r2.text);
        });

        // ---------- F09 agent behaviour
        test("F09 answers with retrieved context and stores the exchange in memory", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = categorized(llm); Response r = f.askQuery("How much did I spend on food in October?");
            check(r.ok && r.trace.size() == 3, "3-step plan: " + r.trace);
            String last = llm.prompts.get(llm.prompts.size() - 1);
            check(last.contains("2026-10-") && !last.contains("2026-09-1"), "only October rows sent as context");
            eq(f.controller().memory().turnCount(), 1, "memory stored");
            f.askQuery("What about groceries in October?"); check(llm.prompts.get(llm.prompts.size() - 1).contains("food in October"), "previous turn included as context");
        });
        test("F09 ambiguous question yields a clarifying question, no answer call", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = categorized(llm); int n = llm.prompts.size(); Response r = f.askQuery("how much?");
            check(r.text.contains("Which category or month"), r.text); eq(llm.prompts.size(), n + 1, "only the interpreter call");
        });
        test("F09 no matching data: says so and does NOT ask the LLM to answer (no fabrication)", () -> {
            FakeLLM llm = new FakeLLM(); AgentFacade f = categorized(llm); int n = llm.prompts.size(); Response r = f.askQuery("What did I spend in December?");
            check(r.text.contains("no transactions matching"), r.text); eq(llm.prompts.size(), n + 1, "interpreter only"); check(r.trace.size() == 2, "plan stopped early: " + r.trace);
        });
        test("F09 LLM outage reported, not faked", () -> { FakeLLM llm = new FakeLLM(); AgentFacade f = categorized(llm); llm.failing = true; Response r = f.askQuery("food in October?"); check(!r.ok, r.text); });
        test("F09 empty system asks for import", () -> check(!app(new FakeLLM()).askQuery("anything").ok, "needs data"));

        // ---------- F10 / F11
        test("F10 summary is labelled partial for the current month, complete otherwise", () -> {
            AgentFacade f = categorized(new FakeLLM());
            check(!f.generateSummary(YearMonth.of(2026, 10)).text.contains("PARTIAL"), "October is complete (today is Nov 2)");
            Path p = Files.createTempFile("tx", ".csv"); Files.writeString(p, "date,amount,merchant,description\n2026-11-01,-20,Loblaws,x\n"); f.importTransactions(p); f.categorizeAll();
            check(f.generateSummary(YearMonth.of(2026, 11)).text.contains("PARTIAL"), "November partial");
            check(!f.generateSummary(YearMonth.of(2025, 1)).ok, "no data month");
        });
        test("F10 summary contains deterministic totals", () -> { String s = categorized(new FakeLLM()).generateSummary(YearMonth.of(2026, 10)).text; check(s.contains("Income: $5600.00") && s.contains("RENT") && s.contains("Saved: $250.00") && s.contains("Spending: $2581.31"), s); });
        test("F11 suggestions refuse with too little data, work with enough", () -> {
            AgentFacade f = app(new FakeLLM()); check(!f.suggestSavings().ok, "empty");
            check(categorized(new FakeLLM()).suggestSavings().ok, "sample data");
        });

        // ---------- F12 goals
        test("F12 goal validation and progress (manual + SAVINGS transactions)", () -> {
            AgentFacade f = categorized(new FakeLLM());
            check(!f.saveGoal("Trip", new BigDecimal("-1"), LocalDate.of(2027, 1, 1), null).ok, "negative target");
            check(!f.saveGoal("Trip", new BigDecimal("2000"), LocalDate.of(2020, 1, 1), null).ok, "past date");
            check(f.saveGoal("Trip", new BigDecimal("2000"), LocalDate.of(2027, 6, 1), new BigDecimal("500")).ok, "valid");
            String s = f.goalsStatus().toString();
            check(s.contains("Trip") && s.contains("%"), s);
            Path p = Files.createTempFile("tx", ".csv"); Files.writeString(p, "date,amount,merchant,description\n2026-11-02,-500,Savings transfer,x\n"); f.importTransactions(p); f.categorizeAll();
            check(f.goalsStatus().toString().contains("50.0%"), "(500 manual + 500 from SAVINGS)/2000 = 50%: " + f.goalsStatus());
        });

        // ---------- F13 export
        test("F13 export CSV / TXT / PDF; empty range refuses to create a file", () -> {
            AgentFacade f = categorized(new FakeLLM()); Path dir = Files.createTempDirectory("exp");
            for (String fmt : new String[]{"csv", "txt", "pdf"}) {
                Response r = f.exportReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "transactions", fmt, dir.resolve("r." + fmt)); check(r.ok, r.text);
                check(Files.size(dir.resolve("r." + fmt)) > 100, fmt + " written");
            }
            check(new String(Files.readAllBytes(dir.resolve("r.pdf")), StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"), "pdf header");
            check(Files.readString(dir.resolve("r.csv")).startsWith("id,date,amount,merchant,description,category"), "csv header");
            Response e = f.exportReport(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31), "transactions", "csv", dir.resolve("none.csv"));
            check(!e.ok && !Files.exists(dir.resolve("none.csv")), "no empty file");
            check(!f.exportReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "transactions", "docx", dir.resolve("x.docx")).ok, "bad format");
        });

        // ---------- patterns / infrastructure
        test("Factory Method: ToolFactory creates distinct tools and rejects unknown types", () -> {
            finance.tools.ToolFactory tf = new finance.tools.ToolFactory();
            for (String t : tf.types()) check(tf.createTool(t) != null, t);
            check(!tf.createTool("trend").getClass().equals(tf.createTool("unusual").getClass()), "different products");
            try { tf.createTool("nope"); check(false, "should throw"); } catch (IllegalArgumentException e) { /* ok */ }
        });
        test("Decorator: RetryingLLMProvider retries then succeeds; gives up after N", () -> {
            int[] n = {0};
            LLMProvider flaky = new LLMProvider() { public String name() { return "flaky"; } public String generate(Prompt p) throws LLMException { if (++n[0] < 3) throw new LLMException("down"); return "ok"; } };
            eq(new RetryingLLMProvider(flaky, 3, 1).generate(new Prompt("s", "u")), "ok", "3rd attempt works");
            n[0] = -10; try { new RetryingLLMProvider(flaky, 2, 1).generate(new Prompt("s", "u")); check(false, "should fail"); } catch (LLMException e) { check(e.getMessage().contains("after 2 attempts"), e.getMessage()); }
        });
        test("Config: missing/placeholder key is reported, never a real key in source", () -> {
            Path env = Files.createTempFile("env", ""); Files.writeString(env, "LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE\nLLM_MODEL=m1\n");
            Config c = Config.load(env); check(!c.hasRealKey(), "placeholder not accepted"); eq(c.get("LLM_MODEL", "x"), "m1", "model from file");
            try { ClaudeProvider.fromConfig(c).generate(new Prompt("s", "u")); check(false, "must refuse"); } catch (LLMException e) { check(e.getMessage().contains(".env.example"), e.getMessage()); }
        });
        test("ClaudeProvider speaks the Messages API (checked against a local stub server) and counts tokens", () -> {
            HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String[] seen = new String[3];
            srv.createContext("/v1/messages", ex -> {
                seen[0] = ex.getRequestHeaders().getFirst("x-api-key"); seen[1] = ex.getRequestHeaders().getFirst("anthropic-version");
                seen[2] = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] b = "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}".getBytes();
                ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close();
            });
            srv.start();
            try {
                long in0 = UsageTracker.inputTokens.get();
                String out = new ClaudeProvider("test-key", "m", "http://127.0.0.1:" + srv.getAddress().getPort()).generate(new Prompt("SYS", "USER"));
                eq(out, "hello", "text"); eq(seen[0], "test-key", "key header"); eq(seen[1], "2023-06-01", "version header");
                Map<String, Object> body = Json.asMap(Json.parse(seen[2]));
                eq(body.get("system"), "SYS", "system"); eq(body.get("model"), "m", "model"); check(seen[2].contains("USER"), "user msg");
                eq(UsageTracker.inputTokens.get() - in0, 12L, "tokens counted");
            } finally { srv.stop(0); }
        });
        test("ClaudeProvider turns HTTP errors into LLMException", () -> {
            HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            srv.createContext("/", ex -> { byte[] b = "{\"error\":\"overloaded\"}".getBytes(); ex.sendResponseHeaders(529, b.length); ex.getResponseBody().write(b); ex.close(); });
            srv.start();
            try { new ClaudeProvider("k", "m", "http://127.0.0.1:" + srv.getAddress().getPort()).generate(new Prompt("s", "u")); check(false, "should throw"); }
            catch (LLMException e) { check(e.getMessage().contains("529"), e.getMessage()); } finally { srv.stop(0); }
        });
        test("SpendingStats.frequency: regular vs irregular", () -> {
            List<Transaction> reg = new ArrayList<>(), irr = new ArrayList<>(); int i = 0;
            for (String d : new String[]{"2026-01-05", "2026-02-04", "2026-03-06", "2026-04-05"}) reg.add(new Transaction("r" + i++, LocalDate.parse(d), new BigDecimal("-10"), "X", ""));
            for (String d : new String[]{"2026-01-05", "2026-01-09", "2026-03-20", "2026-04-02"}) irr.add(new Transaction("i" + i++, LocalDate.parse(d), new BigDecimal("-10"), "X", ""));
            eq(SpendingStats.frequency(reg), "monthly", "regular"); eq(SpendingStats.frequency(irr), null, "irregular");
        });
        test("CLI script run end-to-end through the facade", () -> {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); AgentFacade f = app(new FakeLLM());
            new finance.ui.FinanceCLI(f, new PrintStream(bo), true).run(new BufferedReader(new StringReader("import sample-data/transactions.csv\ncategorize\nbudget 2026-10 DINING=50 GROCERIES=300\ntrack 2026-10\nbogus\ncorrect T0001 nonsense\n")), false);
            String o = bo.toString(); check(o.contains("Imported 59") && o.contains("Categorized 59") && o.contains("Unknown command") && o.contains("FAILED: Invalid category"), o);
        });

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
