package finance.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal text-only PDF writer (Courier, letter pages) so exports need no library. */
final class SimplePdf {
    private SimplePdf() {}

    static byte[] fromText(String text) {
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\n", -1)) {
            String t = l.replace("\t", "    ");
            while (t.length() > 90) { lines.add(t.substring(0, 90)); t = "  " + t.substring(90); }
            lines.add(t);
        }
        int perPage = 58;
        int pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        List<String> objs = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pages; p++) kids.append(4 + p * 2).append(" 0 R ");
        objs.add("<< /Type /Catalog /Pages 2 0 R >>");
        objs.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages + " >>");
        objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>");
        for (int p = 0; p < pages; p++) {
            StringBuilder c = new StringBuilder("BT /F1 9 Tf 12 TL 40 760 Td\n");
            for (int i = p * perPage; i < Math.min(lines.size(), (p + 1) * perPage); i++) c.append('(').append(esc(lines.get(i))).append(") Tj T*\n");
            c.append("ET");
            objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents " + (5 + p * 2) + " 0 R /Resources << /Font << /F1 3 0 R >> >> >>");
            objs.add("<< /Length " + c.length() + " >>\nstream\n" + c + "\nendstream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> off = new ArrayList<>();
        w(out, "%PDF-1.4\n");
        for (int i = 0; i < objs.size(); i++) { off.add(out.size()); w(out, (i + 1) + " 0 obj\n" + objs.get(i) + "\nendobj\n"); }
        int xref = out.size();
        w(out, "xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n");
        for (int o : off) w(out, String.format("%010d 00000 n \n", o));
        w(out, "trailer\n<< /Size " + (objs.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) { if (c == '(' || c == ')' || c == '\\') b.append('\\').append(c); else if (c < 128) b.append(c); else b.append('?'); }
        return b.toString();
    }
    private static void w(ByteArrayOutputStream o, String s) { byte[] b = s.getBytes(StandardCharsets.ISO_8859_1); o.write(b, 0, b.length); }
}
