# EECS3311 Course Project — AI Personal Finance Assistant

An AI agent that imports bank transactions, categorizes them with an LLM, tracks budgets and savings goals, detects unusual and recurring spending, answers natural-language questions about spending, and generates summaries and saving suggestions. It has a **Swing GUI** and a **CLI**, both calling the same `AgentFacade`.

* Stage 1 design: [docs/stage1/STAGE1.md](docs/stage1/STAGE1.md)
* Stage 2 report: [docs/stage2/stage2-report.pdf](docs/stage2/stage2-report.pdf) (Word version alongside)
* Demonstration script for the videos: [docs/stage2/DEMO-SCRIPT.md](docs/stage2/DEMO-SCRIPT.md)
* Demonstration videos: see [Demonstration videos](#demonstration-videos) below

## Prerequisites

* **JDK 17 or newer** (developed and tested on JDK 21). Check with `java -version` and `javac -version`.
* No other dependencies: the project uses only the JDK (Swing, `java.net.http`), so there is nothing to install.
* An **LLM API key** for the AI features (default provider: Anthropic Claude). Without a key the deterministic features still work and the AI features say clearly that no key is configured; they never invent answers.

## Local configuration (API key)

```bash
cp .env.example .env        # Windows: copy .env.example .env
# edit .env and replace YOUR_LOCAL_API_KEY_HERE with your key
```

`.env` is listed in `.gitignore` and must never be committed. Optional settings in `.env`: `LLM_MODEL` (default `claude-haiku-5-5`, a cheap model) and `LLM_BASE_URL`.

### How to get an API key (Anthropic Claude)

The app calls the Anthropic Messages API, so each user needs their own key. No key is included in this repository.

1. Create an account at <https://console.anthropic.com> (this is separate from a claude.ai chat account).
2. Under **Billing**, add a small amount of credit (for example US$5). Running every feature a few times costs a few cents with the default `claude-haiku-5-5` model.
3. Under **API keys**, click **Create key**, name it, and copy it immediately: it is shown only once.
4. Put it in your local `.env` as `LLM_API_KEY=sk-ant-...` (no quotes or spaces) and save. Never paste it into source code, the report or a screenshot.
5. Run `./build.sh` and then `java -jar Agent.jar -cli` or `java -jar Agent.jar -gui`. The CLI banner shows `LLM: Claude (...)` when a key is found; `usage` prints the number of calls and tokens used.

Without a key the deterministic features (import, correction, budgets, tracking, goals, export) still work; the AI features report that no key is configured.

## Demonstration videos

Google Drive links (set to “Anyone with the link”). Drive cannot deep-link to a time, so the start of each feature is listed (mm:ss). The same index with links is in Section 7 of the [Stage 2 report](docs/stage2/stage2-report.pdf).

* [**Video A: CLI**](https://drive.google.com/file/d/1KAVlv_dHQl3HMMgV1IkO_FQSXi0ebfEN/view?usp=sharing)
* [**Video B: GUI**](https://drive.google.com/file/d/1R-IiAiZUZ6882VRqwqbLxYCEMF-li-ki/view?usp=sharing)
* [**Video C: exported files opened after the GUI export**](https://drive.google.com/file/d/1367FFBdWKuAF9SnBBNAo13cmbDxaYdfC/view?usp=sharing)

| Feature | Video A (CLI) | Video B (GUI) |
|---|---|---|
| F01 Import transactions | 1:07 (error case 9:12) | 0:29 (error case 8:49) |
| F02 Categorization | 1:30 | 0:46 |
| F03 Manual correction and undo | 2:50 | 1:03 |
| F04 Budget creation | 3:25 | 1:24 |
| F05 Budget tracking | 3:55 | 1:24 (with F04) |
| F06 Trends | 4:19 | 2:29 |
| F07 Unusual expenses | 4:51 | 2:49 |
| F08 Recurring payments | 5:38 | 3:26 |
| F09 Natural-language query | 6:07 | 4:26 |
| F10 Monthly summary | 7:12 | 5:23 |
| F11 Saving suggestions | 7:38 | 5:38 |
| F12 Savings goals | 8:18 | 6:08 |
| F13 Report export | 8:43 (files opened 10:09) | 7:09 (files opened in Video C) |

The empty-data error cases for F07 and F11 are shown at the start of both videos, before F01. No API key appears in any recording.

## Build and run

```bash
./build.sh                                  # Windows: build.bat   -> compiles into out/ and packages Agent.jar
java -jar Agent.jar -gui                    # graphical interface  (./run.sh gui also works)
java -jar Agent.jar -cli                    # interactive command line (./run.sh cli also works)
java -jar Agent.jar -LLMAPI -input sample-data/task.txt    # standalone agent: run a task file and exit
```

If you run without arguments, the GUI starts when a display is available, otherwise the interactive CLI.

## Standalone CLI (Agent.jar)

`Agent.jar` is a self-contained version of the agent that runs directly from a terminal (Java 17+, no other files needed except your `.env`). It reads a **task file**, runs every step, prints the results and exits, so it can be used from scripts and automated tests.

```bash
./build.sh                                              # creates Agent.jar
java -jar Agent.jar -LLMAPI -input sample-data/task.txt
```

### Supported options

| Option | Meaning |
|---|---|
| `-input <file>` | Run the task in `<file>`, then exit. `-input -` reads the task from standard input. |
| `-LLMAPI [name]` | LLM backend: `claude` (default; needs `LLM_API_KEY` in `.env`) or `none` (AI features switched off, deterministic features still run). `-LLMAPI` on its own means `claude`. |
| `-model <name>` | Model to use (default `claude-haiku-5-5`; overrides `LLM_MODEL`). |
| `-env <file>` | Settings file holding `LLM_API_KEY` (default `./.env`). |
| `-output <file>` | Also save everything the agent prints to `<file>`. |
| `-notrace` | Hide the agent's `[plan: ...]` lines. |
| `-cli` / `-gui` | Interactive command line / graphical interface. |
| `-help` | List the options. |

**Network note:** on an IPv6-only network (for example a phone hotspot) Java can print `Network is unreachable` even though the internet works. Add `-Djava.net.preferIPv6Addresses=true` after `java`, e.g. `java -Djava.net.preferIPv6Addresses=true -jar Agent.jar -LLMAPI -input sample-data/task.txt`.

**Exit code:** `0` if every step succeeded, `1` if any step failed (a `FAILED:` or `Error:` line), `2` for invalid options or an unreadable task file.

### Task file format

One step per line; lines starting with `#` are ignored. A step is either a CLI command (the same commands as the interactive CLI, see `help` and [Example usage](#example-usage-cli)) or a plain-language request of three or more words, which the agent treats as a question (F09). The included [`sample-data/task.txt`](sample-data/task.txt):

```text
import sample-data/transactions.csv
categorize
budget 2026-10 DINING=150 GROCERIES=300 RENT=1450
track 2026-10
unusual
recurring
How much did I spend on dining in October?
summary 2026-10
suggest
export csv 2026-10-01 2026-10-31 exports/task-october.csv
```

### Working example

Output abbreviated with `...`; the AI answer is the one Claude gave in our own run.

```text
$ java -jar Agent.jar -LLMAPI -input sample-data/task.txt -output result.txt
Running task sample-data/task.txt with LLM: Claude (claude-haiku-5-5) +retry
finance> import sample-data/transactions.csv
Imported 59 transaction(s); 0 duplicate(s) skipped; 0 bad row(s).
finance> categorize
Categorized 59 transaction(s); 0 need manual review.
  [plan: fetch uncategorized transactions -> categorize batch (CategorizerTool) -> ...]
finance> budget 2026-10 DINING=150 GROCERIES=300 RENT=1450
Budget for 2026-10 created with 3 categories, total $1900.
...
finance> How much did I spend on dining in October?
  (not a command: sent to the agent as 'ask')
You spent $248.88 on dining in October 2026, across 7 transactions ...
  [plan: interpret question (QueryInterpreterTool) -> retrieve matching transactions -> answer with retrieved context (QueryTool)]
...
finance> export csv 2026-10-01 2026-10-31 exports/task-october.csv
Exported 23 transaction(s) to exports/task-october.csv
Task finished: all steps succeeded.
LLM usage: ...
```

Other examples:

```bash
java -jar Agent.jar -LLMAPI none -input sample-data/task.txt        # no API key needed; AI steps report "AI is switched off"
echo "import sample-data/transactions.csv
recurring" | java -jar Agent.jar -input -                           # task from standard input
java -jar Agent.jar -LLMAPI claude -model claude-haiku-5-5 -input sample-data/demo-script.txt -notrace
```

## Example usage (CLI)

```
finance> import sample-data/transactions.csv
finance> categorize
finance> budget 2026-10 DINING=150 GROCERIES=300 RENT=1450
finance> track 2026-10
finance> unusual
finance> ask How much did I spend on food in October?
finance> summary 2026-10
finance> help
```

CSV format: `date,amount,merchant,description` with `date` as `YYYY-MM-DD` and a **negative amount for money spent**, positive for money received. `sample-data/transactions.csv` (Aug–Oct 2026) and `sample-data/transactions-with-errors.csv` (a duplicate and bad rows, for the error flow) are included. All sample data is invented.

In the GUI: Transactions tab (Import CSV, Categorize with AI, change a category, Undo) → Budget tab (create budget, live progress bars) → Insights (trends, unusual expenses, subscriptions) → Chat (questions) → Summary & Suggestions → Goals → Export.

## Features (13 from Stage 1)

F01 import · F02 AI categorization · F03 manual correction · F04 budget creation · F05 budget tracking · F06 trend analysis · F07 unusual-expense detection · F08 recurring-payment detection · F09 natural-language questions · F10 monthly summary · F11 saving suggestions · F12 savings goals · F13 report export (CSV/TXT/PDF). The mapping to classes and patterns is in the Stage 2 report.

## Tests

```bash
./run-tests.sh
```

Compiles everything with `javac` and runs the Stage 2 verification suite (plain Java; no network or API key needed — it uses a fake LLM and a local stub HTTP server). `src/test/java/finance/GuiSmoke.java` drives the GUI under a virtual display (`xvfb-run`). The full deterministic suite and the KUMA agent tests belong to Stage 3.

## Layout

```
src/main/java/finance/
  app/      Main, CliOptions (standalone CLI options), AgentFacade (Facade)
  ui/       FinanceCLI, FinanceGUI       (the GUI is the BudgetObserver)
  agent/    AgentController, Planner, Plan, MemoryManager, ToolManager, PromptBuilder
  tools/    Tool, ToolCreator/ToolFactory (Factory Method), Categorizer/Trend/Unusual/Recurring/Query tools
  command/  Command + Import/CorrectCategory/CreateBudget/SaveGoal, CommandHistory (undo)
  llm/      LLMProvider (Strategy), ClaudeProvider, DisabledProvider (-LLMAPI none), RetryingLLMProvider (Decorator)
  domain/   Transaction, Category, Budget (Observer subject), SavingsGoal
  data/     TransactionRepository (in-memory)
  report/   ReportGenerator (CSV/TXT/PDF)
```

## Known limitations

Data is held in memory for the session (nothing is saved between runs; re-import the CSV). Categories are a fixed list. PDF export is plain text.
