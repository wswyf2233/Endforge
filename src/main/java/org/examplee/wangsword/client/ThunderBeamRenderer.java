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
import org.examplee.wangsword.entity.ThunderBeamEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 雷电法杖的紫电光束渲染器。
 *
 * <p>用项目现成的 {@code thunder_bolt_purple.png}（32×512 的竖直雷柱）沿光束方向
 * 平铺，两块互相垂直的带状面片交叉，任何角度看都是一道立着的电弧；每段横向
 * 抖动一点，读起来才像放电而不是一根直棍。
 *
 * <p>外层紫辉光 + 内芯加色近白，两层都用同一张雷贴图。
 */
public class ThunderBeamRenderer extends EntityRenderer<ThunderBeamEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/thunder_bolt_purple.png");

    /** 沿长度切多少段（抖动按段算） */
    private static final int SEGMENTS = 24;

    /** 外层半宽 / 内芯半宽 */
    private static final float OUTER_WIDTH = 0.26F;
    private static final float CORE_WIDTH = 0.085F;

    public ThunderBeamRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(ThunderBeamEntity entity) {
        return TEXTURE;
    }

    /** 光柱几十格长，包围盒只有 0.2 格，走视锥判断会在偏出画面时整条被裁掉 */
    @Override
    public boolean shouldRender(ThunderBeamEntity entity, Frustum frustum,
                                double x, double y, double z) {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public void render(ThunderBeamEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float length = entity.getLength();
        if (length < 0.05F) {
            return;
        }

        float maxLife = Math.max(1, entity.getMaxLife());
        float life = entity.age + tickDelta;
        float t = MathHelper.clamp(life / maxLife, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }

        // 起手 2 tick 从法杖尖端拉出去，最后 5 tick 收掉
        float appear = MathHelper.clamp(life / 2.0F, 0.0F, 1.0F);
        float remain = MathHelper.clamp((maxLife - life) / 5.0F, 0.0F, 1.0F);
        float envelope = appear * remain;
        float drawLen = length * (0.45F + 0.55F * appear);
        float time = life * 0.06F;

        // 把局部 +Y 轴转到实体朝向：和冰霜光束、火柱同一套变换
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));

        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 外层：紫色电弧
        sheet(vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE)),
                posMat, normMat, drawLen, OUTER_WIDTH, envelope * 0.72F, time,
                186, 128, 255, false);
        sheet(vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE)),
                posMat, normMat, drawLen, OUTER_WIDTH, envelope * 0.72F, time,
                186, 128, 255, true);

        // 内芯：加色层，暗处也有一条刺眼的紫白中线
        sheet(vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE)),
                posMat, normMat, drawLen, CORE_WIDTH, envelope, time,
                244, 232, 255, false);
        sheet(vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE)),
                posMat, normMat, drawLen, CORE_WIDTH, envelope, time,
                244, 232, 255, true);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
        Matrix4f rotPos = matrices.peek().getPositionMatrix();
        Matrix3f rotNorm = matrices.peek().getNormalMatrix();

        sheet(vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE)),
                rotPos, rotNorm, drawLen, OUTER_WIDTH, envelope * 0.72F, time,
                186, 128, 255, false);
        sheet(vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE)),
                rotPos, rotNorm, drawLen, OUTER_WIDTH, envelope * 0.72F, time,
                186, 128, 255, true);
        sheet(vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE)),
                rotPos, rotNorm, drawLen, CORE_WIDTH, envelope, time,
                244, 232, 255, false);
        sheet(vertexConsumers.getBuffer(RenderLayer.getEyes(TEXTURE)),
                rotPos, rotNorm, drawLen, CORE_WIDTH, envelope, time,
                244, 232, 255, true);
    }

    /**
     * 一条沿 +Y 铺开的带状面片，正/反各由调用方画一次。
     *
     * <p>每段的 x 按正弦抖动，段与段之间共用端点，所以电弧是连续的、不会断开。
     */
    private void sheet(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                       float height, float halfWidth, float alpha, float time,
                       int r, int g, int b, boolean invert) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (a <= 2) {
            return;
        }

        for (int i = 0; i < SEGMENTS; i++) {
            float p1 = (float) i / SEGMENTS;
            float p2 = (float) (i + 1) / SEGMENTS;
            float y1 = height * p1;
            float y2 = height * p2;

            float d1 = wobble(p1, time);
            float d2 = wobble(p2, time);
            // V 必须留在 0..1：雷柱贴图是单张 32×512 竖直图，超出会被 clamp 到
            // 底部那几行全透明像素，整条光束就什么也看不见了
            float v1 = p1;
            float v2 = p2;

            if (invert) {
                vertex(vc, posMat, normMat, -halfWidth + d1, y1, 0.0F, 0.0F, v1, a, r, g, b);
                vertex(vc, posMat, normMat, -halfWidth + d2, y2, 0.0F, 0.0F, v2, a, r, g, b);
                vertex(vc, posMat, normMat, halfWidth + d2, y2, 0.0F, 1.0F, v2, a, r, g, b);
                vertex(vc, posMat, normMat, halfWidth + d1, y1, 0.0F, 1.0F, v1, a, r, g, b);
            } else {
                vertex(vc, posMat, normMat, -halfWidth + d1, y1, 0.0F, 0.0F, v1, a, r, g, b);
                vertex(vc, posMat, normMat, halfWidth + d1, y1, 0.0F, 1.0F, v1, a, r, g, b);
                vertex(vc, posMat, normMat, halfWidth + d2, y2, 0.0F, 1.0F, v2, a, r, g, b);
                vertex(vc, posMat, normMat, -halfWidth + d2, y2, 0.0F, 0.0F, v2, a, r, g, b);
            }
        }
    }

    private float wobble(float p, float time) {
        return 0.13F * MathHelper.sin(p * 19.0F + time * 43.0F)
                + 0.07F * MathHelper.sin(p * 41.0F - time * 67.0F);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a,
                        int r, int g, int b) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, 0.0F, 0.0F, 1.0F)
                .next();
    }
}
