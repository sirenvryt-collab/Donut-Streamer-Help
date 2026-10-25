package com.donutstreamer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Client-only. Reads payment messages the server already shows you in chat, keeps numbers, draws a HUD,
 * and tags doubled payments on your own screen. It never sends packets and never presses or clicks anything for you.
 */
public class DonutStreamerClient implements ClientModInitializer {
    public static final String MOD_ID = "donutstreamer";
    public static Session session;

    private static long lastNanos = System.nanoTime();

    @Override
    public void onInitializeClient() {
        session = Session.load();

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS, Identifier.of(MOD_ID, "hud"), new HudRenderer());
        HudEditor.init();

        ClientReceiveMessageEvents.MODIFY_GAME.register(PaymentChat::modify);
        ClientTickEvents.END_CLIENT_TICK.register(DonutStreamerClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> session.save());
        ClientCommandRegistrationCallback.EVENT.register(Commands::register);
    }

    private static void tick(MinecraftClient client) {
        long now = System.nanoTime();
        if (client.world != null && client.player != null) {
            session.elapsedMs += (now - lastNanos) / 1_000_000L;
        }
        lastNanos = now;
    }
}
