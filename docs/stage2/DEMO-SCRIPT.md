# Demonstration script (Stage 2 videos)

Record the screen and your voice. For every feature say the **feature ID and name**, show **how you invoke it and the input**, show **the result**, say **where AI is involved**, and show **one error/alternative case**. Cover both the **GUI** and the **CLI** across the recordings. Do not show `.env` or your API key (open `.env.example` instead if you need to mention configuration). After recording, put each link plus start time in Section 7 of the report and make sure the links open without an access request.

Setup (before recording): `cp .env.example .env`, put your key in `.env`, `./build.sh`.

## Recording A – CLI (`./run.sh cli`, or step through `sample-data/demo-script.txt`)
| Feature | Type | Show | Error / alternative |
|---|---|---|---|
| F01 Import | `import sample-data/transactions.csv` | 59 imported | `import sample-data/transactions-with-errors.csv` → duplicate skipped, bad rows with line numbers |
| F02 Categorize | `categorize` | counts + plan trace (3 batches); `list` | run again → "Nothing to categorize" |
| F03 Correct | `correct T0028 entertainment`, `undo` | change then undo | `correct T0028 nonsense` |
| F04 Budget | `budget 2026-10 DINING=150 GROCERIES=300 RENT=1450` | created | repeat → duplicate rejected; `budget 2026-10 DINING=0` |
| F05 Track | `track 2026-10` | DINING over budget | `track 2027-01` → no budget |
| F06 Trends | `trends` | narrative + monthly totals | n/a (or a fresh session with one month) |
| F07 Unusual | `unusual`, `dismiss <id>`, `unusual` | laptop flagged, then gone | fresh session: `unusual` before `categorize` |
| F08 Recurring | `recurring` | Netflix/Spotify/rent… | `reject-merge A B` if a merge note appears |
| F09 Ask | `ask How much did I spend on food in October?` then a follow-up | plan trace | `ask how much?` (clarification); `ask What did I spend in December?` (no data) |
| F10 Summary | `summary 2026-10` | totals + narrative | a month with no data |
| F11 Suggest | `suggest` | suggestions | fresh session: `suggest` |
| F12 Goals | `goal Trip 2000 2027-06-01 300`, `goals` | 15% progress | `goal Trip 2000 2020-01-01` |
| F13 Export | `export csv 2026-10-01 2026-10-31 exports/october.csv`, `export pdf ... summary` | open the files | `export csv 2020-01-01 2020-01-31 x.csv` → no file |
| usage | `usage` | token counts (paste into report) | |

## Recording B – GUI (`./run.sh gui`)
Transactions tab: Import CSV → Categorize with AI → select a row, choose a category, "Set category", then "Undo last change". Budget tab: set month 2026-10, limits `DINING=150, GROCERIES=300`, Create budget → watch the bars (red when over; this is the Observer). Insights: Analyze Trends, Find Unusual Expenses (then Dismiss by ID), Find Subscriptions. Chat: ask the questions above. Summary & Suggestions. Goals. Export (CSV/PDF, transactions and summary).
