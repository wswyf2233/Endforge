package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import org.examplee.wangsword.util.CombatFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 凋灵杀手命中凋灵时不产生击退，避免它被一直打飞、乱飘。
 */
@Mixin(LivingEntity.class)
public class WitherKnockbackMixin {

    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void wangsword$noWitherKnockback(double strength, double x, double z, CallbackInfo ci) {
        if (CombatFlags.noWitherKnockback && (Object) this instanceof WitherEntity) {
            ci.cancel();
        }
    }
}