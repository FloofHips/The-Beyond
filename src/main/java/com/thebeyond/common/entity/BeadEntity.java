package com.thebeyond.common.entity;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockShapeFactory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeadEntity extends LivingBlock {

    private static final int[][] SILHOUETTES = {
            {4, 4, 4},
            {4, 12, 4},
            {8, 12, 4},
            {8, 8, 8}
    };

    public BeadEntity(final EntityType<? extends Mob> type, final Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    @Override
    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        int[] size = SILHOUETTES[random.nextInt(SILHOUETTES.length)];
        double w = size[0] / 16.0;
        double h = size[1] / 16.0;
        double d = size[2] / 16.0;

        if (!entropic) {
            return Shapes.box(0.0, 0.0, 0.0, w, h, d);
        }

        AABB core = new AABB((1.0 - w) * 0.5, (1.0 - h) * 0.5, (1.0 - d) * 0.5,
                (1.0 + w) * 0.5, (1.0 + h) * 0.5, (1.0 + d) * 0.5);
        return LivingBlockShapeFactory.growEntropicFrom(random, core);
    }
}
