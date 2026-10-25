package com.donutstreamer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All session data + settings. Everything comes from payment messages in chat.
 * Stats are always recomputed from the history list, so undo and setting changes are exact.
 *
 * IN   = "name paid you $X"  (money received)  -> counts as a win
 * OUT  = "you paid name $X"  (money sent)      -> counts as a loss
 * SELL = "$X" from selling items (tracked separately, optionally included in profit)
 */
public final class Session {
    private static final Logger LOG = LoggerFactory.getLogger("donutstreamer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String IN = "IN";
    public static final String OUT = "OUT";
    public static final String SELL = "SELL";

    public static final class Entry {
        public String kind = IN;
        public long amount;
        public String who = "";
        public long time;

        public Entry() {}

        public Entry(String kind, String who, long amount) {
            this.kind = kind;
            this.who = who == null ? "" : who;
            this.amount = amount;
            this.time = System.currentTimeMillis();
        }
    }

    public static final class WidgetCfg {
        public boolean on;
        public int x;
        public int y;

        public WidgetCfg() {}

        public WidgetCfg(boolean on, int x, int y) {
            this.on = on;
            this.x = x;
            this.y = y;
        }
    }

    public static final class PlayerStat {
        public String name;
        public long in;
        public long out;
        public int count;

        public long net() { return in - out; }
    }

    // ---- persisted ----
    public long lossLimit = 0;         // 0 = off
    public long goal = 0;              // 0 = off (take-profit alert)
    public int tiltAlert = 0;          // 0 = off (alert after N payments sent in a row)
    public double rakebackPct = 0;     // 0 = off
    public long startBalance = -1;     // -1 = not set
    public long elapsedMs = 0;
    public List<Entry> history = new ArrayList<>();

    public boolean hudVisible = true;
    public boolean sound = false;
    public boolean showNames = true;
    public boolean percentMode = false;      // show net as % of starting balance
    public boolean includeSells = false;     // count sell income in net profit
    public boolean sellsFromActionBar = false;
    public boolean doubleTag = true;         // tag incoming payments that doubled the previous one
    public float scale = 1.0f;
    public Map<String, WidgetCfg> widgets = new LinkedHashMap<>();

    // ---- derived (not saved) ----
    public transient long net, received, sent, sold, biggestIn, biggestOut;
    public transient int wins, losses, streak, bestWinStreak, worstLossStreak;

    public WidgetCfg cfg(Widgets.W w) {
        return widgets.computeIfAbsent(w.id, k -> {
            int[] p = Widgets.defaultPos(w);
            return new WidgetCfg(w.defOn, p[0], p[1]);
        });
    }

    /** Profit change an entry causes (sells count only when enabled). */
    public long delta(Entry e) {
        if (IN.equals(e.kind)) return e.amount;
        if (OUT.equals(e.kind)) return -e.amount;
        return includeSells ? e.amount : 0;
    }

    public void recompute() {
        received = 0; sent = 0; sold = 0; biggestIn = 0; biggestOut = 0;
        wins = 0; losses = 0; streak = 0; bestWinStreak = 0; worstLossStreak = 0;
        int ws = 0, ls = 0;
        for (Entry e : history) {
            if (IN.equals(e.kind)) {
                received += e.amount;
                wins++;
                ws++;
                ls = 0;
                streak = ws;
                bestWinStreak = Math.max(bestWinStreak, ws);
                biggestIn = Math.max(biggestIn, e.amount);
            } else if (OUT.equals(e.kind)) {
                sent += e.amount;
                losses++;
                ls++;
                ws = 0;
                streak = -ls;
                worstLossStreak = Math.max(worstLossStreak, ls);
                biggestOut = Math.max(biggestOut, e.amount);
            } else {
                sold += e.amount;
            }
        }
        net = received - sent + (includeSells ? sold : 0);
    }

    public Entry add(String kind, String who, long amount) {
        Entry e = new Entry(kind, who, amount);
        history.add(e);
        recompute();
        return e;
    }

    public Entry undo() {
        if (history.isEmpty()) return null;
        Entry e = history.remove(history.size() - 1);
        recompute();
        return e;
    }

    public void reset() {
        history.clear();
        elapsedMs = 0;
        recompute();
    }

    public int total() { return wins + losses; }

    private static boolean isDouble(long next, long prev) {
        return prev > 0 && Math.abs(next - 2 * prev) <= Math.max(1, Math.round(prev * 0.02));
    }

    /**
     * Call BEFORE adding the new incoming payment. If this player's previous payment to you was
     * exactly half of this one, and you have not paid them since, returns the doubling chain
     * (oldest first, ending with this payment), e.g. [10K, 20K, 40K]. Otherwise an empty list.
     */
    public List<Long> doubleSequence(String who, long amount) {
        List<Long> rev = new ArrayList<>();
        long next = amount;
        for (int i = history.size() - 1; i >= 0; i--) {
            Entry e = history.get(i);
            if (e.who.isEmpty() || !e.who.equalsIgnoreCase(who)) continue;
            if (!IN.equals(e.kind)) break;                 // you paid them in between: chain is broken
            if (!isDouble(next, e.amount)) break;
            if (rev.isEmpty()) rev.add(next);
            rev.add(e.amount);
            next = e.amount;
        }
        List<Long> seq = new ArrayList<>();
        for (int i = rev.size() - 1; i >= 0; i--) seq.add(rev.get(i));
        return seq;
    }

    public String winRate() {
        return total() == 0 ? "0.0" : String.format(Locale.ROOT, "%.1f", 100.0 * wins / total());
    }

    public String time() {
        long s = elapsedMs / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    public String streakText() {
        if (streak > 0) return "W" + streak;
        if (streak < 0) return "L" + (-streak);
        return "-";
    }

    public long rakebackOwed(PlayerStat p) {
        return Math.round(p.in * rakebackPct / 100.0);
    }

    /** Per-player totals, most active first. */
    public List<PlayerStat> playerStats() {
        Map<String, PlayerStat> map = new LinkedHashMap<>();
        for (Entry e : history) {
            if (SELL.equals(e.kind) || e.who.isEmpty()) continue;
            PlayerStat p = map.computeIfAbsent(e.who.toLowerCase(Locale.ROOT), k -> {
                PlayerStat n = new PlayerStat();
                n.name = e.who;
                return n;
            });
            p.count++;
            if (IN.equals(e.kind)) p.in += e.amount; else p.out += e.amount;
        }
        List<PlayerStat> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Integer.compare(b.count, a.count));
        return list;
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "Donut SMP session | Net %s | %dW-%dL (%s%%) | Received %s | Sent %s | Time %s",
                Money.signed(net), wins, losses, winRate(),
                Money.format(received), Money.format(sent), time());
    }

