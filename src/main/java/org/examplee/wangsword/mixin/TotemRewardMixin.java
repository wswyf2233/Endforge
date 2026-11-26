package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.registry.DaggerRegistry;
import org.examplee.wangsword.util.PoisonDaggerCurse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 持有死花短刀时触发不死图腾 → 奖励一颗钻石。
 *
 * <p>注入的是图腾真正生效的返回值，被取消或没触发的情况都不会发奖励。
 */
@Mixin(LivingEntity.class)
public class TotemRewardMixin {

    @Inject(method = "tryUseTotem", at = @At("RETURN"))
    private void wangsword$rewardTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return;
        }
        if (!player.getMainHandStack().isOf(DaggerRegistry.WS_POISONDAGGER)) {
            return;
        }
        PoisonDaggerCurse.onTotemTriggered(player);
    }
}