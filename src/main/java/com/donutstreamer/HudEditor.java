package com.donutstreamer;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Editing UI that lives on the chat screen: drag any HUD element with the left mouse button,
 * and use the "HUD editor" button (top right) to switch elements on and off.
 * Purely local; it only changes where/whether numbers are drawn.
 */
public final class HudEditor {
    private HudEditor() {}

    private record Row(String label, Boolean checked, Runnable action) {}

    private static Screen registeredFor = null;
    private static boolean panelOpen = false;
    private static boolean prevDown = false;
    private static Widgets.W dragging = null;
    private static int dragDX, dragDY;

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            prevDown = leftDown(client);
            dragging = null;
            if (registeredFor == screen) return;   // AFTER_INIT also fires on window resize
            registeredFor = screen;
            ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> render(client, ctx, mx, my));
            ScreenEvents.remove(screen).register(scr -> {
                if (dragging != null) {
                    dragging = null;
                    DonutStreamerClient.session.save();
                }
                if (registeredFor == scr) registeredFor = null;
            });
        });
    }

    private static boolean leftDown(MinecraftClient mc) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static List<Row> rows(Session s) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Show HUD", s.hudVisible, () -> s.hudVisible = !s.hudVisible));
        for (Widgets.W w : Widgets.W.values()) {
            rows.add(new Row(w.label, s.cfg(w).on, () -> s.cfg(w).on = !s.cfg(w).on));
        }
        rows.add(new Row("Tag doubled payments", s.doubleTag, () -> s.doubleTag = !s.doubleTag));
        rows.add(new Row("Net as % of balance", s.percentMode, () -> s.percentMode = !s.percentMode));
        rows.add(new Row("Count sells in profit", s.includeSells, () -> {
            s.includeSells = !s.includeSells;
            s.recompute();
        }));
        rows.add(new Row("Sounds", s.sound, () -> s.sound = !s.sound));
        rows.add(new Row("Show player names", s.showNames, () -> s.showNames = !s.showNames));
        rows.add(new Row("Reset HUD layout", null, () -> {
            s.widgets.clear();
            s.scale = 1.0f;
        }));
        return rows;
    }

    private static void render(MinecraftClient mc, DrawContext ctx, int mx, int my) {
        Session s = DonutStreamerClient.session;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();

        boolean down = leftDown(mc);
        boolean pressed = down && !prevDown;
        boolean released = !down && prevDown;
        prevDown = down;

        // ---- widgets: draw ghosts for ones with no data yet, then outlines ----
        Widgets.W hovered = null;
        if (s.hudVisible) {
            Widgets.W[] all = Widgets.W.values();
            for (Widgets.W w : all) {
                Session.WidgetCfg c = s.cfg(w);
                if (!c.on) continue;
                if (!Widgets.available(w, s)) HudRenderer.drawAt(ctx, mc, s, w, c);
                int[] sz = Widgets.size(w, s, tr);
                int bw = Math.round(sz[0] * s.scale), bh = Math.round(sz[1] * s.scale);
                boolean over = inside(mx, my, c.x, c.y, bw, bh);
                if (over) hovered = w;
                outline(ctx, c.x - 1, c.y - 1, bw + 2, bh + 2,
                        (dragging == w) ? 0xFFFFFF55 : (over ? 0xFFFFFFFF : 0x88FFFFFF));
            }
        }

        // ---- editor button + panel ----
        String btn = "HUD editor " + (panelOpen ? "\u25B2" : "\u25BC");
        int btnW = tr.getWidth(btn) + 8, btnH = 12;
        int btnX = sw - btnW - 4, btnY = 4;

        List<Row> rows = rows(s);
        int rowH = 11, panelW = 0;
        for (Row r : rows) panelW = Math.max(panelW, tr.getWidth((r.checked() == null ? "" : "[x] ") + r.label()));
        panelW += 8;
        int panelH = rows.size() * rowH + 6;
        int panelX = sw - panelW - 4, panelY = btnY + btnH + 2;

        boolean overBtn = inside(mx, my, btnX, btnY, btnW, btnH);
        boolean overPanel = panelOpen && inside(mx, my, panelX, panelY, panelW, panelH);

        boolean consumed = false;
        if (pressed) {
            if (overBtn) {
                panelOpen = !panelOpen;
                consumed = true;
            } else if (overPanel) {
                int idx = (my - panelY - 3) / rowH;
                if (idx >= 0 && idx < rows.size()) {
                    rows.get(idx).action().run();
                    s.save();
                }
                consumed = true;
            }
        }

        // ---- dragging ----
        if (pressed && !consumed && hovered != null) {
            Session.WidgetCfg c = s.cfg(hovered);
            dragging = hovered;
            dragDX = mx - c.x;
            dragDY = my - c.y;
        }
        if (dragging != null) {
            if (down) {
                Session.WidgetCfg c = s.cfg(dragging);
                int[] sz = Widgets.size(dragging, s, tr);
                int bw = Math.round(sz[0] * s.scale), bh = Math.round(sz[1] * s.scale);
                int maxX = Math.max(0, sw - bw);
                int maxY = Math.max(0, mc.getWindow().getScaledHeight() - bh);
                c.x = Math.max(0, Math.min(maxX, mx - dragDX));
                c.y = Math.max(0, Math.min(maxY, my - dragDY));
            }
            if (released) {
                dragging = null;
                s.save();
            }
        }

        // ---- draw button + panel on top ----
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, overBtn ? 0xE0404040 : 0xD0000000);
        outline(ctx, btnX, btnY, btnW, btnH, 0xFFAAAAAA);
        ctx.drawTextWithShadow(tr, btn, btnX + 4, btnY + 2, Widgets.GOLD);

        if (panelOpen) {
            ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0000000);
            outline(ctx, panelX, panelY, panelW, panelH, 0xFFAAAAAA);
            int y = panelY + 3;
            for (Row r : rows) {
                boolean over = inside(mx, my, panelX, y, panelW, rowH);
                if (over) ctx.fill(panelX + 1, y, panelX + panelW - 1, y + rowH, 0x55FFFFFF);
                String prefix = r.checked() == null ? "" : (r.checked() ? "[x] " : "[ ] ");
                int color = (r.checked() != null && r.checked()) ? Widgets.GREEN : Widgets.WHITE;
                ctx.drawTextWithShadow(tr, prefix + r.label(), panelX + 4, y + 1, color);
                y += rowH;
            }
        }
    }
}
