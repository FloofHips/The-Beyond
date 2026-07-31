package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public class BeadEntity extends LivingBlock {
    public BeadEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }
    @Override
    public void getProceduralShape() {
        int w, h ,d;

        switch (level().random.nextInt(4)) {
            case 0 : {
                w = 4;
                h = 4;
                d = 4;
                break;
            }
            case 1 : {
                w = 4;
                h = 12;
                d = 4;
                break;
            }
            case 2 : {
                w = 8;
                h = 12;
                d = 4;
                break;
            }

            default: {
                w = 8;
                h = 8;
                d = 8;
                break;
            }
        }

        this.setDimensions(new Vector3f(w / 16.0F, h / 16.0F, d / 16.0F));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }
}
