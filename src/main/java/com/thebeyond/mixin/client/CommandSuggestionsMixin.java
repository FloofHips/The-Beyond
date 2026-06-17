package com.thebeyond.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Hides the {@code awareness} debug branch from {@code /the_beyond} tab-complete but keeps it typeable - we just
 *  drop it from the parent's suggestions, the node stays in the tree. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @WrapOperation(
        method = "updateCommandInfo",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Suggestions> the_beyond$hideAwareness(
            CommandDispatcher<SharedSuggestionProvider> dispatcher, ParseResults<SharedSuggestionProvider> parse,
            int cursor, Operation<CompletableFuture<Suggestions>> original) {
        CompletableFuture<Suggestions> future = original.call(dispatcher, parse, cursor);
        SuggestionContext<SharedSuggestionProvider> ctx;
        try {
            ctx = parse.getContext().findSuggestionContext(cursor);
        } catch (Exception ignored) {
            return future;   // brigadier couldn't resolve the cursor context — leave suggestions alone
        }
        if (ctx.parent == null || !"the_beyond".equals(ctx.parent.getName())) return future;
        return future.thenApply(suggestions -> {
            List<Suggestion> kept = suggestions.getList().stream()
                    .filter(s -> !"awareness".equals(s.getText()))
                    .toList();
            return kept.size() == suggestions.getList().size()
                    ? suggestions
                    : new Suggestions(suggestions.getRange(), kept);
        });
    }
}
