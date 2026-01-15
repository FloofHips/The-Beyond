package com.thebeyond.util;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public class AOEManager {
    public static void knockback(Level level, Player player, Entity entity) {
        level.levelEvent(2013, entity.getOnPos(), 750);
        level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate((double)3.5F), knockbackPredicate(player, entity)).forEach((p_347296_) -> {
            Vec3 vec3 = p_347296_.position().subtract(entity.position());
            double d0 = getKnockbackPower(player, p_347296_, vec3);
            Vec3 vec31 = vec3.normalize().scale(d0);
            if (d0 > (double)0.0F) {
                p_347296_.push(vec31.x, (double)0.7F, vec31.z);
                if (p_347296_ instanceof ServerPlayer) {
                    ServerPlayer serverplayer = (ServerPlayer)p_347296_;
                    serverplayer.connection.send(new ClientboundSetEntityMotionPacket(serverplayer));
                }
            }

        });
    }

    public static Predicate<LivingEntity> knockbackPredicate(Player player, Entity entity) {
        return (p_344407_) -> {
            boolean flag;
            boolean flag1;
            boolean flag2;
            boolean flag6;
            label62: {
                flag = !p_344407_.isSpectator();
                flag1 = p_344407_ != player && p_344407_ != entity;
                flag2 = !player.isAlliedTo(p_344407_);
                if (p_344407_ instanceof TamableAnimal tamableanimal) {
                    if (tamableanimal.isTame() && player.getUUID().equals(tamableanimal.getOwnerUUID())) {
                        flag6 = true;
                        break label62;
                    }
                }

                flag6 = false;
            }

            boolean flag3;
            label55: {
                flag3 = !flag6;
                if (p_344407_ instanceof ArmorStand armorstand) {
                    if (armorstand.isMarker()) {
                        flag6 = false;
                        break label55;
                    }
                }

                flag6 = true;
            }

            boolean flag5 = entity.distanceToSqr(p_344407_) <= Math.pow((double)3.5F, (double)2.0F);
            return flag && flag1 && flag2 && flag3 && flag6 && flag5;
        };
    }

    public static double getKnockbackPower(Player player, LivingEntity entity, Vec3 entityPos) {
        return ((double)3.5F - entityPos.length()) * (double)0.7F * (double)(player.fallDistance > 5.0F ? 2 : 1) * ((double)1.0F - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }
}
