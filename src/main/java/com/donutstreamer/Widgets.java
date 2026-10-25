package com.donutstreamer;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every HUD element. Each one can be switched on/off and dragged separately. */
public final class Widgets {
    private Widgets() {}

    public enum W {
        NET("net", "Live profit", true),
        RECORD("record", "Win-loss record", true),
        STREAK("streak", "Current streak", true),
        LAST("last", "Last payment", true),
        TIME("time", "Session timer", true),
        GRAPH("graph", "Profit chart", false),
        BARS("bars", "Result bars chart", false),
        WINBAR("winbar", "Win rate bar", false),
        FORM("form", "Last 10 form", false),
        GAMBLES("gambles", "Gamble count", false),
        RECEIVED("received", "Total received", false),
        SENT("sent", "Total sent", false),
        SOLD("sold", "Sold (selling income)", false),
        SPENT("spent", "Spent on items", false),
        AVGWIN("avgwin", "Average win", false),
        AVGLOSS("avgloss", "Average loss", false),
        BIGWIN("bigwin", "Biggest received", false),
        BIGLOSS("bigloss", "Biggest sent", false),
        PEAK("peak", "Peak profit", false),
        LOW("low", "Lowest point", false),
        BEST("beststreak", "Best / worst streak", false),
        PERBET("perbet", "Profit per gamble", false),
        PHR("phr", "Profit per hour", false),
        TOPPAYER("toppayer", "Top payer", false),
        BALANCE("balance", "Balance", false),
        GOALBAR("goalbar", "Goal progress", false),
        LIMIT("limit", "Loss limit bar", false);

        public final String id;
        public final String label;
        public final boolean defOn;

        W(String id, String label, boolean defOn) {
            this.id = id;
            this.label = label;
            this.defOn = defOn;
        }
    }

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;
    public static final int GOLD = 0xFFFFD700;
    public static final int AQUA = 0xFF55FFFF;

    private static final int BG = 0xB00D1117;
    private static final int CHART_W = 124;

    private static final int[][] DEFAULTS = new int[W.values().length][2];

    private static int baseHeight(W w) {
        switch (w) {
            case GRAPH: return 46;
            case BARS: return 32;
            case WINBAR: case GOALBAR: case LIMIT: return 20;
            default: return 13;
        }
    }

    static {
        int x = 6, y = 6;
        for (W w : W.values()) {
            int h = baseHeight(w);
            if (y + h > 210) {
                x += 140;
                y = 6;
            }
            DEFAULTS[w.ordinal()][0] = x;
            DEFAULTS[w.ordinal()][1] = y;
            y += h + 2;
        }
    }

    public static int[] defaultPos(W w) {
        return DEFAULTS[w.ordinal()];
    }

    /** Running profit after every payment, starting at 0. */
    public static List<Long> cumulative(Session s) {
        List<Long> cum = new ArrayList<>();
        cum.add(0L);
        long run = 0;
        for (Session.Entry e : s.history) {
            if (Session.SELL.equals(e.kind) && !s.includeSells) continue;
            run += s.delta(e);
            cum.add(run);
        }
        return cum;
    }

    private static List<Long> windowed(List<Long> list, int max) {
        return list.size() <= max ? list : list.subList(list.size() - max, list.size());
    }

    private static Session.PlayerStat topPayer(Session s) {
        Session.PlayerStat best = null;
        for (Session.PlayerStat p : s.playerStats()) {
            if (p.in > 0 && (best == null || p.in > best.in)) best = p;
        }
        return best;
    }

    /** Whether the widget has something to show right now (the HUD hides widgets without data). */
    public static boolean available(W w, Session s) {
        switch (w) {
            case NET: case RECORD: case STREAK: case TIME: return true;
            case LAST: return !s.history.isEmpty();
            case GRAPH: return cumulative(s).size() >= 3;
            case BARS: return s.total() >= 2;
            case AVGWIN: return s.wins > 0;
            case AVGLOSS: return s.losses > 0;
            case SOLD: return s.sold > 0;
            case SPENT: return s.spent > 0;
            case PHR: return s.total() > 0 && s.elapsedMs >= 60_000;
            case BALANCE: return s.startBalance >= 0;
            case LIMIT: return s.lossLimit > 0;
            case GOALBAR: return s.goal > 0;
            case TOPPAYER: return topPayer(s) != null;
            default: return s.total() > 0;
        }
    }

