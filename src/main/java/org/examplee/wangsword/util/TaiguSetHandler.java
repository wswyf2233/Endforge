package org.examplee.wangsword.util;

import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.TaiguArmorRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 末地合金盔甲套装被动。
 *
 * <ul>
 *   <li>穿齐四件：持续抗性提升 III</li>
 *   <li>穿齐四件：免疫摔落伤害</li>
 *   <li>穿齐四件：创造模式飞行</li>
 *   <li>穿齐四件：致命伤害免死一次（保留 1 血 + 2 秒无敌，冷却 5 分钟）</li>
 * </ul>
 */
public final class TaiguSetHandler {

    /** 抗性提升 III（amplifier 2） */
    private static final int RESIST_AMPLIFIER = 2;

    /** 濒死护盾冷却（tick）：5 分钟 */
    private static final long SHIELD_COOLDOWN = 6000L;

    /** 无敌时间（tick）：2 秒 */
    private static final int SHIELD_INVULNERABLE = 40;

    /** 存档里的模块名 */
    private static final String STORE_KEY = "taigu_shield";

    private static final Map<UUID, Long> SHIELD_READY = new HashMap<>();

    private TaiguSetHandler() {
    }

    public static void init() {
        // 每 tick 维护抗性与飞行
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tickPlayer(player);
            }
        });

        // 免摔落 + 濒死护盾
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (!(entity instanceof ServerPlayerEntity player)) {
                        return true;
                    }
                    if (!TaiguArmorRegistry.isFullSet(player)) {
                        return true;
                    }

                    // 免疫摔落
                    if (source.isOf(DamageTypes.FALL)) {
                        return false;
                    }

                    // 致命伤害 → 濒死护盾
                    if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
                        return true;
                    }
                    if (player.getHealth() - amount <= 0.0F && tryShield(player)) {
                        return false;
                    }
                    return true;
                });

        // 关存档时丢掉内存缓存（冷却本身在存档里）
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
                .register(server -> SHIELD_READY.clear());
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        boolean full = TaiguArmorRegistry.isFullSet(player);

        // 抗性提升 III：常驻
        if (full) {
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
            if (current == null || current.getAmplifier() < RESIST_AMPLIFIER || current.getDuration() < 40) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE, 60, RESIST_AMPLIFIER, false, false, true));
            }
        }

        // 创造级飞行
        if (full) {
            if (!player.getAbilities().allowFlying) {
                player.getAbilities().allowFlying = true;
                player.sendAbilitiesUpdate();
            }
        } else if (!player.isCreative() && !player.isSpectator() && player.getAbilities().allowFlying) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
            // 防止空中脱下直接摔死
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOW_FALLING, 100, 0, false, false, true));
        }
    }

    /** 触发濒死护盾；返回是否成功触发 */
    private static boolean tryShield(ServerPlayerEntity player) {
        World world = player.getWorld();
        long now = GameTime.now(player);
        // 冷却写进存档：退出重进不能把 5 分钟冷却刷掉
        Long cached = SHIELD_READY.get(player.getUuid());
        long readyAt;
        if (cached != null) {
            readyAt = cached;
        } else {
            readyAt = PlayerStore.get(player, STORE_KEY).getLong("ready_at");
            SHIELD_READY.put(player.getUuid(), readyAt);
        }
        if (now < readyAt) {
            return false;
        }
        SHIELD_READY.put(player.getUuid(), now + SHIELD_COOLDOWN);
        NbtCompound nbt = new NbtCompound();
        nbt.putLong("ready_at", now + SHIELD_COOLDOWN);
        PlayerStore.put(player, STORE_KEY, nbt);

        player.setHealth(1.0F);
        player.clearStatusEffects();
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, SHIELD_INVULNERABLE, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.FIRE_RESISTANCE, SHIELD_INVULNERABLE, 0, false, false, true));

        // 原版图腾的客户端表现：实体状态 35 → 客户端自己播图腾音效 + 生成 30 颗粒子。
        // 千万不要用 GameStateChangeS2CPacket.GAME_WON 来"模拟"这个效果——
        // 那个包在客户端会直接 setScreen(new CreditsScreen(...))，也就是终末之诗。
        world.sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING);
        player.sendMessage(Text.translatable("message.wangsword.taigu_shield")
                .formatted(Formatting.LIGHT_PURPLE), true);
        return true;
    }
}