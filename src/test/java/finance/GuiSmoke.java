package finance;

import finance.app.AgentFacade;
import finance.domain.Category;
import finance.ui.FinanceGUI;
import finance.util.Config;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.*;

/** Smoke test for the Swing GUI under a virtual display: builds the window, drives the facade, checks the Observer redraw, saves screenshots. */
public class GuiSmoke {
    public static void main(String[] a) throws Exception {
        AgentFacade f = AgentFacade.create(Config.load());
        FinanceGUI[] g = new FinanceGUI[1];
        SwingUtilities.invokeAndWait(() -> { g[0] = new FinanceGUI(f); g[0].setVisible(true); });
        f.importTransactions(Paths.get("sample-data/transactions.csv"));
        f.categorizeAll();
        f.createBudget(YearMonth.of(2026, 10), new EnumMap<>(Map.of(Category.DINING, new BigDecimal("150"), Category.GROCERIES, new BigDecimal("300"))));
        f.trackBudget(YearMonth.of(2026, 10));
        Thread.sleep(500);
        JTabbedPane tabs = (JTabbedPane) ((JPanel) g[0].getContentPane()).getComponent(0);
        new File("docs/stage2/screens").mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.lang.reflect.Method m = FinanceGUI.class.getDeclaredMethod("refreshTransactions"); m.setAccessible(true); m.invoke(g[0]);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        for (int i : new int[]{0, 1}) {
            final int idx = i;
            SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(idx));
            Thread.sleep(300);
            BufferedImage img = new BufferedImage(g[0].getWidth(), g[0].getHeight(), BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> g[0].paint(img.getGraphics()));
            ImageIO.write(img, "png", new File("docs/stage2/screens/gui-" + tabs.getTitleAt(idx).toLowerCase() + ".png"));
        }
        // find progress bars on the budget tab
        int[] bars = {0};
        SwingUtilities.invokeAndWait(() -> count(g[0], bars));
        System.out.println("progress bars drawn by the Observer callback: " + bars[0]);
        System.exit(bars[0] == 2 ? 0 : 1);
    }

    static void count(Container c, int[] n) { for (Component x : c.getComponents()) { if (x instanceof JProgressBar) n[0]++; if (x instanceof Container) count((Container) x, n); } }
}
