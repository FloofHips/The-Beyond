package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.PerkaStalkMouthBlock;
import com.thebeyond.common.entity.StalkerEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class PerkaStalkMouthBlockEntity extends BlockEntity implements GameEventListener.Provider<PerkaStalkMouthBlockEntity.StalkListener> {
    private final PerkaStalkMouthBlockEntity.StalkListener stalkListener;
    public int cooldownTicks = 200;
    public boolean wasActivated = false;

    public PerkaStalkMouthBlockEntity(BlockPos pos, BlockState blockState) {
        super(BeyondBlockEntities.PERKA_STALK_MOUTH.get(), pos, blockState);
        this.stalkListener = new PerkaStalkMouthBlockEntity.StalkListener(new BlockPositionSource(pos));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PerkaStalkMouthBlockEntity be) {
        if (be.wasActivated && be.cooldownTicks > 0) be.cooldownTicks--;
        if (be.wasActivated && be.cooldownTicks == 0) {
            be.cooldownTicks = 200;
            be.wasActivated = false;
        }
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tag.putInt("cooldown", this.cooldownTicks);
        tag.putBoolean("wasActivated", this.wasActivated);
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        this.cooldownTicks = tag.getInt("cooldown");
        this.wasActivated = tag.getBoolean("wasActivated");
        super.saveAdditional(tag, registries);
    }

    public PerkaStalkMouthBlockEntity.StalkListener getListener() {
        return this.stalkListener;
    }

    public static class StalkListener implements GameEventListener {
        private final PositionSource positionSource;

        public StalkListener(PositionSource positionSource) {
            this.positionSource = positionSource;
        }

        public PositionSource getListenerSource() {
            return this.positionSource;
        }

        public int getListenerRadius() {
            return 8;
        }

        public GameEventListener.DeliveryMode getDeliveryMode() {
            return DeliveryMode.BY_DISTANCE;
        }

        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, GameEvent.Context context, Vec3 pos) {
            if (level.random.nextFloat() < 0.95f) return false;
            if (gameEvent.is(GameEventTags.VIBRATIONS)) {
                if (context.sourceEntity() != null) {
                    if (context.sourceEntity() instanceof StalkerEntity) return false;
                    if (level.random.nextBoolean() && !(context.sourceEntity() instanceof Player)) return false;
                }
                Optional<Vec3> position = positionSource.getPosition(level);
                if (position.isPresent()) {
                    BlockPos containing = BlockPos.containing(position.get());
                    BlockEntity be = level.getBlockEntity(containing);
                    if (be instanceof PerkaStalkMouthBlockEntity ps) {
                        if (ps.wasActivated) return false;
                        ps.wasActivated = true;
                    }
                    PerkaStalkMouthBlock.spawnStalker(level, containing, pos);
                }
                return true;
            }
            return false;
        }
    }
}

