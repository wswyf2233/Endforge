package org.examplee.wangsword.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.registry.EffectRegistry;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 联动：同时带着誓约胜利之剑与阿瓦隆时，维持「誓约的祝福」。
 *
 * <p>剑可以在手上也可以在背包里，剑鞘只要在背包里即可。
 */
public class ExcaliburSynergyHandler {

    /** 效果持续时间（tick），持续刷新 */
    private static final int DURATION = 100;
    /** 剩余时间低于这个值就续一次 */
    private static final int REFRESH_BELOW = 60;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ExcaliburSynergyHandler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || player.isDead()) {
                continue;
            }
            if (!hasItem(player, ItemsRegistry.WS_EXCALIBUR) || !hasItem(player, ItemsRegistry.WS_AVALON)) {
                continue;
            }

            StatusEffectInstance current = player.getStatusEffect(EffectRegistry.OATH_BLESSING);
            if (current == null || current.getDuration() < REFRESH_BELOW) {
                player.addStatusEffect(new StatusEffectInstance(
                        EffectRegistry.OATH_BLESSING, DURATION, 0, true, false, false));
            }
        }
    }

    private static boolean hasItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == item) {
                return true;
            }
        }
        return false;
    }
}
