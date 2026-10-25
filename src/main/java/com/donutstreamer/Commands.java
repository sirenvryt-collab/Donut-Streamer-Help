package com.donutstreamer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class Commands {
    private Commands() {}

    private static Long amount(CommandContext<FabricClientCommandSource> c, String name) {
        try {
            return Money.parse(StringArgumentType.getString(c, name));
        } catch (NumberFormatException e) {
            Actions.chat(Formatting.RED, "Invalid amount. Try something like 5000, 10k or 1.5m.");
            return null;
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> entry(String name, String kind) {
        return literal(name).then(argument("amount", StringArgumentType.word())
            .executes(c -> {
                Long v = amount(c, "amount");
                if (v != null && v > 0) Actions.record(kind, "", v);
                return 1;
            })
            .then(argument("who", StringArgumentType.word()).executes(c -> {
                Long v = amount(c, "amount");
                if (v != null && v > 0) Actions.record(kind, StringArgumentType.getString(c, "who"), v);
                return 1;
            })));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandRegistryAccess access) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = literal("ds");
        root.executes(c -> { Actions.openWindow(); return 1; });

        root.then(literal("help").executes(c -> { Actions.help(); return 1; }));
        root.then(literal("window").executes(c -> { Actions.openWindow(); return 1; }));

        // manual entry, for anything the chat reader missed
        root.then(entry("in", Session.IN));
        root.then(entry("out", Session.OUT));
        root.then(literal("sell").then(argument("amount", StringArgumentType.word()).executes(c -> {
            Long v = amount(c, "amount");
            if (v != null && v > 0) Actions.record(Session.SELL, "", v);
            return 1;
        })));

        root.then(literal("undo").executes(c -> { Actions.undo(); return 1; }));
        root.then(literal("reset").executes(c -> { Actions.requestReset(); return 1; }));
        root.then(literal("summary").executes(c -> { Actions.summary(); return 1; }));
        root.then(literal("players").executes(c -> { Actions.players(); return 1; }));
        root.then(literal("sound").executes(c -> { Actions.toggleSound(); return 1; }));
        root.then(literal("names").executes(c -> { Actions.toggleNames(); return 1; }));
        root.then(literal("doubles").executes(c -> { Actions.toggleDoubles(); return 1; }));
        root.then(literal("percent").executes(c -> { Actions.togglePercent(); return 1; }));
        root.then(literal("sells").executes(c -> { Actions.toggleSells(); return 1; }));
        root.then(literal("sellsource").executes(c -> { Actions.toggleSellSource(); return 1; }));

        root.then(literal("limit")
            .then(literal("off").executes(c -> { Actions.setLimit(0); return 1; }))
            .then(argument("amount", StringArgumentType.word()).executes(c -> {
                Long v = amount(c, "amount");
                if (v != null) Actions.setLimit(v);
                return 1;
            })));

        root.then(literal("goal")
            .then(literal("off").executes(c -> { Actions.setGoal(0); return 1; }))
            .then(argument("amount", StringArgumentType.word()).executes(c -> {
                Long v = amount(c, "amount");
                if (v != null) Actions.setGoal(v);
                return 1;
            })));

        root.then(literal("balance")
            .then(literal("off").executes(c -> { Actions.setBalance(-1); return 1; }))
            .then(argument("amount", StringArgumentType.word()).executes(c -> {
                Long v = amount(c, "amount");
                if (v != null) Actions.setBalance(v);
                return 1;
            })));

        root.then(literal("tilt")
            .then(literal("off").executes(c -> { Actions.setTilt(0); return 1; }))
            .then(argument("n", IntegerArgumentType.integer(2, 20)).executes(c -> {
                Actions.setTilt(IntegerArgumentType.getInteger(c, "n"));
                return 1;
            })));

        root.then(literal("rakeback")
            .then(literal("off").executes(c -> { Actions.setRakeback(0); return 1; }))
            .then(argument("pct", DoubleArgumentType.doubleArg(0.5, 50)).executes(c -> {
                Actions.setRakeback(DoubleArgumentType.getDouble(c, "pct"));
                return 1;
            })));

        LiteralArgumentBuilder<FabricClientCommandSource> hud = literal("hud");
        hud.then(literal("toggle").executes(c -> { Actions.toggleHud(); return 1; }));
        hud.then(literal("reset").executes(c -> { Actions.resetLayout(); return 1; }));
        hud.then(literal("scale").then(argument("n", FloatArgumentType.floatArg(0.5f, 3.0f)).executes(c -> {
            Actions.setHudScale(FloatArgumentType.getFloat(c, "n"));
            return 1;
        })));
        for (Widgets.W w : Widgets.W.values()) {
            hud.then(literal(w.id).executes(c -> { Actions.toggleWidget(w); return 1; }));
        }
        root.then(hud);

        d.register(root);
    }
}