    public static String text(W w, Session s) {
        switch (w) {
            case NET:
                if (s.percentMode && s.startBalance > 0) {
                    return "Live: " + String.format(Locale.ROOT, "%+.1f%%", 100.0 * s.net / s.startBalance);
                }
                return "Live: " + Money.signed(s.net);
            case RECORD: return s.wins + "W - " + s.losses + "L  (" + s.winRate() + "%)";
            case STREAK: return "Streak: " + s.streakText();
            case LAST: {
                if (s.history.isEmpty()) return "Last: -";
                Session.Entry e = s.history.get(s.history.size() - 1);
                boolean in = Session.IN.equals(e.kind);
                boolean sell = Session.SELL.equals(e.kind);
                if (Session.BUY.equals(e.kind)) {
                    return "Last buy: -" + Money.format(e.amount) + (s.showNames && !e.who.isEmpty() ? " (" + e.who + ")" : "");
                }
                String who = (s.showNames && !e.who.isEmpty()) ? (in ? " from " : " to ") + e.who : "";
                if (sell) return "Last sale: +" + Money.format(e.amount);
                return "Last: " + (in ? "+" : "-") + Money.format(e.amount) + who;
            }
            case TIME: return "Time: " + s.time();
            case WINBAR: return "Win rate: " + s.winRate() + "%";
            case GAMBLES: return "Gambles: " + s.total();
            case RECEIVED: return "Received: " + Money.format(s.received);
            case SENT: return "Sent: " + Money.format(s.sent);
            case SOLD: return "Sold: " + Money.format(s.sold);
            case SPENT: return "Spent: " + Money.format(s.spent);
            case AVGWIN: return "Avg win: " + Money.format(s.received / Math.max(1, s.wins));
            case AVGLOSS: return "Avg loss: " + Money.format(s.sent / Math.max(1, s.losses));
            case BIGWIN: return "Biggest in: " + Money.format(s.biggestIn);
            case BIGLOSS: return "Biggest out: " + Money.format(s.biggestOut);
            case PEAK: return "Peak: " + Money.signed(s.peak);
            case LOW: return "Low: " + Money.signed(s.low);
            case BEST: return "Best W" + s.bestWinStreak + " / Worst L" + s.worstLossStreak;
            case PERBET: return "Per gamble: " + Money.signed(s.net / Math.max(1, s.total()));
            case PHR: {
                if (s.elapsedMs < 60_000) return "Profit/hr: -";
                long perHour = Math.round(s.net * 3_600_000.0 / s.elapsedMs);
                return "Profit/hr: " + Money.signed(perHour);
            }
            case TOPPAYER: {
                Session.PlayerStat p = topPayer(s);
                if (p == null) return "Top payer: -";
                return "Top payer: " + (s.showNames ? p.name : "Player") + " " + Money.format(p.in);
            }
            case BALANCE: return "Balance: " + (s.startBalance < 0 ? "-" : Money.format(s.startBalance + s.net));
            case GOALBAR: return "Goal: " + Money.signed(s.net) + " / " + Money.format(s.goal);
            case LIMIT: {
                if (s.lossLimit <= 0) return "Limit: off";
                return s.net <= -s.lossLimit ? "LOSS LIMIT HIT" : "Limit: -" + Money.format(s.lossLimit);
            }
            default: return "";
        }
    }

