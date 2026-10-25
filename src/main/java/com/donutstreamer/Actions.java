package com.donutstreamer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Everything the tracker does. All feedback is local (action bar / own chat).
 * Nothing here sends packets or touches the world, inventory or other players.
 */
public final class Actions {
    private Actions() {}

    private static boolean limitAlerted = false;
    private static boolean goalAlerted = false;
    private static long resetArmedUntil = 0;

    private static Session s() { return DonutStreamerClient.session; }

    private static void bar(Formatting color, String text) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal(text).formatted(color), true);
    }

    public static void chat(Formatting color, String text) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) p.sendMessage(Text.literal(text).formatted(color), false);
    }

    private static void checkAlerts() {
        Session s = s();

        if (s.lossLimit > 0 && s.net <= -s.lossLimit) {
            if (!limitAlerted) {
                limitAlerted = true;
                chat(Formatting.RED, "[Tracker] Loss limit reached (" + Money.format(s.lossLimit)
                        + "). Net: " + Money.signed(s.net) + ". Time to take a break.");
            }
        } else {
            limitAlerted = false;
        }

        if (s.goal > 0 && s.net >= s.goal) {
            if (!goalAlerted) {
                goalAlerted = true;
                chat(Formatting.GREEN, "[Tracker] Profit goal reached (" + Money.format(s.goal)
                        + "). Net: " + Money.signed(s.net) + ".");
            }
        } else {
            goalAlerted = false;
        }

        if (s.tiltAlert > 0 && s.streak == -s.tiltAlert) {
            chat(Formatting.GOLD, "[Tracker] " + s.tiltAlert + " payments sent in a row with nothing back. Maybe slow down?");
        }
    }

    /** Called for every payment / sale message read from chat (and by /ds in|out|sell). */
    public static void record(String kind, String who, long amount) {
        Session s = s();
        Session.Entry e = s.add(kind, who, amount);
        s.save();
        s.logCsv(kind, e.who, e.amount, s.delta(e));
        if (s.sound && Session.IN.equals(kind)) {
            ClientPlayerEntity pl = MinecraftClient.getInstance().player;
            if (pl != null) pl.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
        checkAlerts();
    }

    public static void undo() {
        Session s = s();
        Session.Entry e = s.undo();
        if (e == null) {
            bar(Formatting.GRAY, "Nothing to undo");
            return;
        }
        s.save();
        s.logCsv("UNDO_" + e.kind, e.who, e.amount, -s.delta(e));
        bar(Formatting.YELLOW, "Removed last entry  |  Net " + Money.signed(s.net));
        checkAlerts();
    }

    public static void requestReset() {
        long now = System.currentTimeMillis();
        if (now <= resetArmedUntil) {
            resetArmedUntil = 0;
            Session s = s();
            s.reset();
            s.save();
            s.logCsv("RESET", "", 0, 0);
            limitAlerted = false;
            goalAlerted = false;
            bar(Formatting.YELLOW, "Session reset");
        } else {
            resetArmedUntil = now + 5000;
            bar(Formatting.GOLD, "Run /ds reset again within 5s to confirm");
        }
    }

    public static void toggleHud() {
        s().hudVisible = !s().hudVisible;
        s().save();
        bar(Formatting.GRAY, "HUD " + (s().hudVisible ? "shown" : "hidden"));
    }

    public static void toggleWidget(Widgets.W w) {
        Session.WidgetCfg c = s().cfg(w);
        c.on = !c.on;
        s().save();
        bar(Formatting.GRAY, w.label + " " + (c.on ? "on" : "off"));
    }

    public static void resetLayout() {
        s().widgets.clear();
        s().scale = 1.0f;
        s().save();
        bar(Formatting.GRAY, "HUD layout reset");
    }

    public static void toggleSound() {
        s().sound = !s().sound;
        s().save();
        bar(Formatting.GRAY, "Sounds " + (s().sound ? "on" : "off"));
    }

    public static void toggleNames() {
        s().showNames = !s().showNames;
        s().save();
        bar(Formatting.GRAY, "Player names " + (s().showNames ? "shown" : "hidden"));
    }

    public static void toggleDoubles() {
        s().doubleTag = !s().doubleTag;
        s().save();
        bar(Formatting.GRAY, "Doubled-payment tag " + (s().doubleTag ? "on" : "off"));
    }

    public static void togglePercent() {
        s().percentMode = !s().percentMode;
        s().save();
        bar(Formatting.GRAY, s().percentMode
                ? (s().startBalance > 0 ? "Net shown as % of starting balance" : "Percent mode on (set /ds balance first)")
                : "Net shown as an amount");
    }

    public static void toggleSells() {
        s().includeSells = !s().includeSells;
        s().recompute();
        s().save();
        bar(Formatting.GRAY, "Sells " + (s().includeSells ? "counted in" : "not counted in") + " net profit");
    }

    public static void toggleSellSource() {
        s().sellsFromActionBar = !s().sellsFromActionBar;
        s().save();
        bar(Formatting.GRAY, "Reading sales from " + (s().sellsFromActionBar ? "the action bar" : "chat"));
    }

    public static void setHudScale(float scale) {
        s().scale = scale;
        s().save();
        bar(Formatting.GRAY, "HUD scale " + scale);
    }

    public static void setLimit(long amount) {
        s().lossLimit = amount;
        s().save();
        limitAlerted = false;
        checkAlerts();
        bar(Formatting.AQUA, amount > 0 ? "Loss limit: " + Money.format(amount) : "Loss limit off");
    }

    public static void setGoal(long amount) {
        s().goal = amount;
        s().save();
        goalAlerted = false;
        checkAlerts();
        bar(Formatting.AQUA, amount > 0 ? "Profit goal: " + Money.format(amount) : "Profit goal off");
    }

    public static void setTilt(int n) {
        s().tiltAlert = n;
        s().save();
        bar(Formatting.AQUA, n > 0 ? "Alert after " + n + " payments sent in a row" : "Loss-streak alert off");
    }

    public static void setRakeback(double pct) {
        s().rakebackPct = pct;
        s().save();
        bar(Formatting.AQUA, pct > 0 ? "Rakeback: " + pct + "% of what each player sent you" : "Rakeback tally off");
    }

    public static void setBalance(long amount) {
        s().startBalance = amount;
        s().save();
        bar(Formatting.AQUA, amount >= 0 ? "Starting balance: " + Money.format(amount) : "Balance display off");
    }

    public static void summary() {
        String text = s().summary();
        chat(Formatting.GOLD, text);
        MinecraftClient.getInstance().keyboard.setClipboard(text);
        chat(Formatting.GRAY, "(copied to clipboard)");
    }

    public static void players() {
        Session s = s();
        List<Session.PlayerStat> list = s.playerStats();
        if (list.isEmpty()) {
            chat(Formatting.GRAY, "No player payments yet this session.");
            return;
        }
        chat(Formatting.GOLD, "Payments by player (this session):");
        int shown = 0;
        for (Session.PlayerStat p : list) {
            if (shown++ >= 10) break;
            String rake = s.rakebackPct > 0 ? "  rakeback owed " + Money.format(s.rakebackOwed(p)) : "";
            chat(p.net() >= 0 ? Formatting.GREEN : Formatting.RED,
                    p.name + ": in " + Money.format(p.in) + ", out " + Money.format(p.out)
                            + ", net " + Money.signed(p.net()) + rake);
        }
    }

    public static void openWindow() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new TrackerScreen()));   // next tick, so the chat screen can close first
    }

    public static void help() {
        chat(Formatting.GOLD, "Donut Streamer Tracker (all local). Profit is read from payment messages in chat.");
        chat(Formatting.WHITE, "/ds  opens the tracker window. In chat, use the \"HUD editor\" button; drag elements to move.");
        chat(Formatting.WHITE, "/ds in|out <amt> [name]  /ds sell <amt>  (manual entry)   /ds undo  /ds reset  /ds summary  /ds players");
        chat(Formatting.WHITE, "/ds limit|goal <amt|off>  /ds tilt <2-20|off>  /ds rakeback <0.5-50|off>  /ds balance <amt|off>");
        chat(Formatting.WHITE, "/ds percent  /ds sells  /ds sellsource  /ds doubles  /ds sound  /ds names  /ds hud toggle|reset|scale <n>|<element>");
    }
}
