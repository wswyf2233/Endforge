package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import org.examplee.wangsword.registry.WitherKillerRegistry;
import org.examplee.wangsword.util.CombatFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 凋灵杀手：对凋灵 / 凋灵骷髅严格双倍伤害。
 *
 * <p>在 {@code LivingEntity.damage} 的 HEAD 把伤害值乘 2 后重新调用，
 * 而不是"打完再补一刀"，因此不受无敌帧影响，护甲、抗性、凋零类减伤都按 2 倍后的数值计算。
 */
@Mixin(LivingEntity.class)
public class WitherDoubleDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void wangsword$doubleWitherDamage(DamageSource source, float amount,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (CombatFlags.doublingWither) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof WitherEntity) && !(self instanceof WitherSkeletonEntity)) {
            return;
        }
        if (!(source.getAttacker() instanceof LivingEntity attacker)) {
            return;
        }
        if (!attacker.getMainHandStack().isOf(WitherKillerRegistry.WS_WITHERSPAWN_KILLER)) {
            return;
        }

        CombatFlags.doublingWither = true;
        CombatFlags.noWitherKnockback = true;
        try {
            cir.setReturnValue(self.damage(source, amount * 2.0F));
        } finally {
            CombatFlags.doublingWither = false;
            CombatFlags.noWitherKnockback = false;
        }
    }
}