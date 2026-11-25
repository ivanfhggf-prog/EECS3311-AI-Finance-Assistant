package finance.app;

import finance.llm.*;
import finance.ui.FinanceCLI;
import finance.ui.FinanceGUI;
import finance.util.Config;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Entry point of the standalone agent. Examples:
 *   java -jar Agent.jar -LLMAPI -input sample-data/task.txt     run a task file and exit
 *   java -jar Agent.jar -cli                                     interactive command line
 *   java -jar Agent.jar -gui                                     graphical interface
 * See CliOptions for every option.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(run(args, System.in, System.out, System.err));
    }

    /** Runs the agent and returns the process exit code (0 ok, 1 a step failed, 2 bad options or input). */
    public static int run(String[] args, InputStream stdin, PrintStream stdout, PrintStream stderr) throws IOException {
        CliOptions o;
        try { o = CliOptions.parse(args); }
        catch (IllegalArgumentException e) { stderr.println("Error: " + e.getMessage()); stderr.println(); stderr.println(CliOptions.USAGE); return 2; }
        if (o.mode == CliOptions.Mode.HELP) { stdout.println(CliOptions.USAGE); return 0; }

        Config cfg = Config.load(o.envFile);
        if (o.model != null) cfg.set("LLM_MODEL", o.model);
        LLMProvider llm = o.llm.equals("none") ? new DisabledProvider() : ClaudeProvider.fromConfig(cfg);
        AgentFacade facade = AgentFacade.create(o.llm.equals("none") ? llm : new RetryingLLMProvider(llm, 3, 800), java.time.LocalDate.now());
        if (o.llm.equals("claude") && !cfg.hasRealKey())
            stderr.println("Note: no LLM_API_KEY found in " + o.envFile + " or the environment; AI features will report that no key is configured.");

        CliOptions.Mode mode = o.mode != null ? o.mode : (GraphicsEnvironment.isHeadless() ? CliOptions.Mode.INTERACTIVE : CliOptions.Mode.GUI);
        if (mode == CliOptions.Mode.GUI) {
            if (GraphicsEnvironment.isHeadless()) { stderr.println("No display available; use -cli or -input <file>."); return 2; }
            FinanceGUI.launch(facade);
            return 0;
        }
        PrintStream out = stdout;
        PrintStream file = null;
        if (o.output != null) {
            file = new PrintStream(new FileOutputStream(o.output), true, StandardCharsets.UTF_8);
            out = new PrintStream(new TeeOutputStream(stdout, file), true, StandardCharsets.UTF_8);
        }
        try {
            FinanceCLI cli = new FinanceCLI(facade, out, o.trace);
            if (mode == CliOptions.Mode.INTERACTIVE)
                return cli.run(new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8)), true) == 0 ? 0 : 1;
            BufferedReader task;
            if (o.input.equals("-")) task = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
            else {
                Path p = Paths.get(o.input);
                if (!Files.isReadable(p)) { stderr.println("Error: cannot read task file " + p); return 2; }
                task = Files.newBufferedReader(p, StandardCharsets.UTF_8);
            }
            out.println("Running task " + o.input + " with LLM: " + facade.llmName());
            int failures;
            try (BufferedReader r = task) { failures = cli.run(r, false); }
            out.println("Task finished: " + (failures == 0 ? "all steps succeeded." : failures + " step(s) failed (see FAILED/Error lines)."));
            out.println("LLM usage: " + facade.usage());
            return failures == 0 ? 0 : 1;
        } finally {
            out.flush();
            if (file != null) file.close();
        }
    }

    /** Writes to two streams at once (screen and -output file). */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream a, b;
        TeeOutputStream(OutputStream a, OutputStream b) { this.a = a; this.b = b; }
        @Override public void write(int c) throws IOException { a.write(c); b.write(c); }
        @Override public void write(byte[] buf, int off, int len) throws IOException { a.write(buf, off, len); b.write(buf, off, len); }
        @Override public void flush() throws IOException { a.flush(); b.flush(); }
    }
}
