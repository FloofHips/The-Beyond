package com.thebeyond.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/** Registers MovementBehaviours for Beyond blocks. Loaded only after {@code isLoaded("create")}. */
public final class BeyondCreateCompat {
    private BeyondCreateCompat() {}

    public static void register() {
        try {
            Block faucet = BeyondBlocks.MEMOR_FAUCET.get();
            Block bonfire = BeyondBlocks.BONFIRE.get();
            Block hut = BeyondBlocks.ENADRAKE_HUT.get();
            Block refuge = BeyondBlocks.REFUGE.get();

            MovementBehaviour.REGISTRY.register(faucet, new MemorFaucetMovementBehaviour());
            MovementBehaviour.REGISTRY.register(bonfire, new BonfireMovementBehaviour());
            MovementBehaviour.REGISTRY.register(hut, new EnadrakeHutMovementBehaviour());
            MovementBehaviour.REGISTRY.register(refuge, new RefugeMovementBehaviour());

            // Bypass Create's "destroySpeed=-1 → unmovable" fallback.
            Set<Block> assemblable = Set.of(faucet, bonfire, hut, refuge);
            BlockMovementChecks.registerMovementAllowedCheck((state, world, pos) ->
                    assemblable.contains(state.getBlock())
                            ? BlockMovementChecks.CheckResult.SUCCESS
                            : BlockMovementChecks.CheckResult.PASS);

            TheBeyond.LOGGER.info("[TheBeyond] Create MovementBehaviours registered (faucet, bonfire, hut, refuge).");
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[TheBeyond] Failed to register Create MovementBehaviours", t);
        }
    }
}
