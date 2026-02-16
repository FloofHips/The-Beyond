package com.thebeyond.common.entity;

import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.data.assets.BlockStates;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

import java.awt.*;

public class GravistarEntity extends ThrowableItemProjectile {
    public GravistarEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        BlockState b = this.level().getBlockState(result.getBlockPos());
        Color c = new Color(b.getMapColor(this.level(), result.getBlockPos()).col);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new PixelColorTransitionOptions(
                    new Vector3f(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f),
                    new Vector3f(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f),
                    2f
            ), this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(10, 20), 0.2,0.2,0.2,0.05);
        }
    }

    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            AABB aabb = this.getBoundingBox().inflate(4.0F, 2.0F, 4.0F);
            for(LivingEntity livingentity : this.level().getEntitiesOfClass(LivingEntity.class, aabb, LivingEntity::isAlive)) {
                int i = 0;
                if (livingentity.hasEffect(BeyondEffects.WEIGHTLESS)) {
                    i = livingentity.getEffect(BeyondEffects.WEIGHTLESS).getDuration();
                }
                livingentity.addEffect(new MobEffectInstance(BeyondEffects.WEIGHTLESS, i+600, 1));
            }
            this.discard();
            for(int i = 0; (float)i < 16.0F; ++i) {
                float f2 = this.random.nextFloat() * ((float)Math.PI * 2F);
                float f3 = this.random.nextFloat() * 0.5F + 0.5F;
                float f4 = Mth.sin(f2) * f3;
                float f5 = Mth.cos(f2) * f3;
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, BeyondItems.GRAVISTAR.toStack()), this.getX() + (double)f4, this.getY(), this.getZ() + (double)f5, (double)0.0F, (double)0.0F, (double)0.0F);
            }
            this.playSound(SoundEvents.GLASS_BREAK, 1.0F, 1.0F);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ColorUtils.voidOptions, this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(10, 20), 0.2,0.2,0.2,0.05);
            }
        }
    }

    @Override
    protected Item getDefaultItem() {
        return BeyondItems.GRAVISTAR.asItem();
    }
}
