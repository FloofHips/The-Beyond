package com.thebeyond.common.entity;

import com.thebeyond.client.particle.CloudColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

public class SmokeFuseEntity extends ThrowableItemProjectile {

    public SmokeFuseEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);

        int col = DyedItemColor.getOrDefault(this.getItem(), 0);
        float r = ((col >> 16) & 0xFF) / 255f;
        float g = ((col >> 8) & 0xFF) / 255f;
        float b = (col & 0xFF) / 255f;

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new CloudColorTransitionOptions(
                    new Vector3f(r, g, b),
                    new Vector3f(r+0.1f, g+0.1f, b+0.1f),
                    2.5f
            ), this.getX(),this.getY()+1,this.getZ(),50,0.8,0.8,0.8,0.01);
            serverLevel.sendParticles(new SmokeColorTransitionOptions(
                    new Vector3f(r, g, b),
                    new Vector3f(r+0.1f, g+0.1f, b+0.1f),
                    2.5f
            ), this.getX(),this.getY()+1,this.getZ(),15,1.1,1.1,1.1,0.02);
        }
        this.discard();
    }

    @Override
    protected Item getDefaultItem() {
        return BeyondItems.SMOKE_FUSE.asItem();
    }
}
