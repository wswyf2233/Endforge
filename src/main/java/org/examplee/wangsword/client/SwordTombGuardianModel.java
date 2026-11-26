package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.entity.SwordTombGuardianEntity;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * 剑冢守卫的模型定义：三个资源路径 + 头部视线跟随。
 */
public class SwordTombGuardianModel extends GeoModel<SwordTombGuardianEntity> {

    /** 头部相对身体最多偏转多少度。原版是 ±75 的软上限，这里收得更紧，免得脖子拧断 */
    private static final float MAX_HEAD_YAW = 65.0F;
    /** 抬头 / 低头的最大角度 */
    private static final float MAX_HEAD_PITCH = 40.0F;
    /** 上下看的比重，1.0 是完全跟随，调小会更"呆" */
    private static final float HEAD_PITCH_FACTOR = 0.7F;

    /**
     * 方向系数。GeckoLib 渲染前会做 scale(-1, -1, 1) 的镜像，
     * 局部 Y/X 轴的正旋转在世界里的实际方向都是反的，所以都取负。
     * 万一进游戏发现头转向和视线相反，把对应那个改成 1.0F。
     */
    private static final float HEAD_YAW_SIGN = -1.0F;
    private static final float HEAD_PITCH_SIGN = -1.0F;

    @Override
    public Identifier getModelResource(SwordTombGuardianEntity animatable) {
        return new Identifier("wangsword", "geo/sword_tomb_guardian.geo.json");
    }

    @Override
    public Identifier getTextureResource(SwordTombGuardianEntity animatable) {
        return new Identifier("wangsword", "textures/entity/sword_tomb_guardian.png");
    }

    @Override
    public Identifier getAnimationResource(SwordTombGuardianEntity animatable) {
        return new Identifier("wangsword", "animations/sword_tomb_guardian.animation.json");
    }

    /**
     * 头部视线跟随。
     *
     * <p>GeckoLib 在每帧 {@code tickAnimation}（应用完所有控制器动画）之后立即调这里，
     * 是官方给"代码手动改骨骼"留的口子。原版 {@code LivingEntityRenderer} 会把
     * {@code headYaw - bodyYaw} 换算成头部旋转，而 GeckoLib 不继承它，所以这里自己补上。
     *
     * <p>head 骨骼在 geo 里没有静态旋转（pivot [0,61,0]、rotation 无），所以直接赋值、
     * 不用叠加，每帧覆盖也不会累积。唯一的例外是 death 动画也驱动 head，
     * 那种情况直接跳过，把骨骼完全交给动画。
     */
    @Override
    public void setCustomAnimations(SwordTombGuardianEntity entity, long instanceId,
                                    AnimationState<SwordTombGuardianEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        // 死亡由 death 动画接管；石化期间是雕像，不转头
        if (entity.deathTime > 0 || !entity.isAwake()) {
            return;
        }
        if (!(this.getAnimationProcessor().getBone("head") instanceof GeoBone head)) {
            return;
        }

        float partialTick = MinecraftClient.getInstance().getTickDelta();
        float headYaw = MathHelper.lerp(partialTick, entity.prevHeadYaw, entity.headYaw);
        float bodyYaw = entity.bodyYaw;

        float yawOffset = MathHelper.wrapDegrees(headYaw - bodyYaw);
        yawOffset = MathHelper.clamp(yawOffset, -MAX_HEAD_YAW, MAX_HEAD_YAW);

        float pitch = MathHelper.lerp(partialTick, entity.prevPitch, entity.getPitch());
        pitch = MathHelper.clamp(pitch, -MAX_HEAD_PITCH, MAX_HEAD_PITCH) * HEAD_PITCH_FACTOR;

        head.setRotY((float) Math.toRadians(yawOffset * HEAD_YAW_SIGN));
        head.setRotX((float) Math.toRadians(pitch * HEAD_PITCH_SIGN));
    }
}
