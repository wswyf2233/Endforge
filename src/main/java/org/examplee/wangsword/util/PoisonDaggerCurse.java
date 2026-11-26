package org.examplee.wangsword.util;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 死花短刀的诅咒奖励。
 *
 * <p>三种情况各奖励 1 颗钻石：
 * <ol>
 *   <li>被这把刀的反噬打死</li>
 *   <li>反噬累计扣满 20 点生命（每满 20 点结算一次，余数保留）</li>
 *   <li>持有这把刀时触发不死图腾</li>
 * </ol>
 *
 * <p>所有结算都以「实际损失的生命」为准：护甲减伤、伤害吸收、无敌帧、
 * 已经被挡住/取消的伤害都不会计入，避免刷取。
 */
public final class PoisonDaggerCurse {

    /** 每累计损失多少生命奖励一颗钻石 */
    private static final float THRESHOLD = 20.0F;

    /** 玩家 → 已累计损失的生命（未满 20 的部分） */
    private static final Map<UUID, Float> LOST = new HashMap<>();

    private PoisonDaggerCurse() {
    }

    /**
     * 施加一次反噬伤害，并结算奖励。
     *
     * @return 实际造成的生命损失
     */
    public static float applyBacklash(ServerPlayerEntity player, float amount) {
        if (amount <= 0.0F || player.isDead() || player.isSpectator()) {
            return 0.0F;
        }
        // 只算真正扣掉的血量：不超过当前生命
        float health = player.getHealth();
        float actual = Math.min(health, amount);
        if (actual <= 0.0F) {
            return 0.0F;
        }

        boolean lethal = health - actual <= 0.0F;
        player.damage(player.getDamageSources().magic(), actual);

        // 累计扣血，每满 20 点结算一颗
        float total = LOST.getOrDefault(player.getUuid(), 0.0F) + actual;
        int granted = 0;
        while (total >= THRESHOLD) {
            total -= THRESHOLD;
            granted++;
        }
        LOST.put(player.getUuid(), total);
        if (granted > 0) {
            give(player, new ItemStack(org.examplee.wangsword.registry.ItemsRegistry.WS_DEATHCORESHARD, granted));
        }

        // 被反噬打死：额外一颗
        if (lethal || player.isDead()) {
            give(player, new ItemStack(org.examplee.wangsword.registry.ItemsRegistry.WS_DEATHCORESHARD));
        }
        return actual;
    }

    /** 触发不死图腾时的奖励（由 mixin 调用） */
    public static void onTotemTriggered(ServerPlayerEntity player) {
        give(player, new ItemStack(org.examplee.wangsword.registry.ItemsRegistry.WS_DEATHCORESHARD));
    }

    /** 玩家死亡时清空累计，避免跨死亡继承进度 */
    public static void reset(UUID playerId) {
        LOST.remove(playerId);
    }

    /** 每 tick 清理死亡/离线玩家的累计，防止跨死亡继承进度 */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (LOST.isEmpty()) {
            return;
        }
        java.util.Iterator<UUID> it = LOST.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null || player.isDead() || player.isRemoved()) {
                it.remove();
            }
        }
    }

    /** 发放奖励：优先进背包，放不下就掉在脚下 */
    private static void give(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().insertStack(stack)) {
            ItemEntity drop = player.dropItem(stack, false);
            if (drop != null) {
                drop.setPickupDelay(0);
            }
        }
    }
}
