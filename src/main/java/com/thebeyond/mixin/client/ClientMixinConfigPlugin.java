package com.thebeyond.mixin.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Dist-client gate for {@code the_beyond.client.mixins.json}: vetoes every target on
 *  servers so the config can be {@code required=true} without {@code ClassNotFoundException}
 *  on {@code net.minecraft.client.*}. The plugin itself must reference no game classes. */
public class ClientMixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Do not log — LOGGER's slf4j binding may not be wired yet in datagen/JUnit bootstrap.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // FMLEnvironment.dist is set before mixin configs load, so it's reliable here.
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
