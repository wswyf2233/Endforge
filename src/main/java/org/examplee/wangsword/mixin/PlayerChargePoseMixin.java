package org.examplee.wangsword.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.examplee.wangsword.client.ChargeSlashAnimation;
import org.examplee.wangsword.item.unique.ExcaliburItem;
import org.examplee.wangsword.registry.ItemsRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 誓约胜利之剑的两套动作，直接读 player_animation 里的 JSON 摆骨骼。
 *
 * <ul>
 *   <li>按住 X 蓄力 / 发射下劈：{@code charge_slash}</li>
 *   <li>按住右键风王结界：{@code wind_barrier}</li>
 * </ul>
 *
 * <p>不用 PlayerAnimator 的动画层，所以不会和 Better Combat 抢图层。
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerChargePoseMixin {

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void wangsword$chargePose(LivingEntity entity, float limbAngle, float limbDistance,
                                      float animationProgress, float headYaw, float headPitch,
                                      CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != player) {
            return;
        }
        // PlayerAnimator 已经在驱动时让开，两边同时写骨骼会抖
        if (org.examplee.wangsword.client.ExcaliburAnimation.isDriving()) {
            return;
        }

        ChargeSlashAnimation animation = null;
        float time = 0.0F;

        // 右键：风王结界，按住期间按使用进度播
        ItemStack using = player.getActiveItem();
        if (player.isUsingItem() && using.getItem() == ItemsRegistry.WS_EXCALIBUR) {
            int max = using.getMaxUseTime();
            int elapsed = max - player.getItemUseTimeLeft();
            if (elapsed >= 0) {
                animation = ChargeSlashAnimation.WIND;
                time = elapsed / 20.0F;
            }
        }

        // 否则：X 蓄力 / 下劈
        if (animation == null && (ExcaliburItem.CHARGE_ELAPSED > 0 || ExcaliburItem.FIRE_ANIM > 0)) {
            animation = ChargeSlashAnimation.CHARGE;
            time = (ExcaliburItem.CHARGE_ELAPSED
                    + (ExcaliburItem.FIRE_ANIM > 0 ? ExcaliburItem.FIRE_ANIM_TICKS - ExcaliburItem.FIRE_ANIM : 0))
                    / 20.0F;
        }

        if (animation == null || !animation.isLoaded()) {
            return;
        }

        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        apply(model, animation, time);

        if ((Object) this instanceof PlayerEntityModel<?> playerModel) {
            copyPose(model.rightArm, playerModel.rightSleeve);
            copyPose(model.leftArm, playerModel.leftSleeve);
        }
    }

    private static void apply(BipedEntityModel<?> model, ChargeSlashAnimation animation, float time) {
        float[] rotation;

        if ((rotation = animation.rotation("body", time)) != null) {
            add(model.body, rotation);
        }
        if ((rotation = animation.rotation("head", time)) != null) {
            add(model.head, rotation);
        }
        if ((rotation = animation.rotation("right_arm", time)) != null) {
            set(model.rightArm, rotation);
        }
        if ((rotation = animation.rotation("left_arm", time)) != null) {
            set(model.leftArm, rotation);
        }
        if ((rotation = animation.rotation("right_leg", time)) != null) {
            set(model.rightLeg, rotation);
        }
        if ((rotation = animation.rotation("left_leg", time)) != null) {
            set(model.leftLeg, rotation);
        }
    }

    private static void set(ModelPart part, float[] rotation) {
        part.pitch = rotation[0];
        part.yaw = rotation[1];
        part.roll = rotation[2];
    }

    /**
     * 躯干和头要“叠加”而不是覆盖：
     * 原版 setAngles 里已经按身体朝向算好了 yaw，覆盖掉的话人就不跟着视角转了。
     */
    private static void add(ModelPart part, float[] rotation) {
        part.pitch += rotation[0];
        part.yaw += rotation[1];
        part.roll += rotation[2];
    }

    private static void copyPose(ModelPart from, ModelPart to) {
        to.pitch = from.pitch;
        to.yaw = from.yaw;
        to.roll = from.roll;
    }
}
