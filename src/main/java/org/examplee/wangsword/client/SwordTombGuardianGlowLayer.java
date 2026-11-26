package org.examplee.wangsword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.entity.SwordTombGuardianEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 带呼吸的发光层。
 *
 * <p>父类 {@link AutoGlowingGeoLayer} 直接用 15728640（满亮度坐标）画一遍
 * <code>sword_tomb_guardian_glowmask.png</code>，亮度是死的。
 * 这里只改一件事：把渲染时的顶点颜色乘上一个随时间起伏的系数，
 * 遮罩区域就会自己一亮一暗地"呼吸"，不需要多帧贴图。
 *
 * <p>三种状态：
 * <ul>
 *   <li>石化未苏醒 —— 恒定 0.72 亮度，像埋在石头里的死物，不动</li>
 *   <li>苏醒 —— 2.4 秒一个周期，在 0.62 ~ 1.00 之间起伏</li>
 *   <li>死亡 —— 随 deathTime 线性衰减到 0，20 tick 内彻底熄灭</li>
 * </ul>
 */
public class SwordTombGuardianGlowLayer extends AutoGlowingGeoLayer<SwordTombGuardianEntity> {

    /** 呼吸最暗时的亮度（1.0 是满亮） */
    private static final float PULSE_MIN = 0.62F;
    /** 每 tick 推进的相位（弧度）。0.13 rad/tick ≈ 48 tick ≈ 2.4 秒一呼一吸 */
    private static final float PULSE_SPEED = 0.13F;
    /** 石化的常亮值 */
    private static final float PETRIFIED_BRIGHTNESS = 0.72F;
    /** 满亮度坐标（LightmapTextureManager.MAX_LIGHT_COORDINATE），自发光不看环境光 */
    private static final int MAX_LIGHT = 0xF00000;

    public SwordTombGuardianGlowLayer(GeoRenderer<SwordTombGuardianEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(MatrixStack matrices, SwordTombGuardianEntity animatable, BakedGeoModel model,
                       RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        // 用父类那套 RenderLayer（内部会去找 _glowmask 贴图并做成半透明自发光层）
        RenderLayer glowType = getRenderType(animatable);
        VertexConsumer glowBuffer = bufferSource.getBuffer(glowType);

        float brightness = brightnessOf(animatable, partialTick);
        if (brightness <= 0.01F) {
            return;
        }

        // 最后四个 float 是顶点颜色，乘在贴图颜色上，所以用它做亮度。
        // 光坐标仍然传满亮度，保证只会变暗、不会受环境光影响。
        getRenderer().reRender(model, matrices, bufferSource, animatable, glowType, glowBuffer,
                partialTick, MAX_LIGHT, OverlayTexture.DEFAULT_UV,
                brightness, brightness, brightness, 1.0F);
    }

    private static float brightnessOf(SwordTombGuardianEntity entity, float partialTick) {
        if (entity.deathTime > 0) {
            // 死亡后 20 tick 内熄灭，和尸体消失的时间对齐
            return Math.max(0.0F, 1.0F - entity.deathTime / 20.0F);
        }
        if (entity.isLastStanding()) {
            // 锁血无敌：亮度拉满并高频抖动（约 7 tick 一轮），像核心在过载
            float t = (entity.age + partialTick) * 0.9F;
            return 0.85F + 0.15F * (0.5F + 0.5F * MathHelper.sin(t));
        }
        if (!entity.isAwake()) {
            return PETRIFIED_BRIGHTNESS;
        }
        float phase = (entity.age + partialTick) * PULSE_SPEED;
        return PULSE_MIN + (1.0F - PULSE_MIN) * (0.5F + 0.5F * MathHelper.sin(phase));
    }
}
