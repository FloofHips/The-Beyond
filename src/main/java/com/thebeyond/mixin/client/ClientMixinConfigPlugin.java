package com.thebeyond.mixin.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates every mixin listed in {@code the_beyond.client.mixins.json} behind a
 * dedicated-server check so the config can be declared {@code required=true}
 * without crashing a server-side JVM that doesn't have {@code net.minecraft.client.*}
 * on its classpath.
 *
 * <h2>Why this works (verified against mixin-0.8.5 source)</h2>
 * <ol>
 *   <li>{@code MixinInfo.readDeclaredTargets} reads the {@code @Mixin} annotation
 *       off the mixin bytecode and, for each declared target, calls
 *       {@code shouldApplyMixin(ignorePlugin, targetName)} which delegates to
 *       {@link #shouldApplyMixin(String, String)} here. Targets we veto are
 *       dropped from {@code declaredTargets}.</li>
 *   <li>When every target is vetoed, the mixin never appears in any target class's
 *       mixin set → {@code MixinApplicatorStandard.apply} never iterates it →
 *       {@code MixinInfo.createContextFor} → {@code MixinPreProcessorStandard.attach}
 *       are never called → the "Attach error … ClassNotFoundException: net.minecraft.client.Minecraft"
 *       that was crashing the {@code test} task on the NeoForge JUnit bootstrap
 *       cannot fire.</li>
 * </ol>
 *
 * <p>The plugin itself must not reference any game/client class. Only FML-loader
 * classes ({@link FMLEnvironment}, {@link Dist}) and JDK/ASM/mixin types, all of
 * which are on the server classpath during bootstrap.
 *
 * <h2>Why a uniform client-gate rather than per-mixin gating</h2>
 * {@code the_beyond.client.mixins.json} contains only client-only mixins (see
 * its {@code mixins} array). If a non-client mixin is ever added there, it
 * should be moved to {@code the_beyond.mixins.json} instead — keeping the
 * config single-purpose lets this plugin be a one-liner.
 */
public class ClientMixinConfigPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // No-op. Do not log here: this method fires during mixin bootstrap,
        // before TheBeyond.LOGGER's slf4j binding has been wired in some
        // environments (datagen, JUnit), so logging can itself throw.
    }

    @Override
    public String getRefMapperConfig() {
        // Use the default refmap declared in the JSON ("the_beyond.refmap.json").
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // FMLEnvironment.dist is populated early enough that it's reliable by the
        // time Mixin starts resolving configs — FML sets it before loading mod
        // configs, including mixin configs. On the NeoForge JUnit runtime
        // (JUnitMain → Bootstrap.bootStrap) dist is DEDICATED_SERVER, which is
        // exactly the case we're guarding.
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No-op. We don't need to observe or mutate target sets across configs.
    }

    @Override
    public List<String> getMixins() {
        // No dynamically-added mixins. The JSON's "mixins" array is authoritative.
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No pre-apply transformations.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No post-apply transformations.
    }
}
