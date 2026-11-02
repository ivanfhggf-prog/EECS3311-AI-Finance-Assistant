package finance.app;

import finance.ui.FinanceCLI;
import finance.ui.FinanceGUI;
import finance.util.Config;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.nio.file.*;

/** Entry point: `gui` (default when a display exists), `cli`, or `cli --script file.txt`. */
public class Main {
    public static void main(String[] args) throws IOException {
        Config cfg = Config.load();
        AgentFacade facade = AgentFacade.create(cfg);
        String mode = args.length > 0 ? args[0] : (GraphicsEnvironment.isHeadless() ? "cli" : "gui");
        if (mode.equals("gui")) {
            if (GraphicsEnvironment.isHeadless()) { System.err.println("No display available; use 'cli'."); System.exit(1); }
            FinanceGUI.launch(facade);
        } else if (mode.equals("cli")) {
            boolean trace = true;
            if (args.length > 2 && args[1].equals("--script")) {
                try (BufferedReader r = Files.newBufferedReader(Paths.get(args[2]))) { new FinanceCLI(facade, System.out, trace).run(r, false); }
            } else new FinanceCLI(facade, System.out, trace).run(new BufferedReader(new InputStreamReader(System.in)), true);
        } else { System.err.println("Usage: Main [gui|cli [--script file]]"); System.exit(2); }
    }
}
