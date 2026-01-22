package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

import static com.thebeyond.common.block.BonfireBlock.LIT;

public class BonfireBlockEntity extends BlockEntity {
    private int activationTimer = -1;
    private Player activatingPlayer = null;
    public int birthday = 0;
    public BonfireBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
    public BonfireBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.BONFIRE.get(), pos, blockState);
    }

    public int getBirthday() {
        return birthday;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        birthday = tag.getInt("Birthday");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (getBlockState().getValue(LIT)) {
            tag.putInt("Birthday", birthday);
        }
    }

    public void activate(Player player) {
        if (activationTimer == -1 && level != null && !level.isClientSide) {
            this.birthday = (int) (level.getDayTime() / 24000);
            activationTimer = 200;
            activatingPlayer = player;
            setChanged();
        }
    }

    public boolean isItMyBirthdayToday() {
        int birthday = getBirthday();

        if (birthday != 0 && level != null) {
            return ((level.getDayTime() / 24000)) > birthday + 30;
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BonfireBlockEntity be) {
        if (be.activationTimer > 0) {
            be.activationTimer--;

            if (be.activationTimer % 5 == 0) {
                be.spawnLanterns();
            }

            if (be.activationTimer <= 0) {
                be.finishActivation();
            }

            be.setChanged();
        }
    }

    private void spawnLanterns() {
        if (level == null || activatingPlayer == null) return;

        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos center = worldPosition;

        AABB boundingBox = new AABB(center).inflate(20);

        List<LanternEntity> lanterns = serverLevel.getEntitiesOfClass(LanternEntity.class, boundingBox);
        int currentCount = lanterns.size();

        if (currentCount > 0) {
            LanternEntity randomLantern = lanterns.get(level.random.nextInt(lanterns.size()));
            serverLevel.sendParticles(ParticleTypes.HEART, randomLantern.getX() + 0, randomLantern.getY() + 0, randomLantern.getZ() + 0, 3, 0.25, 0.25, 0.25, 0.015);

        }

        if (currentCount < 10) {
            if ((level.random.nextFloat() < 0.9f)) {
                BlockPos spawnPos = BlockPos.randomInCube(getLevel().random, 1, center, 10).iterator().next();
                if (spawnPos != null) {
                    BlockPos newpos = spawnPos.atY(getBlockPos().getY());
                    LanternEntity.spawnSelf(serverLevel, newpos, activatingPlayer);
                }
            }
        }

        if (level.random.nextFloat() < 0.8f && (serverLevel.isThundering() ? true : activationTimer < 100)) {
            if (!lanterns.isEmpty()) {
                LanternEntity randomLantern = lanterns.get(level.random.nextInt(lanterns.size()));
                randomLantern.split(serverLevel, activatingPlayer);
            }
        }

    }

    private void finishActivation() {
        activationTimer = -1;
        activatingPlayer = null;
        setChanged();
    }
}
