package com.thebeyond.mixin.client;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Hides undiscovered biomes from tab-complete. Client-side because that's where suggestions get built - the real gate lives on the server. */
@Mixin(ResourceOrTagArgument.class)
public abstract class ResourceOrTagArgumentMixin {

    @Shadow @Final private HolderLookup<?> registryLookup;
    @Shadow @Final ResourceKey<? extends Registry<?>> registryKey;

    @Inject(method = "listSuggestions", at = @At("HEAD"), cancellable = true)
    private <S> void the_beyond$filterBiomeSuggestions(
            CommandContext<S> context, SuggestionsBuilder builder,
            CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        if (!BeyondAwareness.gateEnabled()) return;
        if (!Registries.BIOME.equals(this.registryKey)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Predicate<ResourceLocation> biomeHidden = HiddenContentFilter.biomeHiddenTest(player);
        SharedSuggestionProvider.suggestResource(
                this.registryLookup.listTagIds().map(TagKey::location)
                        .filter(id -> !HiddenContentFilter.isUtilityLocateTag(id)), builder, "#");
        cir.setReturnValue(SharedSuggestionProvider.suggestResource(
                this.registryLookup.listElementIds()
                        .map(ResourceKey::location)
                        .filter(id -> !biomeHidden.test(id)),
                builder));
    }
}
