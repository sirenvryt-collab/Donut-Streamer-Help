package com.donutstreamer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Read-only tracker window (open with /ds). Shows the session and a per-player table with rakeback. */
public final class TrackerScreen extends Screen {
    public TrackerScreen() {
        super(Text.literal("Donut Streamer Tracker"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Session s = DonutStreamerClient.session;

        int pw = 340;
        int ph = Math.min(this.height - 20, 230);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        ctx.fill(px, py, px + pw, py + ph, 0xE0101010);
        outline(ctx, px, py, pw, ph, 0xFFAAAAAA);

        int x = px + 8;
        int y = py + 8;
        ctx.drawTextWithShadow(this.textRenderer, "Donut Streamer Tracker", x, y, Widgets.GOLD);
        y += 14;

        int netColor = s.net > 0 ? Widgets.GREEN : (s.net < 0 ? Widgets.RED : Widgets.WHITE);
        String netText = "Net: " + Money.signed(s.net);
        if (s.startBalance > 0) {
            netText += String.format(java.util.Locale.ROOT, "  (%+.1f%%)", 100.0 * s.net / s.startBalance);
        }
        ctx.drawTextWithShadow(this.textRenderer, netText, x, y, netColor);
        y += 11;
        ctx.drawTextWithShadow(this.textRenderer,
                s.wins + "W - " + s.losses + "L (" + s.winRate() + "%)   Streak " + s.streakText()
                        + "   Time " + s.time(), x, y, Widgets.WHITE);
        y += 11;
        ctx.drawTextWithShadow(this.textRenderer,
                "Received " + Money.format(s.received) + "   Sent " + Money.format(s.sent)
                        + "   Sold " + Money.format(s.sold) + (s.includeSells ? " (counted)" : " (not counted)"),
                x, y, Widgets.GRAY);
        y += 15;

        boolean rake = s.rakebackPct > 0;
        int cName = x, cIn = x + 100, cOut = x + 160, cNet = x + 220, cRake = x + 280;
        ctx.drawTextWithShadow(this.textRenderer, "Player", cName, y, Widgets.GOLD);
        ctx.drawTextWithShadow(this.textRenderer, "Sent you", cIn, y, Widgets.GOLD);
        ctx.drawTextWithShadow(this.textRenderer, "You sent", cOut, y, Widgets.GOLD);
        ctx.drawTextWithShadow(this.textRenderer, "Net", cNet, y, Widgets.GOLD);
        if (rake) ctx.drawTextWithShadow(this.textRenderer, "Owed", cRake, y, Widgets.GOLD);
        y += 12;

        List<Session.PlayerStat> players = s.playerStats();
        int maxRows = Math.max(0, (py + ph - 22 - y) / 10);
        if (players.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, "No player payments yet.", cName, y, Widgets.GRAY);
        }
        for (int i = 0; i < players.size() && i < maxRows; i++) {
            Session.PlayerStat p = players.get(i);
            String name = s.showNames ? p.name : "Player " + (i + 1);
            ctx.drawTextWithShadow(this.textRenderer, name, cName, y, Widgets.WHITE);
            ctx.drawTextWithShadow(this.textRenderer, Money.format(p.in), cIn, y, Widgets.GREEN);
            ctx.drawTextWithShadow(this.textRenderer, Money.format(p.out), cOut, y, Widgets.RED);
            ctx.drawTextWithShadow(this.textRenderer, Money.signed(p.net()), cNet,
                    y, p.net() >= 0 ? Widgets.GREEN : Widgets.RED);
            if (rake) ctx.drawTextWithShadow(this.textRenderer, Money.format(s.rakebackOwed(p)), cRake, y, Widgets.AQUA);
            y += 10;
        }

        ctx.drawTextWithShadow(this.textRenderer, "Esc to close  |  /ds help for commands",
                x, py + ph - 14, Widgets.GRAY);
    }
}
