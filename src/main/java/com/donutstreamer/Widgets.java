package com.donutstreamer;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Every HUD element. Each one can be enabled/disabled and dragged separately. */
public final class Widgets {
    private Widgets() {}

    public enum W {
        NET("net", "Net profit", true),
        RECORD("record", "Win-loss record", true),
        STREAK("streak", "Current streak", true),
        LAST("last", "Last payment", true),
        TIME("time", "Session timer", true),
        GRAPH("graph", "Profit graph", false),
        RECEIVED("received", "Total received", false),
        SENT("sent", "Total sent", false),
        SOLD("sold", "Sold (selling income)", false),
        BIGWIN("bigwin", "Biggest received", false),
        BIGLOSS("bigloss", "Biggest sent", false),
        BEST("beststreak", "Best / worst streak", false),
        PHR("phr", "Profit per hour", false),
        BALANCE("balance", "Balance", false),
        LIMIT("limit", "Loss limit", false);

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

    private static final int GRAPH_POINTS = 40;
    private static final int GRAPH_H = 26;

    private static final int[][] DEFAULTS = new int[W.values().length][2];

    static {
        int y = 6;
        for (W w : W.values()) {
            DEFAULTS[w.ordinal()][0] = 6;
            DEFAULTS[w.ordinal()][1] = y;
            y += (w == W.GRAPH ? GRAPH_H + 8 : 12) + 2;
        }
    }

    public static int[] defaultPos(W w) {
        return DEFAULTS[w.ordinal()];
    }

    private static List<Long> cumulative(Session s) {
        List<Long> cum = new ArrayList<>();
        long run = 0;
        for (Session.Entry e : s.history) {
            long d = s.delta(e);
            if (Session.SELL.equals(e.kind) && !s.includeSells) continue;
            run += d;
            cum.add(run);
        }
        return cum;
    }

    /** Whether the widget has something to show right now (the HUD hides widgets without data). */
    public static boolean available(W w, Session s) {
        return switch (w) {
            case NET, RECORD, STREAK, TIME -> true;
            case LAST -> !s.history.isEmpty();
            case GRAPH -> cumulative(s).size() >= 2;
            case RECEIVED, SENT, BIGWIN, BIGLOSS, BEST -> s.total() > 0;
            case SOLD -> s.sold > 0;
            case PHR -> s.total() > 0 && s.elapsedMs >= 60_000;
            case BALANCE -> s.startBalance >= 0;
            case LIMIT -> s.lossLimit > 0;
        };
    }

    public static String text(W w, Session s) {
        switch (w) {
            case NET:
                if (s.percentMode && s.startBalance > 0) {
                    return "Net: " + String.format(Locale.ROOT, "%+.1f%%", 100.0 * s.net / s.startBalance);
                }
                return "Net: " + Money.signed(s.net);
            case RECORD: return s.wins + "W - " + s.losses + "L  (" + s.winRate() + "%)";
            case STREAK: return "Streak: " + s.streakText();
            case LAST: {
                if (s.history.isEmpty()) return "Last: -";
                Session.Entry e = s.history.get(s.history.size() - 1);
                boolean in = Session.IN.equals(e.kind);
                boolean sell = Session.SELL.equals(e.kind);
                String who = (s.showNames && !e.who.isEmpty()) ? (in ? " from " : " to ") + e.who : "";
                if (sell) return "Last sale: +" + Money.format(e.amount);
                return "Last: " + (in ? "+" : "-") + Money.format(e.amount) + who;
            }
            case TIME: return "Time: " + s.time();
            case RECEIVED: return "Received: " + Money.format(s.received);
            case SENT: return "Sent: " + Money.format(s.sent);
            case SOLD: return "Sold: " + Money.format(s.sold);
            case BIGWIN: return "Biggest in: " + Money.format(s.biggestIn);
            case BIGLOSS: return "Biggest out: " + Money.format(s.biggestOut);
            case BEST: return "Best W" + s.bestWinStreak + " / Worst L" + s.worstLossStreak;
            case PHR: {
                if (s.elapsedMs < 60_000) return "Profit/hr: -";
                long perHour = Math.round(s.net * 3_600_000.0 / s.elapsedMs);
                return "Profit/hr: " + Money.signed(perHour);
            }
            case BALANCE: return "Balance: " + (s.startBalance < 0 ? "-" : Money.format(s.startBalance + s.net));
            case LIMIT: {
                if (s.lossLimit <= 0) return "Limit: off";
                return s.net <= -s.lossLimit ? "LOSS LIMIT HIT" : "Limit: -" + Money.format(s.lossLimit);
            }
            default: return "";
        }
    }

    public static int color(W w, Session s) {
        switch (w) {
            case NET: case PHR: return s.net > 0 ? GREEN : (s.net < 0 ? RED : WHITE);
            case STREAK: return s.streak > 0 ? GREEN : (s.streak < 0 ? RED : GRAY);
            case LAST: {
                if (s.history.isEmpty()) return GRAY;
                Session.Entry e = s.history.get(s.history.size() - 1);
                if (Session.SELL.equals(e.kind)) return AQUA;
                return Session.IN.equals(e.kind) ? GREEN : RED;
            }
            case RECEIVED: case BIGWIN: return GREEN;
            case SENT: case BIGLOSS: return RED;
            case SOLD: return AQUA;
            case LIMIT: return (s.lossLimit > 0 && s.net <= -s.lossLimit) ? RED : GRAY;
            case TIME: case BEST: return GRAY;
            default: return WHITE;
        }
    }

    /** @return {width, height} of the widget in unscaled GUI pixels. */
    public static int[] size(W w, Session s, TextRenderer tr) {
        if (w == W.GRAPH) return new int[]{4 + GRAPH_POINTS * 2, GRAPH_H + 8};
        return new int[]{tr.getWidth(text(w, s)) + 4, 12};
    }

    /** Draws the widget with its top-left corner at the current matrix origin. */
    public static void draw(W w, Session s, DrawContext ctx, TextRenderer tr) {
        int[] sz = size(w, s, tr);
        ctx.fill(0, 0, sz[0], sz[1], 0x90000000);
        if (w == W.GRAPH) {
            drawGraph(s, ctx, sz);
        } else {
            ctx.drawTextWithShadow(tr, text(w, s), 2, 2, color(w, s));
        }
    }

    private static void drawGraph(Session s, DrawContext ctx, int[] sz) {
        List<Long> cum = cumulative(s);
        int from = Math.max(0, cum.size() - GRAPH_POINTS);
        List<Long> pts = cum.subList(from, cum.size());
        if (pts.isEmpty()) return;

        long min = 0, max = 0;
        for (long v : pts) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        long range = Math.max(1, max - min);
        int top = 4;
        int zeroY = top + (int) Math.round((double) max / range * GRAPH_H);

        ctx.fill(2, zeroY, sz[0] - 2, zeroY + 1, 0x66FFFFFF);
        for (int i = 0; i < pts.size(); i++) {
            long v = pts.get(i);
            int vy = top + (int) Math.round((double) (max - v) / range * GRAPH_H);
            int y1 = Math.min(vy, zeroY);
            int y2 = Math.max(vy, zeroY) + 1;
            int x = 2 + i * 2;
            ctx.fill(x, y1, x + 2, y2, v >= 0 ? GREEN : RED);
        }
    }
}
