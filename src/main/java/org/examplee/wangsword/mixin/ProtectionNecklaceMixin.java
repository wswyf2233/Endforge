package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.util.ProtectionNecklaceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 保护项链的真减伤层。
 *
 * <p>注入 {@code LivingEntity#modifyAppliedDamage} 的 RETURN：这个方法返回的是
 * 「护甲 + 保护附魔 + 抗性效果」都已经算完的最终伤害，在这里再乘一次
 * (1 - 30%)，等于给玩家加了一层<b>不吃原版 80% 抗性上限</b>的减伤。
 *
 * <p>为什么不放在 {@code damage} 的 HEAD：那里拿到的是原始伤害，扣完之后护甲还会再算一遍，
 * 最终效果会被护甲稀释；放在 RETURN 才是真的「这一刀少掉 30% 血」。
 */
@Mixin(LivingEntity.class)
public class ProtectionNecklaceMixin {

    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void wangsword$protectionNecklaceReduce(DamageSource source, float amount,
                                                    CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player)) {
            return;
        }
        float applied = cir.getReturnValue();
        if (applied <= 0.0F) {
            return;
        }
        cir.setReturnValue(ProtectionNecklaceHandler.reduceFinalDamage(player, source, applied));
    }
}
