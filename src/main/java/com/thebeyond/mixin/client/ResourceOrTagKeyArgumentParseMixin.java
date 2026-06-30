package com.thebeyond.mixin.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
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

/** Makes undiscovered structures (and hidden utility tags) parse as unknown, like the biome gate does.
 *  We check gating client-side since the structure registry isn't synced to the client. */
@Mixin(ResourceOrTagKeyArgument.class)
public abstract class ResourceOrTagKeyArgumentParseMixin {

    @Shadow @Final ResourceKey<? extends Registry<?>> registryKey;

    @Inject(method = "parse", at = @At("HEAD"))
    private void the_beyond$gateParse(StringReader reader, CallbackInfoReturnable<?> cir) throws CommandSyntaxException {
        if (!BeyondAwareness.gateEnabled()) return;
        if (!Registries.STRUCTURE.equals(this.registryKey)) return;
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
            reader.setCursor(cursor);
            return;
        }

        boolean gated = isTag ? HiddenContentFilter.isUtilityLocateTag(id)
                              : HiddenContentFilter.isStructureHiddenClient(id, player);
        if (gated) {
            throw ResourceArgument.ERROR_UNKNOWN_RESOURCE.create(id, this.registryKey.location());
        }
        reader.setCursor(cursor);
    }
}
