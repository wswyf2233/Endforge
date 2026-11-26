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
 * 死神镰刀·右键「血契」。
 *
 * <p>消耗自身生命（直到只剩 1 点）换取攻击力，持续 8 秒；
 * 期间生命被锁在 1 点，无法通过回血脱离险境。
 */
public final class BloodPactHandler {

    /** 持续时间：8 秒 */
    public static final int DURATION = 160;
    /** 每消耗 1 点生命换算的攻击力 */
    private static final double DAMAGE_PER_HP = 1.0D;
    /** 攻击力加成上限 */
    private static final double MAX_BONUS = 15.0D;

    private static final UUID MODIFIER_ID = UUID.fromString("4d5e6f70-8b9c-4d4e-9f50-6b7c8d9e0f1a");

    private static final class Pact {
        long endTick;
        double bonus;
    }

    private static final Map<UUID, Pact> ACTIVE = new HashMap<>();

    private BloodPactHandler() {
    }

    /** 开启血契，返回实际获得的攻击力加成 */
    public static double start(ServerPlayerEntity player) {
        float health = player.getHealth();
        if (health <= 2.0F) {
            return 0.0D;
        }
        double bonus = Math.min(MAX_BONUS, (health - 1.0F) * DAMAGE_PER_HP);

        Pact pact = ACTIVE.computeIfAbsent(player.getUuid(), id -> new Pact());
        pact.endTick = player.getWorld().getTime() + DURATION;
        pact.bonus = bonus;

        player.setHealth(1.0F);
        applyBonus(player, bonus);

        player.sendMessage(Text.translatable("message.wangsword.bloodpact.start",
                String.format("%.1f", bonus)).formatted(Formatting.DARK_RED), true);
        return bonus;
    }

    public static boolean isActive(PlayerEntity player) {
        Pact pact = ACTIVE.get(player.getUuid());
        return pact != null && player.getWorld().getTime() < pact.endTick;
    }

    /** 注册伤害免疫：血契期间不受任何伤害 */
    public static void init() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) -> {
                    if (!(entity instanceof ServerPlayerEntity player)) {
                        return true;
                    }
                    // 血契生效期间免疫一切伤害，等于把 1 点血锁死
                    return !isActive(player);
                });
    }
    /** 每 tick：锁血在 1 点，到期移除加成 */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Pact>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pact> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.isDead()) {
                it.remove();
                continue;
            }
            Pact pact = entry.getValue();
            long now = player.getWorld().getTime();

            if (now >= pact.endTick) {
                applyBonus(player, 0.0D);
                it.remove();
                player.sendMessage(Text.translatable("message.wangsword.bloodpact.end")
                        .formatted(Formatting.GRAY), true);
                continue;
            }

            // 生命锁定为 1 点：任何回血都会被立刻压回去
            if (player.getHealth() > 1.0F) {
                player.setHealth(1.0F);
            }
        }
    }

    private static void applyBonus(PlayerEntity player, double bonus) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        if (bonus > 0.0D) {
            instance.addTemporaryModifier(new EntityAttributeModifier(
                    MODIFIER_ID, "ws_blood_pact", bonus,
                    EntityAttributeModifier.Operation.ADDITION));
        }
    }
}