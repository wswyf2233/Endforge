package org.examplee.wangsword.client;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.examplee.wangsword.entity.ThunderBoltEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 紫电 / 蓝电的渲染器。
 *
 * <p>原版闪电是顶点色画的白线，没有贴图可换；这里用两张自己画的雷柱贴图
 * （{@code thunder_bolt_purple.png} / {@code thunder_bolt_blue.png}）重画：
 * 两块互相垂直的面片交叉，任何角度看都是一道立起来的雷。
 *
 * <p>贴图是 32×512（1:16），所以四边形的宽必须取「高 / 16」，不然锯齿会被横向拉扁。
 *
 * <p>淡出用实体自己的 {@code visualAge} 而不是 {@code age}：{@link ThunderBoltEntity}
 * 第 3 tick 之后就不再走原版 tick 了，{@code age} 是不涨的。
 */
public class ThunderBoltRenderer extends EntityRenderer<ThunderBoltEntity> {

    private static final Identifier PURPLE =
            new Identifier("wangsword", "textures/entity/thunder_bolt_purple.png");
    private static final Identifier BLUE =
            new Identifier("wangsword", "textures/entity/thunder_bolt_blue.png");

    /** 雷柱高度（格） */
    private static final float HEIGHT = 16.0F;
    /** 半宽 = 高度 / 贴图长宽比(32:512 = 1:16) / 2 */
    private static final float HALF_WIDTH = HEIGHT / 16.0F / 2.0F;

    public ThunderBoltRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ThunderBoltEntity entity) {
        return entity.getStyle() == ThunderBoltEntity.BLUE ? BLUE : PURPLE;
    }

    @Override
    public void render(ThunderBoltEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        int age = entity.getVisualAge();
        float life = age + tickDelta;
        float t = life / (float) ThunderBoltEntity.VISUAL_LIFE;

        // 起手 1.0 → 结尾 0.12 线性衰减，再叠 ±14% 的抖动，读起来才像在放电
        float alpha = MathHelper.clamp(1.05F - t, 0.12F, 1.0F)
                * (0.86F + 0.14F * MathHelper.sin(life * 2.6F + entity.getId()));
        if (alpha <= 0.02F) {
            return;
        }

        // 每道雷高矮、左右镜像都不一样，18 道一起落才不会像复制粘贴
        float height = HEIGHT * (0.82F + (entity.getId() % 7) * 0.05F);
        boolean flipU = (entity.getId() & 1) == 0;

        VertexConsumer vc = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucentEmissive(this.getTexture(entity)));

        matrices.push();
        try {
            // 交叉两块面片：一块朝 Z，一块朝 X。
            // 每块正反各画一次，不依赖渲染层是否关了背面剔除。
            quad(vc, matrices, alpha, height, flipU, false);
            quad(vc, matrices, alpha, height, flipU, true);

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            quad(vc, matrices, alpha, height, flipU, false);
            quad(vc, matrices, alpha, height, flipU, true);
        } finally {
            matrices.pop();
        }

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * 一块 2×1 的竖直面片。
     *
     * @param flipU  左右镜像（UV 的 u 反过来），让相邻两道雷不是同一张脸
     * @param invert 顶点绕序反过来，用于画背面
     */
    private void quad(VertexConsumer vc, MatrixStack matrices,
                      float alpha, float height, boolean flipU, boolean invert) {
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        float w = HALF_WIDTH;
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        float u0 = flipU ? 1.0F : 0.0F;
        float u1 = flipU ? 0.0F : 1.0F;

        if (invert) {
            vertex(vc, posMat, normMat, -w, 0.0F, 0.0F, u0, 1.0F, a);
            vertex(vc, posMat, normMat, -w, height, 0.0F, u0, 0.0F, a);
            vertex(vc, posMat, normMat, w, height, 0.0F, u1, 0.0F, a);
            vertex(vc, posMat, normMat, w, 0.0F, 0.0F, u1, 1.0F, a);
        } else {
            vertex(vc, posMat, normMat, -w, 0.0F, 0.0F, u0, 1.0F, a);
            vertex(vc, posMat, normMat, w, 0.0F, 0.0F, u1, 1.0F, a);
            vertex(vc, posMat, normMat, w, height, 0.0F, u1, 0.0F, a);
            vertex(vc, posMat, normMat, -w, height, 0.0F, u0, 0.0F, a);
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a) {
        vc.vertex(posMat, x, y, z)
                .color(255, 255, 255, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, 0.0F, 0.0F, 1.0F)
                .next();
    }
}
