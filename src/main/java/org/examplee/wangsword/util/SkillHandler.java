package org.examplee.wangsword.util;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.item.ISkillWeapon;

public class SkillHandler {

    /**
     * 处理 X 键大招请求。
     *
     * @param expectedItemId 客户端发请求时手持的物品 ID；和当前手持不一致就直接丢弃，
     *                       防止"放完弩的技能后切到长枪，长枪被顺带触发"这类串味问题
     */
    public static void handleUltimate(ServerPlayerEntity player, boolean charged, String expectedItemId) {
        if (player.isDead() || player.isSpectator()) return;

        ItemStack held = player.getMainHandStack();
        String actualId = Registries.ITEM.getId(held.getItem()).toString();

        // 武器和发请求时的对不上 —— 玩家中途换手了，忽略这次请求
        if (expectedItemId != null && !actualId.equals(expectedItemId)) return;

        if (held.getItem() instanceof ISkillWeapon weapon) {
            weapon.triggerUltimate(player, charged);
        }
    }
}