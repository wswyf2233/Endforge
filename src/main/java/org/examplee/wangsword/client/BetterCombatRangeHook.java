package org.examplee.wangsword.client;

import net.bettercombat.api.client.AttackRangeExtensions;
import net.minecraft.client.MinecraftClient;

/**
 * 把终焉姿态的 +1.5 格距离接到 Better Combat 的攻击距离计算上。
 *
 * <p>单独成类，只有确认安装了 Better Combat 才会加载，避免缺 mod 时类加载失败。
 */
public final class BetterCombatRangeHook {

    private BetterCombatRangeHook() {
    }

    public static void register() {
        AttackRangeExtensions.register(context -> new AttackRangeExtensions.Modifier(
                context.player() == MinecraftClient.getInstance().player
                        ? TaiguStanceClient.extraRange()
                        : 0.0D,
                AttackRangeExtensions.Operation.ADD));
    }
}