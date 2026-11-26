package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.entity.CinderColossusEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * 熔核巨兽的模型定义：三个资源路径 + 头部视线跟随。
 */
public class CinderColossusModel extends GeoModel<CinderColossusEntity> {

    private static final float MAX_HEAD_YAW = 55.0F;
    private static final float MAX_HEAD_PITCH = 35.0F;
    private static final float HEAD_PITCH_FACTOR = 0.7F;

    /** GeckoLib 渲染前做了 scale(-1, -1, 1) 镜像，局部旋转方向是反的，所以取负 */
    private static final float HEAD_YAW_SIGN = -1.0F;
    private static final float HEAD_PITCH_SIGN = -1.0F;

    @Override
    public Identifier getModelResource(CinderColossusEntity animatable) {
        return new Identifier("wangsword", "geo/cinder_colossus.geo.json");
    }

    @Override
    public Identifier getTextureResource(CinderColossusEntity animatable) {
        return new Identifier("wangsword", "textures/entity/cinder_colossus.png");
    }

    @Override
    public Identifier getAnimationResource(CinderColossusEntity animatable) {
        return new Identifier("wangsword", "animations/cinder_colossus.animation.json");
    }

    @Override
    public void setCustomAnimations(CinderColossusEntity entity, long instanceId,
                                    AnimationState<CinderColossusEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        // 死亡动画会驱动 head，那时把骨骼完全交给动画
        if (entity.deathTime > 0) {
            return;
        }
        if (!(this.getAnimationProcessor().getBone("head") instanceof GeoBone head)) {
            return;
        }

        float partialTick = MinecraftClient.getInstance().getTickDelta();
        float headYaw = MathHelper.lerp(partialTick, entity.prevHeadYaw, entity.headYaw);
        float yawOffset = MathHelper.wrapDegrees(headYaw - entity.bodyYaw);
        yawOffset = MathHelper.clamp(yawOffset, -MAX_HEAD_YAW, MAX_HEAD_YAW);

        float pitch = MathHelper.lerp(partialTick, entity.prevPitch, entity.getPitch());
        pitch = MathHelper.clamp(pitch, -MAX_HEAD_PITCH, MAX_HEAD_PITCH) * HEAD_PITCH_FACTOR;

        head.setRotY((float) Math.toRadians(yawOffset * HEAD_YAW_SIGN));
        head.setRotX((float) Math.toRadians(pitch * HEAD_PITCH_SIGN));
    }
}
