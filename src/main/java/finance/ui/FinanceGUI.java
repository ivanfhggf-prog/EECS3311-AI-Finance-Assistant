package finance.ui;

import finance.agent.Response;
import finance.app.AgentFacade;
import finance.command.CommandResult;
import finance.domain.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

/** Swing dashboard. It is the BudgetObserver: progress bars refresh when a Budget notifies. Long AI calls run off the UI thread. */
public class FinanceGUI extends JFrame implements BudgetObserver {
    private final AgentFacade facade;
    private final DefaultTableModel txModel = new DefaultTableModel(new String[]{"ID", "Date", "Amount", "Merchant", "Category", "Review"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable txTable = new JTable(txModel);
    private final JTextArea status = new JTextArea(3, 40);
    private final JPanel barsPanel = new JPanel();
    private final JTextArea insights = area(), chatLog = area(), summaryOut = area(), goalsOut = area();
    private final JTextField budgetMonth = new JTextField(YearMonth.now().toString(), 8), budgetLimits = new JTextField("DINING=200, GROCERIES=400", 30);

    public static void launch(AgentFacade f) { SwingUtilities.invokeLater(() -> new FinanceGUI(f).setVisible(true)); }

    public FinanceGUI(AgentFacade facade) {
        super("AI Personal Finance Assistant");
        this.facade = facade;
        facade.addBudgetObserver(this);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(980, 700);
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Transactions", transactionsTab());
        tabs.add("Budget", budgetTab());
        tabs.add("Insights", insightsTab());
        tabs.add("Chat", chatTab());
        tabs.add("Summary & Suggestions", summaryTab());
        tabs.add("Goals", goalsTab());
        tabs.add("Export", exportTab());
        status.setEditable(false); status.setLineWrap(true);
        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(new JScrollPane(status), BorderLayout.SOUTH);
        say("LLM: " + facade.llmName());
    }

    private static JTextArea area() { JTextArea a = new JTextArea(); a.setEditable(false); a.setLineWrap(true); a.setWrapStyleWord(true); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); return a; }

    private void say(String s) { status.setText(s); }

    /** Runs a blocking facade call off the UI thread, then shows the result on it. */
    private <T> void async(String busy, Supplier<T> work, java.util.function.Consumer<T> done) {
        say(busy + " ...");
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() { return work.get(); }
            @Override protected void done() {
                try { done.accept(get()); } catch (Exception e) { say("Error: " + e.getCause()); }
                refreshTransactions();
            }
        }.execute();
    }

