package com.thebeyond.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.awareness.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Op-only debug controls for the discovery gate. The awareness branch hides when the gate is off. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class BeyondAwarenessCommand {

    private BeyondAwarenessCommand() {}

    private static final List<ResourceLocation> CORE_KEYS = List.of(
            BeyondAwarenessKeys.FARLANDS_DISCOVERY,
            BeyondAwarenessKeys.WALL_PROXIMITY,
            BeyondAwarenessKeys.BEYOND_ACCESS);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // The bare .executes keeps /the_beyond working even when every subcommand is gated off.
        dispatcher.register(Commands.literal("the_beyond")
                .requires(src -> src.hasPermission(2))
                .executes(BeyondAwarenessCommand::usage)
                .then(Commands.literal("awareness")
                        .requires(src -> BeyondAwareness.gateEnabled())
                        .then(Commands.literal("grant")
                                .then(Commands.argument("key", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(grantable(), b))
                                        .executes(c -> grant(c, List.of(c.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(c -> grant(c, EntityArgument.getPlayers(c, "targets"))))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("key", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(knownOf(c), b))
                                        .executes(c -> revoke(c, List.of(c.getSource().getPlayerOrException())))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(c -> revoke(c, EntityArgument.getPlayers(c, "targets"))))))
                        .then(Commands.literal("list")
                                .executes(c -> list(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> list(c, EntityArgument.getPlayer(c, "target")))))
                        .then(Commands.literal("reset")
                                .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> reset(c, EntityArgument.getPlayers(c, "targets")))))));
    }

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("[the_beyond] subcommands: noise_dump")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** What you can grant: the region keys plus every gateable item. */
    private static Collection<ResourceLocation> grantable() {
        Set<ResourceLocation> s = new HashSet<>(CORE_KEYS);
        s.addAll(HiddenContentFilter.gateableItemIds());
        return s;
    }

    /** What you can revoke: whatever this player is already aware of. */
    private static Collection<ResourceLocation> knownOf(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        return p != null ? BeyondAwareness.knownSnapshot(p) : CORE_KEYS;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        CommandSourceStack src = ctx.getSource();
        ResourceLocation key = ResourceLocationArgument.getId(ctx, "key");
        int changed = 0;
        for (ServerPlayer sp : targets) if (BeyondAwareness.grant(sp, key)) changed++;
        int n = changed;
        src.sendSuccess(() -> Component.literal(
                "[the_beyond] Granted " + key + " (" + n + "/" + targets.size() + " changed).")
                .withStyle(ChatFormatting.GREEN), true);
        return changed;
    }

    private static int revoke(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        CommandSourceStack src = ctx.getSource();
        ResourceLocation key = ResourceLocationArgument.getId(ctx, "key");
        int changed = 0;
        for (ServerPlayer sp : targets) if (BeyondAwareness.revoke(sp, key)) changed++;
        int n = changed;
        src.sendSuccess(() -> Component.literal(
                "[the_beyond] Revoked " + key + " (" + n + "/" + targets.size() + " changed).")
                .withStyle(ChatFormatting.GREEN), true);
        return changed;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack src = ctx.getSource();
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(target);
        String name = target.getName().getString();
        if (known.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[the_beyond] " + name + " is aware of nothing.")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            String joined = known.stream().map(ResourceLocation::toString).sorted()
                    .collect(Collectors.joining(", "));
            src.sendSuccess(() -> Component.literal("[the_beyond] " + name + " is aware of: " + joined)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return known.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        CommandSourceStack src = ctx.getSource();
        int total = 0;
        for (ServerPlayer sp : targets) {
            // Copy first — the snapshot might be a live view of what we're mutating.
            for (ResourceLocation key : Set.copyOf(BeyondAwareness.knownSnapshot(sp))) {
                if (BeyondAwareness.revoke(sp, key)) total++;
            }
        }
        int n = total;
        src.sendSuccess(() -> Component.literal(
                "[the_beyond] Reset — revoked " + n + " key(s) across " + targets.size() + " player(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return total;
    }
}
