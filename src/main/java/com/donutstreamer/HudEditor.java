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
        rows.add(new Row("Turn ALL elements on", null, () -> {
            for (Widgets.W w : Widgets.W.values()) s.cfg(w).on = true;
        }));
        rows.add(new Row("Turn ALL elements off", null, () -> {
            for (Widgets.W w : Widgets.W.values()) s.cfg(w).on = false;
        }));
        for (Widgets.W w : Widgets.W.values()) {
            rows.add(new Row(w.label, s.cfg(w).on, () -> s.cfg(w).on = !s.cfg(w).on));
        }
        rows.add(new Row("Tag doubled payments", s.doubleTag, () -> s.doubleTag = !s.doubleTag));
        rows.add(new Row("Live as % of balance", s.percentMode, () -> s.percentMode = !s.percentMode));
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

        // ---- editor button + panel (rows flow into columns so everything fits on screen) ----
        String btn = "HUD editor " + (panelOpen ? "\u25B2" : "\u25BC");
        int btnW = tr.getWidth(btn) + 8, btnH = 12;
        int btnX = sw - btnW - 4, btnY = 4;

        List<Row> rows = rows(s);
        int rowH = 11;
        int labelW = 0;
        for (Row r : rows) labelW = Math.max(labelW, tr.getWidth("[x] " + r.label()));
        int colW = labelW + 10;
        int panelY = btnY + btnH + 2;
        int screenH = mc.getWindow().getScaledHeight();
        int perCol = Math.max(1, (screenH - panelY - 12) / rowH);
        int cols = (rows.size() + perCol - 1) / perCol;
        int panelW = cols * colW;
        int panelH = Math.min(rows.size(), perCol) * rowH + 6;
        int panelX = sw - panelW - 4;

        boolean overBtn = inside(mx, my, btnX, btnY, btnW, btnH);
        boolean overPanel = panelOpen && inside(mx, my, panelX, panelY, panelW, panelH);

        boolean consumed = false;
        if (pressed) {
            if (overBtn) {
                panelOpen = !panelOpen;
                consumed = true;
            } else if (overPanel) {
                int col = (mx - panelX) / colW;
                int rowIdx = (my - panelY - 3) / rowH;
                int idx = col * perCol + rowIdx;
                if (rowIdx >= 0 && rowIdx < perCol && idx >= 0 && idx < rows.size()) {
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
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, overBtn ? 0xE0303844 : 0xE00D1117);
        outline(ctx, btnX, btnY, btnW, btnH, Widgets.GOLD);
        ctx.drawTextWithShadow(tr, btn, btnX + 4, btnY + 2, Widgets.GOLD);

        if (panelOpen) {
            ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF00D1117);
            outline(ctx, panelX, panelY, panelW, panelH, 0xFF30363D);
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                int rx = panelX + (i / perCol) * colW;
                int ry = panelY + 3 + (i % perCol) * rowH;
                if (inside(mx, my, rx, ry, colW, rowH)) ctx.fill(rx + 1, ry, rx + colW - 1, ry + rowH, 0x40FFFFFF);
                String prefix = r.checked() == null ? "" : (r.checked() ? "[x] " : "[ ] ");
                int color = r.checked() == null ? Widgets.GOLD : (r.checked() ? Widgets.GREEN : Widgets.GRAY);
                ctx.drawTextWithShadow(tr, prefix + r.label(), rx + 5, ry + 1, color);
            }
        }
    }
}
