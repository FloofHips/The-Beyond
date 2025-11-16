package com.thebeyond.common.block;

import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AuroraciteBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public AuroraciteBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entitycollisioncontext) {
            Entity entity = entitycollisioncontext.getEntity();
            if (entity != null) {
                if (!(canEntityWalkOn(entity) && context.isAbove(Shapes.block(), pos, false))) {
                    return Shapes.empty();
                }
            }
        }

        return Shapes.block();
    }

    private boolean canEntityWalkOn(Entity entity) {
        if (entity instanceof AbyssalNomadEntity) {
            return true;
        } else {
            return entity instanceof LivingEntity ? ((LivingEntity)entity).getItemBySlot(EquipmentSlot.FEET).is(Items.GOLDEN_BOOTS) : false;
        }
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isCreative()) return Shapes.empty();
        return Shapes.block();
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {

        if (level instanceof ServerLevel serverLevel && !state.getValue(POWERED) && entity.getKnownMovement().length() > 0.1F) {
            level.setBlockAndUpdate(pos, state.setValue(POWERED, true));
            level.scheduleTick(pos, this, 20);
            serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5, 1, 0, 0.1, 0, 0);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlockAndUpdate(pos, state.setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
