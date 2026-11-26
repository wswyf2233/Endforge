package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.registry.ItemsRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 阿瓦隆（剑鞘）的主动效果：展开「远离尘世的理想乡」。
 *
 * <p>背包里带着剑鞘时按 Z 发动：数秒内免疫一切伤害并持续回血，只保护自己。
 */
public class AvalonHandler {

    /** 持续时间（tick）：5 秒 */
    public static final int DURATION = 100;
    /** 冷却（tick）：90 秒 */
    public static final int COOLDOWN = 1800;
    /** 技能 ID：改走技能池才有右侧图标（90 秒太长，没图标等于盲等） */
    public static final String SKILL_AVALON = "avalon_open";
    /** 展开期间每秒回复的生命（1 点 = 半颗心） */
    public static final int HEAL_PER_SECOND = 4;

    public static final Identifier ACTIVATE_PACKET = new Identifier("wangsword", "avalon_activate");
    public static final Identifier START_PACKET = new Identifier("wangsword", "avalon_start");
    public static final Identifier END_PACKET = new Identifier("wangsword", "avalon_end");

    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(AvalonHandler::onServerTick);

        // 展开期间免疫一切伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity player && isActive(player)));

        // 掉线清状态，避免重连后残留无敌
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> clear(handler.getPlayer()));
    }

    /** 背包（含副手）里是否带着剑鞘 */
    public static boolean hasAvalon(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).getItem() == ItemsRegistry.WS_AVALON) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive(ServerPlayerEntity player) {
        Integer left = ACTIVE.get(player.getUuid());
        return left != null && left > 0;
    }

    /** 客户端按 Z：服务端校验背包与冷却后展开 */
    public static void handleActivate(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator() || !hasAvalon(player)) {
            return;
        }
        if (isActive(player)) {
            return;
        }
        if (!SkillCooldowns.isReady(player, SKILL_AVALON)) {
            SkillCooldowns.notifyCooldown(player, SKILL_AVALON, "阿瓦隆");
            return;
        }

        ACTIVE.put(player.getUuid(), DURATION);
        SkillCooldowns.start(player, SKILL_AVALON, COOLDOWN);

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 1.1F);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 0.7F);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(DURATION);
        ServerPlayNetworking.send(player, START_PACKET, buf);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Integer left = ACTIVE.get(player.getUuid());
            if (left == null) {
                continue;
            }

            if (left <= 0 || player.isDead()) {
                ACTIVE.remove(player.getUuid());
                if (left != null && left <= 0) {
                    ServerPlayNetworking.send(player, END_PACKET, PacketByteBufs.create());
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.2F);
                }
                continue;
            }

            // 每秒回 4 点
            if (left % 5 == 0) {
                player.heal(HEAL_PER_SECOND / 4.0F);
            }

            // 金色粒子：围着自己转一圈
            if (left % 2 == 0 && player.getWorld() instanceof net.minecraft.server.world.ServerWorld world) {
                double radius = 1.1D;
                double phase = left * 0.35D;
                for (int i = 0; i < 10; i++) {
                    double ang = phase + Math.PI * 2.0D * i / 10.0D;
                    world.spawnParticles(ParticleTypes.END_ROD,
                            player.getX() + Math.cos(ang) * radius,
                            player.getY() + 0.4D + (i % 5) * 0.32D,
                            player.getZ() + Math.sin(ang) * radius,
                            1, 0.0D, 0.01D, 0.0D, 0.0D);
                }
            }

            ACTIVE.put(player.getUuid(), left - 1);
        }
    }

    /** 玩家退出时清掉状态，避免重连后残留无敌 */
    public static void clear(ServerPlayerEntity player) {
        ACTIVE.remove(player.getUuid());
    }
}
