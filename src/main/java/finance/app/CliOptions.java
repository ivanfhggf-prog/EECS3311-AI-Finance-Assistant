package finance.app;

import java.nio.file.*;
import java.util.*;

/**
 * Command-line options of the standalone agent (java -jar Agent.jar ...). Parsing is separate from Main so it can be tested.
 *
 *   -input <file>     run the task file (one command or question per line), then exit; "-" reads standard input
 *   -LLMAPI [name]    LLM backend: claude (default) or none (AI off). "-LLMAPI" on its own means claude
 *   -model <name>     model to use (default claude-haiku-5-5; overrides LLM_MODEL)
 *   -env <file>       where to read LLM_API_KEY and other settings (default ./.env)
 *   -output <file>    also write everything the agent prints to this file
 *   -notrace          hide the "[plan: ...]" lines
 *   -cli              interactive command line       -gui   graphical interface       -help   this text
 *
 * The older forms "gui", "cli" and "cli --script <file>" still work.
 */
public final class CliOptions {
    public enum Mode { GUI, INTERACTIVE, TASK_FILE, HELP }

    public Mode mode;                 // null = choose GUI if a display exists, otherwise interactive CLI
    public String input;              // task file path or "-" for stdin
    public String llm = "claude";
    public String model, output;
    public Path envFile = Paths.get(".env");
    public boolean trace = true;

    public static final String USAGE = String.join("\n",
            "AI Personal Finance Assistant - standalone agent",
            "",
            "Usage: java -jar Agent.jar [options]",
            "  -input <file>     run the task in <file> and exit (one command or plain-language question per line; '-' = standard input)",
            "  -LLMAPI [name]    LLM backend: claude (default, needs LLM_API_KEY) or none (AI features off)",
            "  -model <name>     model name, default claude-haiku-5-5",
            "  -env <file>       settings file holding LLM_API_KEY, default ./.env",
            "  -output <file>    also save the agent's output to <file>",
            "  -notrace          do not print the agent's plan steps",
            "  -cli              interactive command line (type 'help' for commands)",
            "  -gui              graphical interface (default when a display is available)",
            "  -help             show this text",
            "",
            "Example: java -jar Agent.jar -LLMAPI -input sample-data/task.txt",
            "Exit code: 0 if every step succeeded, 1 if any step failed, 2 for invalid options.");

    public static CliOptions parse(String[] args) {
        CliOptions o = new CliOptions();
        List<String> a = Arrays.asList(args);
        // legacy forms used by run.sh / run.bat
        if (!a.isEmpty() && a.get(0).equals("gui")) { o.mode = Mode.GUI; return o; }
        if (!a.isEmpty() && a.get(0).equals("cli")) {
            if (a.size() > 2 && a.get(1).equals("--script")) { o.mode = Mode.TASK_FILE; o.input = a.get(2); }
            else o.mode = Mode.INTERACTIVE;
            return o;
        }
        for (int i = 0; i < a.size(); i++) {
            String opt = a.get(i);
            String key = opt.replaceFirst("^--?", "").toLowerCase(Locale.ROOT);
            switch (key) {
                case "input": case "i": o.input = value(a, ++i, opt); o.mode = Mode.TASK_FILE; break;
                case "llmapi": case "llm":
                    if (i + 1 < a.size() && !a.get(i + 1).startsWith("-")) o.llm = a.get(++i).toLowerCase(Locale.ROOT);
                    else o.llm = "claude";
                    if (!o.llm.equals("claude") && !o.llm.equals("none"))
                        throw new IllegalArgumentException("Unknown LLM backend '" + o.llm + "' (use claude or none)");
                    break;
                case "model": o.model = value(a, ++i, opt); break;
                case "env": o.envFile = Paths.get(value(a, ++i, opt)); break;
                case "output": case "o": o.output = value(a, ++i, opt); break;
                case "notrace": o.trace = false; break;
                case "cli": if (o.mode == null) o.mode = Mode.INTERACTIVE; break;
                case "gui": o.mode = Mode.GUI; break;
                case "help": case "h": o.mode = Mode.HELP; return o;
                default: throw new IllegalArgumentException("Unknown option '" + opt + "'");
            }
        }
        return o;
    }

    private static String value(List<String> a, int i, String opt) {
        if (i >= a.size() || a.get(i).startsWith("-") && !a.get(i).equals("-")) throw new IllegalArgumentException(opt + " needs a value");
        return a.get(i);
    }
}
