package com.donutstreamer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;

/** Draws every enabled widget at its own position. Pure rendering of locally stored numbers. */
public final class HudRenderer implements HudElement {
    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Session s = DonutStreamerClient.session;
        if (s == null || !s.hudVisible || mc.player == null || mc.options.hudHidden) return;

        for (Widgets.W w : Widgets.W.values()) {
            Session.WidgetCfg c = s.cfg(w);
            if (!c.on || !Widgets.available(w, s)) continue;
            drawAt(ctx, mc, s, w, c);
        }
    }

    static void drawAt(DrawContext ctx, MinecraftClient mc, Session s, Widgets.W w, Session.WidgetCfg c) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) c.x, (float) c.y);
        m.scale(s.scale, s.scale);
        Widgets.draw(w, s, ctx, mc.textRenderer);
        m.popMatrix();
    }
}
