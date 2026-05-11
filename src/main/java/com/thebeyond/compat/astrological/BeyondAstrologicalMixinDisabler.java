package com.thebeyond.compat.astrological;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BeyondAstrologicalMixinDisabler implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BeyondAstrologicalDisabler");

    private static final List<String> HOSTILE_MIXINS = List.of(
        "com.Apothic0n.Astrological.mixin.ChorusPlantBlockMixin",
        "com.Apothic0n.Astrological.mixin.ChorusFlowerBlockMixin",
        "com.Apothic0n.Astrological.mixin.DimensionSpecialEffectsMixin",
        "com.Apothic0n.Astrological.mixin.DimensionTypeMixin"
    );

    private static final List<String> HOSTILE_RELATIVE = List.of(
        "ChorusPlantBlockMixin",
        "ChorusFlowerBlockMixin",
        "DimensionSpecialEffectsMixin",
        "DimensionTypeMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[Beyond] BeyondAstrologicalMixinDisabler onLoad fired (pkg={})", mixinPackage);
        tryDisable("onLoad");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        LOGGER.info("[Beyond] acceptTargets fired");
        tryDisable("acceptTargets");
    }

    @Override
    public List<String> getMixins() {
        tryDisable("getMixins");
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static synchronized void tryDisable(String hook) {
        try {
            Set<?> configs = Mixins.getConfigs();
            for (Object configWrapper : configs) {
                String name = safeName(configWrapper);
                if (!"mixins.astrological.json".equals(name)) continue;
                Object mixinConfig = unwrapConfig(configWrapper);
                if (mixinConfig == null) {
                    LOGGER.warn("[Beyond] unwrapConfig returned null for {}", name);
                    continue;
                }
                LOGGER.info("[Beyond] Targeting astrological config (hook={}, configClass={})",
                    hook, mixinConfig.getClass().getName());
                int removed = stripHostile(mixinConfig);
                LOGGER.info("[Beyond] Astrological strip result: {} entries removed (hook={})", removed, hook);
            }
        } catch (Throwable t) {
            LOGGER.warn("[Beyond] tryDisable failed (hook={})", hook, t);
        }
    }

    private static Object unwrapConfig(Object configWrapper) {
        try {
            Method get = configWrapper.getClass().getDeclaredMethod("get");
            get.setAccessible(true);
            return get.invoke(configWrapper);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safeName(Object configWrapper) {
        try {
            Method getName = configWrapper.getClass().getDeclaredMethod("getName");
            getName.setAccessible(true);
            Object name = getName.invoke(configWrapper);
            return name != null ? name.toString() : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static int stripHostile(Object mixinConfig) {
        int removed = 0;
        removed += removeStrings(mixinConfig, "mixinClasses");
        removed += removeStrings(mixinConfig, "mixinClassesClient");
        removed += removeStrings(mixinConfig, "mixinClassesServer");
        removed += removeInfos(mixinConfig, "pendingMixins");
        removed += removeInfos(mixinConfig, "mixins");
        return removed;
    }

    private static int removeStrings(Object cfg, String fieldName) {
        Field f = findField(cfg.getClass(), fieldName);
        if (f == null) {
            LOGGER.info("[Beyond] field {} not found", fieldName);
            return 0;
        }
        try {
            Object value = f.get(cfg);
            if (value == null) {
                LOGGER.info("[Beyond] field {} is null", fieldName);
                return 0;
            }
            if (!(value instanceof List<?> list)) {
                LOGGER.info("[Beyond] field {} is not a List ({})", fieldName, value.getClass().getName());
                return 0;
            }
            LOGGER.info("[Beyond] field {} size={} ({})", fieldName, list.size(), list);
            List<String> kept = new ArrayList<>(list.size());
            int removed = 0;
            for (Object item : list) {
                if (!(item instanceof String s)) {
                    kept.add(item == null ? null : item.toString());
                    continue;
                }
                if (isHostile(s)) {
                    LOGGER.info("[Beyond] removing {} from {}", s, fieldName);
                    removed++;
                } else {
                    kept.add(s);
                }
            }
            if (removed > 0) {
                f.set(cfg, kept);
            }
            return removed;
        } catch (Throwable t) {
            LOGGER.warn("[Beyond] removeStrings({}) failed", fieldName, t);
            return 0;
        }
    }

    private static int removeInfos(Object cfg, String fieldName) {
        Field f = findField(cfg.getClass(), fieldName);
        if (f == null) return 0;
        try {
            Object value = f.get(cfg);
            if (!(value instanceof List<?> list)) return 0;
            List<Object> kept = new ArrayList<>(list.size());
            int removed = 0;
            for (Object item : list) {
                if (item instanceof IMixinInfo info && isHostile(info.getClassName())) {
                    LOGGER.info("[Beyond] removing MixinInfo {} from {}", info.getClassName(), fieldName);
                    removed++;
                } else {
                    kept.add(item);
                }
            }
            if (removed > 0) {
                f.set(cfg, kept);
            }
            return removed;
        } catch (Throwable t) {
            LOGGER.warn("[Beyond] removeInfos({}) failed", fieldName, t);
            return 0;
        }
    }

    private static boolean isHostile(String name) {
        if (name == null) return false;
        if (HOSTILE_MIXINS.contains(name)) return true;
        int lastDot = name.lastIndexOf('.');
        String relative = lastDot >= 0 ? name.substring(lastDot + 1) : name;
        return HOSTILE_RELATIVE.contains(relative);
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
