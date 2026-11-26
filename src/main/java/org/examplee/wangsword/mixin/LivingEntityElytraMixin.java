package org.examplee.wangsword.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.examplee.wangsword.registry.ArmorRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让紫金四件套拥有原版鞘翅的滑翔判定。
 *
 * <p>原版 LivingEntity.tickFallFlying() 每 tick 检查 胸甲.isOf(Items.ELYTRA)，
 * 不是鞘翅就清掉滑翔标记。这里在方法开头接管：穿齐四件且在空中时维持滑翔，
 * 并自己补上原版被跳过的 roll 动画与风声。
 *
 * <p>用 @Inject 而不是 @Redirect：MixinExtras 0.5.4 处理 @Redirect 时会抛
 * ClassCastException（ArrayList → AnnotationNode）。
 */
@Mixin(LivingEntity.class)
public class LivingEntityElytraMixin {

    @Shadow
    protected int roll;

    @Inject(method = "tickFallFlying", at = @At("HEAD"), cancellable = true)
    private void wangsword$keepCustomElytra(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!(self instanceof PlayerEntity player)) return;
        if (!ArmorRegistry.isFullSet(player)) return;

        // 只维持已经进入滑翔的状态，避免落地瞬间反复开关导致抽动
        if (!player.isFallFlying()) return;

        // 落地 / 骑乘 / 漂浮 → 交给原版（清掉滑翔）
        if (player.isOnGround() || player.hasVehicle() || player.hasStatusEffect(StatusEffects.LEVITATION)) {
            return;
        }

        player.startFallFlying();
        player.fallDistance = 0.0F;

        // 补回原版的滚转动画
        this.roll++;

        // 补回原版的滑翔风声（每 10 tick 一次，仅客户端）
        if (this.roll % 10 == 0 && player.getWorld().isClient()) {
            player.getWorld().playSound(player,
                    player.getBlockPos(),
                    SoundEvents.ITEM_ELYTRA_FLYING,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        ci.cancel();
    }
}