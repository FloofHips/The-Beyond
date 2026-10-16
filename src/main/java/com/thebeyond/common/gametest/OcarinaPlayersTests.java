package com.thebeyond.common.gametest;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.TrinketEntity;
import com.thebeyond.common.item.OcarinaItem;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(TheBeyond.MODID)
@PrefixGameTestTemplate(false)
public final class OcarinaPlayersTests {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private OcarinaPlayersTests() {
    }

    @GameTest(template = "projector_bench")
    public static void playersKeepTheirOwnTrinkets(final GameTestHelper helper) {
        ServerPlayer a = player(helper, new Vec3(1.5, 2, 1.5));
        ServerPlayer b = player(helper, new Vec3(3.5, 2, 3.5));
        List<TrinketEntity> ofA = trinkets(helper, a, new Vec3(0.5, 2, 0.5), new Vec3(1.5, 2, 0.5), new Vec3(0.5, 2, 1.5));
        List<TrinketEntity> ofB = trinkets(helper, b, new Vec3(4.5, 2, 4.5), new Vec3(3.5, 2, 4.5), new Vec3(4.5, 2, 3.5));

        play(a, false);
        log("A selects", a, ofA, b, ofB);
        helper.assertTrue(holds(a, ofA) && allSelected(ofA, true), "A did not select its own trinkets");

        play(b, false);
        log("B selects", a, ofA, b, ofB);
        helper.assertTrue(holds(b, ofB) && allSelected(ofB, true), "B did not select its own trinkets");
        helper.assertTrue(holds(a, ofA) && allSelected(ofA, true), "B's selection took A's trinkets");

        play(a, true);
        log("A deselects", a, ofA, b, ofB);
        helper.assertTrue(OcarinaItem.getLinkedTrinkets(a).isEmpty() && allSelected(ofA, false), "A kept trinkets after deselecting");
        helper.assertTrue(holds(b, ofB) && allSelected(ofB, true), "A's deselection released B's trinkets");

        helper.getLevel().getServer().getPlayerList().remove(a);
        helper.getLevel().getServer().getPlayerList().remove(b);
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(helper.absoluteVec(at));
        return player;
    }

    private static List<TrinketEntity> trinkets(GameTestHelper helper, ServerPlayer owner, Vec3... at) {
        return java.util.Arrays.stream(at).map(pos -> {
            TrinketEntity trinket = helper.spawn(BeyondEntityTypes.TRINKET.get(), pos);
            trinket.setOwner(owner.getUUID());
            return trinket;
        }).toList();
    }

    private static void play(ServerPlayer player, boolean sneaking) {
        ItemStack ocarina = new ItemStack(BeyondItems.OCARINA.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, ocarina);
        player.setShiftKeyDown(sneaking);
        ocarina.getItem().releaseUsing(ocarina, player.level(), player, ocarina.getUseDuration(player));
        player.setShiftKeyDown(false);
    }

    private static boolean holds(ServerPlayer player, List<TrinketEntity> trinkets) {
        return new HashSet<>(OcarinaItem.getLinkedTrinkets(player)).equals(new HashSet<>(trinkets));
    }

    private static boolean allSelected(List<TrinketEntity> trinkets, boolean selected) {
        return trinkets.stream().allMatch(t -> t.isSelected() == selected);
    }

    private static void log(String step, ServerPlayer a, List<TrinketEntity> ofA, ServerPlayer b, List<TrinketEntity> ofB) {
        LOGGER.info("[gametest] ocarina {}: A linked={} own selected={}, B linked={} own selected={}", step,
                OcarinaItem.getLinkedTrinkets(a).size(), ofA.stream().filter(TrinketEntity::isSelected).count(),
                OcarinaItem.getLinkedTrinkets(b).size(), ofB.stream().filter(TrinketEntity::isSelected).count());
    }
}
