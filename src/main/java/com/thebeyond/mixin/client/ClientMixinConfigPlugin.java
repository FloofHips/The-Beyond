package com.thebeyond.mixin.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates every mixin in {@code the_beyond.client.mixins.json} behind a dist-client check
 * so the config can be {@code required=true} without crashing dedicated-server JVMs
 * (where {@code net.minecraft.client.*} is absent).
 *
 * <p>Vetoing every declared target at {@link #shouldApplyMixin} prevents mixin attach from
 * ever loading the target bytecode, so {@code ClassNotFoundException: Minecraft} cannot
 * fire during the NeoForge JUnit bootstrap. The plugin must not reference any game class
 * itself — only FML-loader, JDK, ASM, and mixin types (all server-classpath safe).
 *
 * <p>Client-only config is assumed: if a non-client mixin is added, move it to
 * {@code the_beyond.mixins.json} rather than complicating this gate.
 */
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
