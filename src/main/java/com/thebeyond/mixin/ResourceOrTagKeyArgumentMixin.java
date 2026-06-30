package com.thebeyond.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Hides undiscovered structures from {@code /locate structure} tab-complete. Server-side because clients don't have the structure registry. */
@Mixin(ResourceOrTagKeyArgument.class)
public abstract class ResourceOrTagKeyArgumentMixin {

    @Shadow @Final ResourceKey<? extends Registry<?>> registryKey;

    @Inject(method = "listSuggestions", at = @At("HEAD"), cancellable = true)
    private <S> void the_beyond$filterStructureSuggestions(
            CommandContext<S> context, SuggestionsBuilder builder,
            CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        if (!BeyondAwareness.gateEnabled()) return;
        if (!Registries.STRUCTURE.equals(this.registryKey)) return;
        if (!(context.getSource() instanceof CommandSourceStack css)) return;
        ServerPlayer player = css.getPlayer();
        if (player == null) return;
        Registry<Structure> reg = css.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Predicate<ResourceLocation> structureHidden = HiddenContentFilter.structureHiddenTest(player);
        SharedSuggestionProvider.suggestResource(reg.getTagNames().map(TagKey::location)
                .filter(id -> !HiddenContentFilter.isUtilityLocateTag(id)), builder, "#");
        cir.setReturnValue(SharedSuggestionProvider.suggestResource(
                reg.keySet().stream().filter(id -> !structureHidden.test(id)),
                builder));
    }
}
