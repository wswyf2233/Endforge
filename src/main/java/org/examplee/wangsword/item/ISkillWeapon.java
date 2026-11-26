package org.examplee.wangsword.item;

import net.minecraft.server.network.ServerPlayerEntity;

public interface ISkillWeapon {
    void triggerUltimate(ServerPlayerEntity player, boolean charged);
}