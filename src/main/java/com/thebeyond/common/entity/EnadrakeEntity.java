package com.thebeyond.common.entity;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.EventHooks;

public class EnadrakeEntity extends PathfinderMob {
    public EnadrakeEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1));
        this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 3f));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, EnderglopEntity.class, true));
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.20000000298023224);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if(source.getEntity() instanceof LivingEntity livingEntity){
            if(livingEntity.level().isClientSide){
                livingEntity.level().playLocalSound(livingEntity, SoundEvents.HORSE_DEATH, SoundSource.HOSTILE, 0.5f, 1);
                livingEntity.level().playLocalSound(livingEntity, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2, 2);
            }
            this.lookAt(livingEntity, 180, 180);
            livingEntity.addEffect(new MobEffectInstance(BeyondEffects.DEAFENED, 200));
        }
        return super.hurt(source, amount);
    }


    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BONE_MEAL)) {
            itemstack.consume(1, player);
            if(this.level() instanceof ServerLevel level)
                this.growUp(level);
            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    private void growUp(ServerLevel level) {
        if (this.random.nextInt(3) == 0) {
            this.navigation.stop();
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((p_258973_) -> {
                return p_258973_.getHolder(BeyondFeatures.OBIROOT.getId());
            }).ifPresent((p_255669_) -> {
                if(((ConfiguredFeature)p_255669_.value()).place(level, level.getChunkSource().getGenerator(), random, BlockPos.containing(this.position())))
                    this.discard();
            });
        }
    }
}