    public static int color(W w, Session s) {
        switch (w) {
            case NET: case PHR: case PERBET: return s.net > 0 ? GREEN : (s.net < 0 ? RED : WHITE);
            case STREAK: return s.streak > 0 ? GREEN : (s.streak < 0 ? RED : GRAY);
            case LAST: {
                if (s.history.isEmpty()) return GRAY;
                Session.Entry e = s.history.get(s.history.size() - 1);
                if (Session.SELL.equals(e.kind)) return AQUA;
                if (Session.BUY.equals(e.kind)) return GOLD;
                return Session.IN.equals(e.kind) ? GREEN : RED;
            }
            case RECEIVED: case BIGWIN: case AVGWIN: case PEAK: case TOPPAYER: return GREEN;
            case SENT: case BIGLOSS: case AVGLOSS: case LOW: case SPENT: return RED;
            case SOLD: case FORM: return AQUA;
            case WINBAR: return s.wins >= s.losses ? GREEN : RED;
            case GOALBAR: return (s.goal > 0 && s.net >= s.goal) ? GREEN : GOLD;
            case LIMIT: return (s.lossLimit > 0 && s.net <= -s.lossLimit) ? RED : GRAY;
            case GRAPH: case BARS: case GAMBLES: return GOLD;
            case TIME: case BEST: return GRAY;
            default: return WHITE;
        }
    }

    /** @return {width, height} of the widget in unscaled GUI pixels. */
    public static int[] size(W w, Session s, TextRenderer tr) {
        switch (w) {
            case GRAPH: return new int[]{CHART_W, baseHeight(w)};
            case BARS: return new int[]{CHART_W, baseHeight(w)};
            case FORM: return new int[]{5 + tr.getWidth("Form") + 5 + 10 * 9 + 2, baseHeight(w)};
            case WINBAR: case GOALBAR: case LIMIT:
                return new int[]{Math.max(96, tr.getWidth(text(w, s)) + 10), baseHeight(w)};
            default: return new int[]{tr.getWidth(text(w, s)) + 10, baseHeight(w)};
        }
    }

    /** Draws the widget with its top-left corner at the current matrix origin. */
    public static void draw(W w, Session s, DrawContext ctx, TextRenderer tr) {
        int[] sz = size(w, s, tr);
        int iw = sz[0] - 8;
        ctx.fill(0, 0, sz[0], sz[1], BG);
        ctx.fill(0, 0, sz[0], 1, 0x30FFFFFF);
        ctx.fill(0, 0, 2, sz[1], color(w, s));
        switch (w) {
            case GRAPH:
                lineChart(ctx, 5, 3, iw, sz[1] - 6, windowed(cumulative(s), 80));
                break;
            case BARS:
                resultBars(ctx, 5, 3, iw, sz[1] - 6, s, 20);
                break;
            case FORM:
                ctx.drawTextWithShadow(tr, "Form", 5, 3, GRAY);
                pips(ctx, 5 + tr.getWidth("Form") + 5, 3, s, 10);
                break;
            case WINBAR:
                ctx.drawTextWithShadow(tr, text(w, s), 5, 3, color(w, s));
                splitBar(ctx, 5, 13, iw, 4, s.wins, s.losses);
                break;
            case GOALBAR:
                ctx.drawTextWithShadow(tr, text(w, s), 5, 3, color(w, s));
                progressBar(ctx, 5, 13, iw, 4, s.goal > 0 ? (double) s.net / s.goal : 0, color(w, s));
                break;
            case LIMIT: {
                ctx.drawTextWithShadow(tr, text(w, s), 5, 3, color(w, s));
                double frac = s.lossLimit > 0 ? (double) Math.max(0, -s.net) / s.lossLimit : 0;
                int bar = frac >= 1 ? RED : (frac >= 0.5 ? GOLD : GREEN);
                progressBar(ctx, 5, 13, iw, 4, frac, bar);
                break;
            }
            default:
                ctx.drawTextWithShadow(tr, text(w, s), 5, 3, color(w, s));
                break;
        }
    }

    // ================= chart helpers =================

    public static final int GREEN_DIM = 0x5555FF55;
    public static final int RED_DIM = 0x55FF5555;
    public static final int TRACK = 0x40FFFFFF;
    public static final int ZERO_LINE = 0x66FFFFFF;

