package org.examplee.wangsword.client;

import net.minecraft.client.render.Frustum;
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
import org.examplee.wangsword.entity.CinderBeamEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 熔核巨兽火焰激光的渲染器。
 *
 * <p>结构照搬远古守卫者的光束：一条从施法点射出去的实体，渲染成一到两块
 * 互相垂直的带面片，任何角度看都有一片正对屏幕。
 *
 * <p>和本项目的紫电/冰霜光束不同，这里<b>不用半透明层</b>：贴图
 * {@code cinder_beam.png} 本身是 alpha 全 255 的橙红渐变（中心白热 → 橙 →
 * 深红边），用 {@link RenderLayer#getEntityCutoutNoCull} 直接硬切绘制。
 * 结果是一条有实体感的火柱，而不是一层会透背景的辉光。
 *
 * <p>不透明层没法靠 alpha 淡入淡出，所以出场和收尾都做成"抽长 / 收窄"：
 * 前 2 tick 从 45% 拉到全长，最后 3 tick 把宽度压回 35%。
 */
public class CinderBeamRenderer extends EntityRenderer<CinderBeamEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/cinder_beam.png");

    /** 沿长度切几段。激光是直的，不需要雷弧那样的高分段 */
    private static final int SEGMENTS = 6;

    /** 内芯相对外层的宽度比例 */
    private static final float CORE_SCALE = 0.42F;

    public CinderBeamRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(CinderBeamEntity entity) {
        return TEXTURE;
    }

    /** 光柱几十格长、包围盒只有 0.2 格，走视锥判断会在偏出画面时整条被裁掉 */
    @Override
    public boolean shouldRender(CinderBeamEntity entity, Frustum frustum,
                                double x, double y, double z) {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public void render(CinderBeamEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float length = entity.getLength();
        if (length < 0.05F) {
            return;
        }

        float life = entity.age + tickDelta;
        float maxLife = Math.max(1, entity.getMaxAge());
        if (life >= maxLife) {
            return;
        }

        float appear = MathHelper.clamp(life / 2.0F, 0.0F, 1.0F);
        float remain = MathHelper.clamp((maxLife - life) / 3.0F, 0.0F, 1.0F);
        float drawLen = length * (0.45F + 0.55F * appear);
        float halfWidth = entity.getThickness()
                * (0.55F + 0.45F * appear)
                * (0.35F + 0.65F * remain);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));

        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 第一片
        sheet(vertexConsumers, posMat, normMat, drawLen, halfWidth, 255, 196, 132);
        sheet(vertexConsumers, posMat, normMat, drawLen, halfWidth * CORE_SCALE, 255, 252, 226);
        // 第二片：转 90° 交叉，保证侧面视角也看得见
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
        Matrix4f rotPos = matrices.peek().getPositionMatrix();
        Matrix3f rotNorm = matrices.peek().getNormalMatrix();
        sheet(vertexConsumers, rotPos, rotNorm, drawLen, halfWidth, 255, 196, 132);
        sheet(vertexConsumers, rotPos, rotNorm, drawLen, halfWidth * CORE_SCALE, 255, 252, 226);

        matrices.pop();
    }

    /**
     * 一条沿 +Y 铺开的带状面片，双面都由 {@code getEntityCutoutNoCull} 负责。
     *
     * <p>V 严格留在 0..1：贴图是单张 32×128，绑定时是 clamp，超出范围会被拉成
     * 边缘那一行像素，整根光柱就变成一块纯色。
     */
    private void sheet(VertexConsumerProvider vertexConsumers, Matrix4f posMat, Matrix3f normMat,
                       float height, float halfWidth, int r, int g, int b) {
        VertexConsumer vc = vertexConsumers.getBuffer(
                RenderLayer.getEntityCutoutNoCull(TEXTURE));

        for (int i = 0; i < SEGMENTS; i++) {
            float p1 = (float) i / SEGMENTS;
            float p2 = (float) (i + 1) / SEGMENTS;
            float y1 = height * p1;
            float y2 = height * p2;

            vertex(vc, posMat, normMat, -halfWidth, y1, 0.0F, 0.0F, p1, r, g, b);
            vertex(vc, posMat, normMat, halfWidth, y1, 0.0F, 1.0F, p1, r, g, b);
            vertex(vc, posMat, normMat, halfWidth, y2, 0.0F, 1.0F, p2, r, g, b);
            vertex(vc, posMat, normMat, -halfWidth, y2, 0.0F, 0.0F, p2, r, g, b);
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v,
                        int r, int g, int b) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, 0.0F, 0.0F, 1.0F)
                .next();
    }
}
