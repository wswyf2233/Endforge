package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.util.CombatFlags;
import org.examplee.wangsword.util.FulgurSetHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幽卫钢套被动 2：穿齐四件时，受到 BOSS 的伤害减免 25%。
 *
 * <p>为什么不用 {@code ServerLivingEntityEvents.ALLOW_DAMAGE}：那个事件只能返回
 * true/false，改不了数值。这里沿用 {@code WitherDoubleDamageMixin} 的做法，
 * 在 {@code LivingEntity.damage} 的 HEAD 把数值缩一下再重入，护甲/抗性等后续计算
 * 都按缩减后的基础伤害算，和正常减伤一致。
 *
 * <p>火焰免疫没有走这里——那条在 {@code ALLOW_DAMAGE} 里直接取消，
 * 连伤害数字都不跳。
 */
@Mixin(LivingEntity.class)
public class FulgurBossDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void wangsword$reduceBossDamage(DamageSource source, float amount,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (CombatFlags.reducingFulgurBoss) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!FulgurSetHandler.isFullSet(player) || !FulgurSetHandler.isBossDamage(source)) {
            return;
        }

        CombatFlags.reducingFulgurBoss = true;
        try {
            cir.setReturnValue(self.damage(source, amount * FulgurSetHandler.BOSS_DAMAGE_MULTIPLIER));
        } finally {
            CombatFlags.reducingFulgurBoss = false;
        }
    }
}
