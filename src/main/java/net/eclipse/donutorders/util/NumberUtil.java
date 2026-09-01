package net.eclipse.donutorders.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberUtil {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat PLAIN =
            new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));

    private NumberUtil() {
    }

    public static String money(double value) {
        return MONEY.format(value);
    }

    public static String comma(long value) {
        return PLAIN.format(value);
    }

    public static Double parseDouble(String raw) {
        if (raw == null) return null;
        try {
            double parsed = Double.parseDouble(raw.replace(",", "").replace("$", "").trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) return null;
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseInt(String raw) {
        Double parsed = parseDouble(raw);
        if (parsed == null) return null;
        if (parsed > Integer.MAX_VALUE) return null;
        return (int) Math.floor(parsed);
    }
}
