package com.donutstreamer;

import java.util.Locale;

/** Parses and formats amounts like 10k, 1.5m, 2b. */
public final class Money {
    private Money() {}

    public static long parse(String raw) throws NumberFormatException {
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
        if (s.isEmpty()) throw new NumberFormatException("empty");
        long mult = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> mult = 1_000L;
            case 'm' -> mult = 1_000_000L;
            case 'b' -> mult = 1_000_000_000L;
            case 't' -> mult = 1_000_000_000_000L;
            default -> { }
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        double v = Double.parseDouble(s);
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) throw new NumberFormatException("bad amount");
        return Math.round(v * mult);
    }

    public static String format(long v) {
        boolean neg = v < 0;
        double a = Math.abs((double) v);
        String out;
        if (a >= 1e12) out = trim(a / 1e12) + "T";
        else if (a >= 1e9) out = trim(a / 1e9) + "B";
        else if (a >= 1e6) out = trim(a / 1e6) + "M";
        else if (a >= 1e3) out = trim(a / 1e3) + "K";
        else out = Long.toString((long) a);
        return neg ? "-" + out : out;
    }

    public static String signed(long v) {
        return v > 0 ? "+" + format(v) : format(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }
}