    /** Filled line chart of a running profit series. Green above zero, red below. */
    public static void lineChart(DrawContext ctx, int x, int y, int w, int h, List<Long> pts) {
        if (w < 4 || h < 4) return;
        int n = pts.size();
        long min = 0, max = 0;
        for (long v : pts) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        long range = Math.max(1, max - min);
        int zeroY = y + (int) Math.round((double) max / range * (h - 1));
        ctx.fill(x, zeroY, x + w, zeroY + 1, ZERO_LINE);
        if (n < 2) return;

        int prevY = -1;
        for (int px = 0; px < w; px++) {
            double t = (double) px * (n - 1) / (w - 1);
            int i = (int) Math.floor(t);
            if (i >= n - 1) i = n - 2;
            double f = t - i;
            double val = pts.get(i) + (pts.get(i + 1) - pts.get(i)) * f;
            int vy = y + (int) Math.round((max - val) / range * (h - 1));
            boolean pos = val >= 0;
            ctx.fill(x + px, Math.min(vy, zeroY), x + px + 1, Math.max(vy, zeroY) + 1, pos ? GREEN_DIM : RED_DIM);
            int y1 = prevY < 0 ? vy : Math.min(prevY, vy);
            int y2 = prevY < 0 ? vy : Math.max(prevY, vy);
            ctx.fill(x + px, y1, x + px + 1, y2 + 1, pos ? GREEN : RED);
            prevY = vy;
        }
    }

    private static List<Session.Entry> results(Session s) {
        List<Session.Entry> res = new ArrayList<>();
        for (Session.Entry e : s.history) {
            if (Session.IN.equals(e.kind) || Session.OUT.equals(e.kind)) res.add(e);
        }
        return res;
    }

    /** One bar per payment: received goes up (green), sent goes down (red), height = size of payment. */
    public static void resultBars(DrawContext ctx, int x, int y, int w, int h, Session s, int count) {
        List<Session.Entry> res = results(s);
        int mid = y + h / 2;
        ctx.fill(x, mid, x + w, mid + 1, ZERO_LINE);
        if (res.isEmpty() || count <= 0) return;
        List<Session.Entry> sub = res.subList(Math.max(0, res.size() - count), res.size());
        long max = 1;
        for (Session.Entry e : sub) max = Math.max(max, e.amount);
        int slot = Math.max(2, w / count);
        int half = Math.max(1, h / 2 - 1);
        int offset = count - sub.size();
        for (int i = 0; i < sub.size(); i++) {
            Session.Entry e = sub.get(i);
            int bx = x + (offset + i) * slot;
            int hh = Math.max(1, (int) Math.round((double) e.amount / max * half));
            if (Session.IN.equals(e.kind)) {
                ctx.fill(bx, mid - hh, bx + slot - 1, mid, GREEN);
            } else {
                ctx.fill(bx, mid + 1, bx + slot - 1, mid + 1 + hh, RED);
            }
        }
    }

    /** Green/red split bar for wins vs losses. */
    public static void splitBar(DrawContext ctx, int x, int y, int w, int h, int wins, int losses) {
        ctx.fill(x, y, x + w, y + h, TRACK);
        int total = wins + losses;
        if (total <= 0) return;
        int gw = (int) Math.round((double) w * wins / total);
        ctx.fill(x, y, x + gw, y + h, GREEN);
        ctx.fill(x + gw, y, x + w, y + h, RED);
    }

    /** Simple filled progress bar, frac clamped to 0..1. */
    public static void progressBar(DrawContext ctx, int x, int y, int w, int h, double frac, int color) {
        ctx.fill(x, y, x + w, y + h, TRACK);
        double f = Math.max(0, Math.min(1, frac));
        ctx.fill(x, y, x + (int) Math.round(w * f), y + h, color);
    }

    /** Last N results as little coloured squares, newest on the right. */
    public static void pips(DrawContext ctx, int x, int y, Session s, int count) {
        List<Session.Entry> res = results(s);
        List<Session.Entry> sub = res.subList(Math.max(0, res.size() - count), res.size());
        int offset = count - sub.size();
        for (int i = 0; i < sub.size(); i++) {
            int px = x + (offset + i) * 9;
            ctx.fill(px, y, px + 7, y + 7, Session.IN.equals(sub.get(i).kind) ? GREEN : RED);
        }
    }
}
