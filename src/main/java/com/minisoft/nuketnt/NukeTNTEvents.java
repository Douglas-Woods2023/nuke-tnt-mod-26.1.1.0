package com.minisoft.nuketnt;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = NukeTNTMod.MODID)
public class NukeTNTEvents {

    /**
     * 检查玩家是否穿着全套铜甲
     */
    private static boolean hasFullCopperArmor(Player player) {
        // 获取玩家物品栏
        var inventory = player.getInventory();

        // 盔甲槽位索引：36=靴子, 37=护腿, 38=胸甲, 39=头盔
        ItemStack boots = inventory.getItem(36);
        ItemStack leggings = inventory.getItem(37);
        ItemStack chestplate = inventory.getItem(38);
        ItemStack helmet = inventory.getItem(39);

        return helmet.getItem() == Items.COPPER_HELMET &&
                chestplate.getItem() == Items.COPPER_CHESTPLATE &&
                leggings.getItem() == Items.COPPER_LEGGINGS &&
                boots.getItem() == Items.COPPER_BOOTS;
    }

    /**
     * 给玩家施加辐射效果（中毒II、恶心、虚弱、凋零）
     * 持续时间为 30 秒（600 ticks）
     */
    private static void applyRadiationEffects(Player player) {
        if (!player.isAlive()) return; // 玩家死亡则不再添加效果
        if (hasFullCopperArmor(player)) {
            return; // 穿着全套铜甲，免疫辐射
        }
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 600, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 0, false, false, true));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(NukeTNTMod.NUKE_BLOCK.get())) {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                var pos = event.getPos();
                var player = event.getPlayer();
                event.setCanceled(true);
                serverLevel.removeBlock(pos, false);

                // 爆炸破坏计算器（保持不变）
                ExplosionDamageCalculator damageCalculator = new ExplosionDamageCalculator() {
                    @Override
                    public boolean shouldBlockExplode(net.minecraft.world.level.Explosion explosion, net.minecraft.world.level.BlockGetter blockGetter, net.minecraft.core.BlockPos blockPos, BlockState blockState, float power) {
                        return !blockState.isAir() && blockState.getDestroySpeed(blockGetter, blockPos) >= 0;
                    }
                    @Override
                    public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, net.minecraft.world.entity.Entity entity) {
                        return true;
                    }
                    @Override
                    public Optional<Float> getBlockExplosionResistance(net.minecraft.world.level.Explosion explosion, net.minecraft.world.level.BlockGetter blockGetter, net.minecraft.core.BlockPos blockPos, BlockState blockState, FluidState fluidState) {
                        return Optional.of(Math.min(blockState.getBlock().getExplosionResistance(), 6.0F));
                    }
                };

                serverLevel.explode(player, null, damageCalculator,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        300.0F, true, Level.ExplosionInteraction.TNT);

                // 辐射效果范围
                double explosionRadius = 305.0;
                AABB affectedArea = new AABB(
                        pos.getX() - explosionRadius, pos.getY() - explosionRadius, pos.getZ() - explosionRadius,
                        pos.getX() + explosionRadius, pos.getY() + explosionRadius, pos.getZ() + explosionRadius
                );
                List<Entity> entitiesInRange = serverLevel.getEntities(null, affectedArea);
                for (Entity entity : entitiesInRange) {
                    if (entity instanceof Player affectedPlayer) {
                        applyRadiationEffects(affectedPlayer);
                    }
                }

                // 粒子效果（保持不变）
                for (int i = 0; i < 200; i++) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10,
                            serverLevel.getRandom().nextDouble() * 3 - 1.5,
                            serverLevel.getRandom().nextDouble() * 3,
                            serverLevel.getRandom().nextDouble() * 3 - 1.5, 0.5);
                }
                for (int i = 0; i < 100; i++) {
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5,
                            serverLevel.getRandom().nextDouble() * 2 - 1,
                            serverLevel.getRandom().nextDouble() * 2,
                            serverLevel.getRandom().nextDouble() * 2 - 1, 0.3);
                }
                for (int i = 0; i < 50; i++) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5,
                            serverLevel.getRandom().nextDouble() * 2 - 1,
                            serverLevel.getRandom().nextDouble() * 2,
                            serverLevel.getRandom().nextDouble() * 2 - 1, 0.5);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getItem().is(NukeTNTMod.NUKE_ITEM.get())) {
            if (event.getEntity() instanceof Player player) {
                applyRadiationEffects(player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("你吃了铀块！辐射侵蚀着你的身体！"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!player.isAlive()) return;

        // 检查主手或副手是否持有铀块
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean holdingUranium = (mainHand.getItem() == NukeTNTMod.NUKE_ITEM.get()) ||
                (offHand.getItem() == NukeTNTMod.NUKE_ITEM.get());

        if (holdingUranium) {
            applyRadiationEffects(player);
        }
    }
}