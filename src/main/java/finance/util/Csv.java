package finance.util;

import java.util.ArrayList;
import java.util.List;

/** Small CSV line parser supporting quoted fields and doubled quotes. */
public final class Csv {
    private Csv() {}

    public static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else q = false;
                } else cur.append(c);
            } else if (c == '"') q = true;
            else if (c == ',') { out.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(c);
        }
        if (q) throw new IllegalArgumentException("unterminated quote");
        out.add(cur.toString().trim());
        return out;
    }

    public static String escape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