    // ---- files ----
    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutstreamer.json");
    }

    private static Path csvPath() {
        return FabricLoader.getInstance().getGameDir().resolve("donutstreamer").resolve("bets.csv");
    }

    public static Session load() {
        Path p = configPath();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                Session s = GSON.fromJson(r, Session.class);
                if (s != null) {
                    if (s.history == null) s.history = new ArrayList<>();
                    if (s.widgets == null) s.widgets = new LinkedHashMap<>();
                    s.history.removeIf(e -> e == null || e.kind == null || e.who == null);
                    s.recompute();
                    return s;
                }
            } catch (Exception e) {
                LOG.warn("Could not read config, starting fresh", e);
            }
        }
        Session s = new Session();
        s.recompute();
        return s;
    }

    public void save() {
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            LOG.warn("Could not save config", e);
        }
    }

    /** Appends a row to <game dir>/donutstreamer/bets.csv for post-stream review. */
    public void logCsv(String type, String who, long amount, long delta) {
        try {
            Path p = csvPath();
            Files.createDirectories(p.getParent());
            boolean fresh = !Files.exists(p);
            StringBuilder sb = new StringBuilder();
            if (fresh) sb.append("time,type,player,amount,delta,net_after\n");
            sb.append(Instant.now()).append(',').append(type).append(',')
              .append(who == null ? "" : who).append(',')
              .append(amount).append(',').append(delta).append(',').append(net).append('\n');
            Files.writeString(p, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("Could not write bets.csv", e);
        }
    }
}