    // ---- F01, F02, F03 ----------------------------------------------------------------------------------------
    private JPanel transactionsTab() {
        JPanel p = new JPanel(new BorderLayout());
        JButton imp = new JButton("Import CSV..."), cat = new JButton("Categorize with AI");
        JComboBox<Category> box = new JComboBox<>(Arrays.stream(Category.values()).filter(c -> c != Category.UNCATEGORIZED).toArray(Category[]::new));
        JButton fix = new JButton("Set category of selected"), undo = new JButton("Undo last change");
        imp.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(".");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { say(facade.importTransactions(fc.getSelectedFile().toPath()).toString()); refreshTransactions(); }
        });
        cat.addActionListener(e -> async("Categorizing", facade::categorizeAll, r -> say(r.toString())));
        fix.addActionListener(e -> {
            int row = txTable.getSelectedRow();
            if (row < 0) { say("Select a transaction first."); return; }
            CommandResult r = facade.correctCategory((String) txModel.getValueAt(row, 0), String.valueOf(box.getSelectedItem()));
            say(r.toString()); refreshTransactions();
        });
        undo.addActionListener(e -> { say(facade.undo()); refreshTransactions(); });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(imp); top.add(cat); top.add(box); top.add(fix); top.add(undo);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(txTable), BorderLayout.CENTER);
        return p;
    }

    void refreshTransactions() {
        txModel.setRowCount(0);
        for (Transaction t : facade.transactions()) txModel.addRow(new Object[]{t.getId(), t.getDate(), t.getAmount().toPlainString(), t.getMerchant(), t.getCategory(), t.needsReview() ? "review" : ""});
    }

    // ---- F04, F05 (Observer) ----------------------------------------------------------------------------------
    private JPanel budgetTab() {
        JPanel p = new JPanel(new BorderLayout());
        JButton create = new JButton("Create budget"), track = new JButton("Refresh progress");
        create.addActionListener(e -> {
            try {
                Map<Category, BigDecimal> lim = new EnumMap<>(Category.class);
                for (String part : budgetLimits.getText().split(",")) {
                    String[] kv = part.trim().split("=");
                    Category c = kv.length == 2 ? Category.parse(kv[0]) : null;
                    if (c == null) { say("Bad limit '" + part.trim() + "' - use CATEGORY=amount"); return; }
                    lim.put(c, new BigDecimal(kv[1].trim()));
                }
                say(facade.createBudget(YearMonth.parse(budgetMonth.getText().trim()), lim).toString());
            } catch (RuntimeException ex) { say("Invalid input: " + ex.getMessage()); }
        });
        track.addActionListener(e -> {
            try { say(facade.trackBudget(YearMonth.parse(budgetMonth.getText().trim())).toString()); } catch (RuntimeException ex) { say("Invalid month: " + ex.getMessage()); }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Month (YYYY-MM)")); top.add(budgetMonth); top.add(new JLabel("Limits")); top.add(budgetLimits); top.add(create); top.add(track);
        barsPanel.setLayout(new BoxLayout(barsPanel, BoxLayout.Y_AXIS));
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(barsPanel), BorderLayout.CENTER);
        return p;
    }

    /** Self-painted progress bar so the colour (green / red when over budget) is the same on every OS look-and-feel. */
    private static final class SpendBar extends JComponent {
        private final int pct; private final boolean over; private final String label;
        SpendBar(int pct, boolean over, String label) { this.pct = pct; this.over = over; this.label = label; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(0xE3E3E3)); g2.fillRoundRect(0, 0, w, h, 12, 12);
            g2.setColor(over ? new Color(0xEF5350) : new Color(0x81C784)); g2.fillRoundRect(0, 0, Math.max(12, w * pct / 100), h, 12, 12);
            g2.setColor(Color.BLACK); g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, (w - fm.stringWidth(label)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Observer callback: redraw per-category progress bars. */
    @Override public void update(Budget b) {
        Runnable r = () -> {
            barsPanel.removeAll();
            barsPanel.add(new JLabel("Budget " + b.getMonth() + " - total limit $" + b.totalLimit()));
            b.getLimits().forEach((c, lim) -> {
                int pct = (int) Math.min(100, Math.round(b.getSpent(c).doubleValue() * 100 / lim.doubleValue()));
                SpendBar bar = new SpendBar(pct, b.isOver(c), c + ": $" + b.getSpent(c) + " / $" + lim + (b.isOver(c) ? "  OVER BUDGET" : ""));
                bar.setPreferredSize(new Dimension(400, 30));
                bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                barsPanel.add(Box.createVerticalStrut(6)); barsPanel.add(bar);
            });
            barsPanel.revalidate(); barsPanel.repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    // ---- F06, F07, F08 ----------------------------------------------------------------------------------------
    private JPanel insightsTab() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (String[] b : new String[][]{{"Analyze Trends", "trend"}, {"Find Unusual Expenses", "unusual"}, {"Find Subscriptions", "recurring"}}) {
            JButton btn = new JButton(b[0]);
            btn.addActionListener(e -> async(b[0], () -> facade.analyzeSpending(b[1]), r -> {
                StringBuilder s = new StringBuilder(r.toString());
                if (r.data.get("chart") instanceof List) for (Object o : (List<?>) r.data.get("chart")) s.append("\n").append(o);
                if (r.data.get("flags") != null) s.append("\n\nTo dismiss a flag, type its transaction ID below and press 'Dismiss flag'.");
                insights.setText(s.toString()); say(r.ok ? "Done." : "Could not analyze: " + r.text);
            }));
            top.add(btn);
        }
        JTextField dismiss = new JTextField(6); JButton dBtn = new JButton("Dismiss flag (tx ID)");
        dBtn.addActionListener(e -> say(facade.dismissFlag(dismiss.getText().trim())));
        top.add(dismiss); top.add(dBtn);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(insights), BorderLayout.CENTER);
        return p;
    }

    // ---- F09 -------------------------------------------------------------------------------------------------
    private JPanel chatTab() {
        JPanel p = new JPanel(new BorderLayout());
        JTextField in = new JTextField(); JButton send = new JButton("Ask");
        Runnable go = () -> {
            String q = in.getText().trim();
            if (q.isEmpty()) return;
            in.setText("");
            chatLog.append("You: " + q + "\n");
            async("Thinking", () -> facade.askQuery(q), r -> chatLog.append("Assistant: " + r.text + "\n   [plan: " + String.join(" -> ", r.trace) + "]\n\n"));
        };
        send.addActionListener(e -> go.run()); in.addActionListener(e -> go.run());
        JPanel bottom = new JPanel(new BorderLayout()); bottom.add(in, BorderLayout.CENTER); bottom.add(send, BorderLayout.EAST);
        p.add(new JScrollPane(chatLog), BorderLayout.CENTER); p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    // ---- F10, F11 ---------------------------------------------------------------------------------------------
    private JPanel summaryTab() {
        JPanel p = new JPanel(new BorderLayout());
        JTextField month = new JTextField(YearMonth.now().minusMonths(1).toString(), 8);
        JButton sum = new JButton("Generate Summary"), sug = new JButton("Saving Suggestions");
        sum.addActionListener(e -> {
            try { YearMonth m = YearMonth.parse(month.getText().trim()); async("Generating summary", () -> facade.generateSummary(m), r -> { summaryOut.setText(r.toString()); say(r.ok ? "Done." : r.text); }); }
            catch (RuntimeException ex) { say("Invalid month (use YYYY-MM)."); }
        });
        sug.addActionListener(e -> async("Generating suggestions", facade::suggestSavings, r -> { summaryOut.setText(r.toString()); say(r.ok ? "Done." : r.text); }));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Month")); top.add(month); top.add(sum); top.add(sug);
        p.add(top, BorderLayout.NORTH); p.add(new JScrollPane(summaryOut), BorderLayout.CENTER);
        return p;
    }

    // ---- F12 -------------------------------------------------------------------------------------------------
    private JPanel goalsTab() {
        JPanel p = new JPanel(new BorderLayout());
        JTextField name = new JTextField("Trip", 8), target = new JTextField("2000", 6), date = new JTextField(LocalDate.now().plusMonths(6).toString(), 9), contrib = new JTextField("0", 5);
        JButton save = new JButton("Save goal"), refresh = new JButton("Refresh");
        save.addActionListener(e -> {
            try {
                say(facade.saveGoal(name.getText().trim(), new BigDecimal(target.getText().trim()), LocalDate.parse(date.getText().trim()), new BigDecimal(contrib.getText().trim())).toString());
                goalsOut.setText(facade.goalsStatus().toString());
            } catch (RuntimeException ex) { say("Invalid input: " + ex.getMessage()); }
        });
        refresh.addActionListener(e -> goalsOut.setText(facade.goalsStatus().toString()));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Name")); top.add(name); top.add(new JLabel("Target $")); top.add(target); top.add(new JLabel("By")); top.add(date); top.add(new JLabel("Add $")); top.add(contrib); top.add(save); top.add(refresh);
        p.add(top, BorderLayout.NORTH); p.add(new JScrollPane(goalsOut), BorderLayout.CENTER);
        return p;
    }

    // ---- F13 -------------------------------------------------------------------------------------------------
    private JPanel exportTab() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField from = new JTextField(YearMonth.now().minusMonths(1).atDay(1).toString(), 9), to = new JTextField(LocalDate.now().toString(), 9), file = new JTextField("exports/report.csv", 18);
        JComboBox<String> fmt = new JComboBox<>(new String[]{"csv", "txt", "pdf"}), type = new JComboBox<>(new String[]{"transactions", "summary"});
        JButton go = new JButton("Export");
        go.addActionListener(e -> {
            try {
                async("Exporting", () -> facade.exportReport(LocalDate.parse(from.getText().trim()), LocalDate.parse(to.getText().trim()), (String) type.getSelectedItem(), (String) fmt.getSelectedItem(), Paths.get(file.getText().trim())), r -> say(r.toString()));
            } catch (RuntimeException ex) { say("Invalid input: " + ex.getMessage()); }
        });
        p.add(new JLabel("From")); p.add(from); p.add(new JLabel("To")); p.add(to); p.add(type); p.add(fmt); p.add(file); p.add(go);
        return p;
    }
}
