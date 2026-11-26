package org.examplee.wangsword.util;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 末地合金剑「终焉姿态」状态管理。
 *
 * <p>持续 10 秒：攻击力 +35%，普通攻击命中附加 4 点虚空伤害（无视护甲）。
 * 期间若 1 秒内没有命中任何目标，则自身损失 1 点生命。
 */
public final class TaiguStanceHandler {

    /** 姿态持续时间：10 秒 */
    public static final int DURATION = 200;
    /** 未攻击扣血的间隔：1 秒 */
    private static final int DRAIN_INTERVAL = 20;
    /** 每次扣血量（1 颗心 = 2 点，这里 1 点） */
    private static final float DRAIN_AMOUNT = 1.0F;
    /** 攻击力加成 */
    private static final double DAMAGE_BONUS = 0.35D;

    private static final UUID MODIFIER_ID = UUID.fromString("3c4d5e6f-7a8b-4c3d-8e4f-5a6b7c8d9e0f");

    private static final class Stance {
        long endTick;
        long lastAttackTick;
        long lastDrainTick;
    }

    private static final Map<UUID, Stance> ACTIVE = new HashMap<>();

    private TaiguStanceHandler() {
    }

    /** 开启姿态 */
    public static void start(ServerPlayerEntity player) {
        long now = player.getWorld().getTime();
        Stance stance = ACTIVE.computeIfAbsent(player.getUuid(), id -> new Stance());
        stance.endTick = now + DURATION;
        stance.lastAttackTick = now;
        stance.lastDrainTick = now;

        applyBonus(player, true);

        player.sendMessage(Text.translatable("message.wangsword.taigu_stance.start")
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    public static boolean isActive(PlayerEntity player) {
        Stance stance = ACTIVE.get(player.getUuid());
        if (stance == null) {
            return false;
        }
        return player.getWorld().getTime() < stance.endTick;
    }


    public static void onAttack(PlayerEntity player) {
        Stance stance = ACTIVE.get(player.getUuid());
        if (stance != null) {
            stance.lastAttackTick = player.getWorld().getTime();
        }
    }

    /** 每 tick 维护：到期移除加成，未攻击则扣血 */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Stance>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Stance> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            long now = player.getWorld().getTime();
            Stance stance = entry.getValue();

            if (now >= stance.endTick) {
                applyBonus(player, false);
                it.remove();
                player.sendMessage(Text.translatable("message.wangsword.taigu_stance.end")
                        .formatted(Formatting.GRAY), true);
                continue;
            }

            // 1 秒内没有攻击 → 扣 1 点生命
            if (now - stance.lastAttackTick >= DRAIN_INTERVAL
                    && now - stance.lastDrainTick >= DRAIN_INTERVAL) {
                stance.lastDrainTick = now;
                player.damage(player.getDamageSources().magic(), DRAIN_AMOUNT);
            }
        }
    }

    private static void applyBonus(PlayerEntity player, boolean add) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        if (add) {
            instance.addTemporaryModifier(new EntityAttributeModifier(
                    MODIFIER_ID, "ws_taigu_stance", DAMAGE_BONUS,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

}