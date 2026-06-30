package com.thebeyond.mixin.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes undiscovered biomes (and hidden utility tags) parse as unknown, so typing one turns red and won't run.
 *  Client-side, because that's where command text gets validated. */
@Mixin(ResourceOrTagArgument.class)
public abstract class ResourceOrTagArgumentParseMixin {

    @Shadow @Final ResourceKey<? extends Registry<?>> registryKey;
    @Shadow @Final private static Dynamic2CommandExceptionType ERROR_UNKNOWN_TAG;

    @Inject(method = "parse", at = @At("HEAD"))
    private void the_beyond$gateParse(StringReader reader, CallbackInfoReturnable<?> cir) throws CommandSyntaxException {
        if (!BeyondAwareness.gateEnabled()) return;
        if (!Registries.BIOME.equals(this.registryKey)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int cursor = reader.getCursor();
        boolean isTag;
        ResourceLocation id;
        try {
            isTag = reader.canRead() && reader.peek() == '#';
            if (isTag) reader.skip();
            id = ResourceLocation.read(reader);
        } catch (CommandSyntaxException malformed) {
            reader.setCursor(cursor);   // let vanilla report its own parse error
            return;
        }

        boolean gated = isTag ? HiddenContentFilter.isUtilityLocateTag(id)
                              : HiddenContentFilter.isBiomeHidden(id, player);
        if (gated) {
            // Match vanilla's cursor: tag rewinds to the start, resource stays past the id.
            if (isTag) {
                reader.setCursor(cursor);
                throw ERROR_UNKNOWN_TAG.create(id, this.registryKey.location());
            }
            throw ResourceArgument.ERROR_UNKNOWN_RESOURCE.create(id, this.registryKey.location());
        }
        reader.setCursor(cursor);   // not gated → restore for vanilla parse
    }
}
