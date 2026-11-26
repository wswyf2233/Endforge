package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.examplee.wangsword.registry.EffectRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 冰封（温和版）：被冻住的生物无法「新锁定」目标，但已在途的攻击照旧生效。
 *
 * <p>为什么不在 {@code FrozenEffect} 里每 tick 调 {@code setTarget(null)}：
 * 状态效果在 {@code LivingEntity.baseTick()} 里结算，而 AI 的 goal 在
 * {@code MobEntity.mobTick()} 里跑，顺序上效果先、AI 后。所以效果里清掉的目标，
 * 同一 tick 就会被 targetSelector 重新设回来，等于没清。
 *
 * <p>这里直接在 {@code setTarget} 入口拦截：只要身上有 FROZEN，任何非空 target 一律丢弃，
 * 目标选择器、复仇、Brain 里的攻击任务全都设不进去，且只在真的尝试设置时才有开销。
 * {@code target == null} 必须放行，否则 AI 收手时连「清空」都做不到，会一直卡着旧目标。
 *
 * <p>效果到期后自然恢复：下一 tick targetSelector 会重新选目标，不需要额外收尾。
 */
@Mixin(MobEntity.class)
public class FrozenTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void wangsword$blockTargetWhileFrozen(LivingEntity target, CallbackInfo ci) {
        // 清空目标必须放行，否则旧目标永远摘不掉
        if (target == null) {
            return;
        }

        MobEntity self = (MobEntity) (Object) this;
        if (self.hasStatusEffect(EffectRegistry.FROZEN)) {
            ci.cancel();
        }
    }
}
