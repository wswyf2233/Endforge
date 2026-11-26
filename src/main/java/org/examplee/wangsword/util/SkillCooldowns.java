package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 技能冷却的唯一权威。
 *
 * <p><b>为什么不直接用 {@code ItemCooldownManager}：</b>那是「物品」级冷却，
 * 一件武器只有一个槽，同一把武器的两个技能会互相锁（火刀的火柱锁住火射、
 * 太刀的万雷锁住雷怒），而且原版会连带拒掉这件物品的所有右键使用。
 * 这里按「技能 ID」算，每把武器想挂几个技能就挂几个。
 *
 * <p><b>客户端怎么知道剩余时间：</b>冷却只存在服务端，技能真正放出去时推一个
 * {@link #SYNC_PACKET}（skillId + 冷却 tick）过去，客户端据此倒计时画图标。
 * 所以图标只在「用过一次之后」才有意义，正好符合需求——没用过的技能本来就是就绪的。
 */
public final class SkillCooldowns {

    /** 下行包：skillId（字符串）+ 冷却 tick（varInt） */
    public static final Identifier SYNC_PACKET = new Identifier("wangsword", "skill_cd");

    /** 玩家 → (技能 ID → 可以再放的时刻) */
    private static final Map<UUID, Map<String, Long>> READY_AT = new HashMap<>();

    private SkillCooldowns() {
    }

    /** 只注册下线清理，别无他事 */
    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> clear(handler.getPlayer().getUuid()));
    }

    public static boolean isReady(ServerPlayerEntity player, String skillId) {
        Map<String, Long> map = READY_AT.get(player.getUuid());
        if (map == null) {
            return true;
        }
        Long readyAt = map.get(skillId);
        return readyAt == null || player.getWorld().getTime() >= readyAt;
    }

    /** 冷却还剩几秒（向上取整）；就绪返回 0 */
    public static int remainingSeconds(ServerPlayerEntity player, String skillId) {
        Map<String, Long> map = READY_AT.get(player.getUuid());
        if (map == null) {
            return 0;
        }
        Long readyAt = map.get(skillId);
        if (readyAt == null) {
            return 0;
        }
        long left = readyAt - player.getWorld().getTime();
        return left <= 0L ? 0 : (int) Math.ceil(left / 20.0D);
    }

    /** 冷却中：动作栏提示「XX · 冷却中 N 秒」 */
    public static void notifyCooldown(ServerPlayerEntity player, String skillId, String label) {
        int left = remainingSeconds(player, skillId);
        if (left > 0) {
            player.sendMessage(Text.translatable("message.wangsword.skill.cooldown", label, left), true);
        }
    }

    /** 进入冷却：记时间并同步客户端 */
    public static void start(ServerPlayerEntity player, String skillId, int cooldownTicks) {
        READY_AT.computeIfAbsent(player.getUuid(), k -> new HashMap<>())
                .put(skillId, player.getWorld().getTime() + cooldownTicks);
        sync(player, skillId, cooldownTicks);
    }

    /** 一步到位：就绪则进冷却并返回 true；冷却中发提示返回 false */
    public static boolean tryUse(ServerPlayerEntity player, String skillId, String label, int cooldownTicks) {
        if (!isReady(player, skillId)) {
            notifyCooldown(player, skillId, label);
            return false;
        }
        start(player, skillId, cooldownTicks);
        return true;
    }

    public static void clear(UUID uuid) {
        READY_AT.remove(uuid);
    }

    private static void sync(ServerPlayerEntity player, String skillId, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(skillId);
        buf.writeVarInt(ticks);
        ServerPlayNetworking.send(player, SYNC_PACKET, buf);
    }
}
