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
import org.examplee.wangsword.entity.FrostBeamEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 冰霜法杖的光束渲染器。
 *
 * <p>做法参照远古守卫的激光：一条从眼睛笔直射出去的细光柱，
 * 由三层同轴圆柱叠出来（外层冷蓝辉光 / 主束亮青 / 内芯近白），
 * 再在起手与末端各补一小段光团，避免被一刀切断。
 *
 * <p>贴图沿长度方向滚动，读作"能量在往前冲"。贴图重复次数
 * {@link #LAYERS} 必须等于 {@code tiles}，否则每一层都会露出横向接缝
 * （火柱那版踩过这个坑）。
 */
public class FrostBeamRenderer extends EntityRenderer<FrostBeamEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/frost_beam.png");

    private static final int SEGMENTS = 24;
    private static final int LAYERS = 16;

    public FrostBeamRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FrostBeamEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FrostBeamEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float length = entity.getLength();
        if (length < 0.05F) {
            return;
        }

        float life = entity.age + tickDelta;
        float t = MathHelper.clamp(life / FrostBeamEntity.LIFE, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }

        // 把局部 +Y 轴转到实体朝向：和火锥、风筒同一套变换
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 起手 2 tick 从枪口拉出来，最后 5 tick 收掉
        float appear = MathHelper.clamp(life / 2.0F, 0.0F, 1.0F);
        float remain = MathHelper.clamp((FrostBeamEntity.LIFE - life) / 5.0F, 0.0F, 1.0F);
        float envelope = appear * remain;

        // 光柱实际长度也跟 appear 一起伸长，看起来"射出去"而不是"瞬间出现"
        float drawLen = length * (0.45F + 0.55F * appear);
        float thick = entity.getThickness();
        float time = life * 0.06F;

        // 外层：冷蓝辉光，负责体量。半径不能给大——激光要"细而锐"，
        // 太宽就变成一条雾带，而不是一束光
        drawCylinder(vc, posMat, normMat, 0.24F * thick, drawLen, 0.55F, 0.34F * envelope, time,
                44, 112, 198);
        // 主束：亮青，光束本体
        drawCylinder(vc, posMat, normMat, 0.11F * thick, drawLen, 1.30F, 0.95F * envelope, time,
                140, 220, 255);
        // 内芯：近白，让光束在暗处也有一条刺眼的中线
        drawCylinder(vc, posMat, normMat, 0.048F * thick, drawLen, 2.40F, 1.00F * envelope, time,
                240, 252, 255);

        // 起手光团：从法杖尖端冒出来的一小团寒气。
        // 半径必须小——起点离眼睛只有一格多，稍微给大一点就会在第一人称里糊住准心
        drawCone(vc, posMat, normMat, 0.15F * thick, 0.07F * thick, 0.50F,
                1.20F, -2.0F, 0.80F * envelope, time, 0.0F, 196, 240, 255, 96, 176, 240);
        // 末端光团：光束打到东西时炸开的一点霜花，越到收尾越亮（反过来膨胀会显得是"弹回来"）
        float tip = 1.0F - remain;
        drawCone(vc, posMat, normMat, 0.11F * thick, (0.09F + 0.45F * tip) * thick, 0.28F + 0.45F * tip,
                1.00F, 2.4F, 0.90F * envelope, time, drawLen - 0.08F,
                236, 250, 255, 150, 214, 250);
    }

    /** 等半径圆柱，贴图沿长度滚动 */
    private void drawCylinder(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                              float radius, float height, float scroll, float alpha, float time,
                              int r, int g, int b) {
        for (int layer = 0; layer < LAYERS; layer++) {
            float p1 = (float) layer / LAYERS;
            float p2 = (float) (layer + 1) / LAYERS;

            float y1 = height * p1;
            float y2 = height * p2;

            // 亮度轻微流动，光束才不是一根塑料管
            float flick = 0.92F + 0.08F * MathHelper.sin(time * 26.0F + p1 * 9.0F);
            int a = (int) (MathHelper.clamp(alpha * flick, 0.0F, 1.0F) * 255.0F);
            if (a <= 2) {
                continue;
            }

            // V 必须连续，不能取小数部分：两顶点之间发生 1→0 回绕会把贴图拉成一条横带
            float v1 = p1 * LAYERS - time * scroll * 8.0F;
            float v2 = p2 * LAYERS - time * scroll * 8.0F;

            for (int i = 0; i < SEGMENTS; i++) {
                float ang1 = (float) (Math.PI * 2.0D * i / SEGMENTS);
                float ang2 = (float) (Math.PI * 2.0D * (i + 1) / SEGMENTS);

                float w1 = wobble(ang1, p1, time);
                float w2 = wobble(ang2, p1, time);
                float w3 = wobble(ang2, p2, time);
                float w4 = wobble(ang1, p2, time);

                float x1 = MathHelper.cos(ang1) * radius * w1;
                float z1 = MathHelper.sin(ang1) * radius * w1;
                float x2 = MathHelper.cos(ang2) * radius * w2;
                float z2 = MathHelper.sin(ang2) * radius * w2;
                float x3 = MathHelper.cos(ang2) * radius * w3;
                float z3 = MathHelper.sin(ang2) * radius * w3;
                float x4 = MathHelper.cos(ang1) * radius * w4;
                float z4 = MathHelper.sin(ang1) * radius * w4;

                float u1 = (float) i / SEGMENTS;
                float u2 = (float) (i + 1) / SEGMENTS;

                vertex(vc, posMat, normMat, x1, y1, z1, u1, v1, a, r, g, b, x1, 0.0F, z1);
                vertex(vc, posMat, normMat, x2, y1, z2, u2, v1, a, r, g, b, x2, 0.0F, z2);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, v2, a, r, g, b, x3, 0.0F, z3);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, v2, a, r, g, b, x4, 0.0F, z4);

                // 内表面：从侧面斜看时也能看到光束内部
                vertex(vc, posMat, normMat, x2, y1, z2, u2, v1, a, r, g, b, -x2, 0.0F, -z2);
                vertex(vc, posMat, normMat, x1, y1, z1, u1, v1, a, r, g, b, -x1, 0.0F, -z1);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, v2, a, r, g, b, -x4, 0.0F, -z4);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, v2, a, r, g, b, -x3, 0.0F, -z3);
            }
        }
    }

    /** 圆台（起手/末端的光团），沿局部 +Y 从 yStart 往长 height */
    private void drawCone(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                          float r0, float r1, float height,
                          float scroll, float twist, float alpha, float time, float yStart,
                          int br, int bg, int bb, int tr, int tg, int tb) {
        int layers = 4;
        for (int layer = 0; layer < layers; layer++) {
            float p1 = (float) layer / layers;
            float p2 = (float) (layer + 1) / layers;

            float y1 = yStart + height * p1;
            float y2 = yStart + height * p2;
            float r1a = MathHelper.lerp(p1, r0, r1);
            float r2a = MathHelper.lerp(p2, r0, r1);

            int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
            if (a <= 2) {
                continue;
            }
            int cr = (int) MathHelper.lerp(p1, br, tr);
            int cg = (int) MathHelper.lerp(p1, bg, tg);
            int cb = (int) MathHelper.lerp(p1, bb, tb);

            float v1 = -time * scroll * 2.0F;
            float v2 = 1.0F - time * scroll * 2.0F;
            float tw = twist * p1 + time * 4.0F;

            for (int i = 0; i < SEGMENTS; i++) {
                float ang1 = (float) (Math.PI * 2.0D * i / SEGMENTS) + tw;
                float ang2 = (float) (Math.PI * 2.0D * (i + 1) / SEGMENTS) + tw;

                float x1 = MathHelper.cos(ang1) * r1a;
                float z1 = MathHelper.sin(ang1) * r1a;
                float x2 = MathHelper.cos(ang2) * r1a;
                float z2 = MathHelper.sin(ang2) * r1a;
                float x3 = MathHelper.cos(ang2) * r2a;
                float z3 = MathHelper.sin(ang2) * r2a;
                float x4 = MathHelper.cos(ang1) * r2a;
                float z4 = MathHelper.sin(ang1) * r2a;

                float u1 = (float) i / SEGMENTS;
                float u2 = (float) (i + 1) / SEGMENTS;

                vertex(vc, posMat, normMat, x1, y1, z1, u1, v1, a, cr, cg, cb, x1, 0.0F, z1);
                vertex(vc, posMat, normMat, x2, y1, z2, u2, v1, a, cr, cg, cb, x2, 0.0F, z2);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, v2, a, cr, cg, cb, x3, 0.0F, z3);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, v2, a, cr, cg, cb, x4, 0.0F, z4);

                vertex(vc, posMat, normMat, x2, y1, z2, u2, v1, a, cr, cg, cb, -x2, 0.0F, -z2);
                vertex(vc, posMat, normMat, x1, y1, z1, u1, v1, a, cr, cg, cb, -x1, 0.0F, -z1);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, v2, a, cr, cg, cb, -x4, 0.0F, -z4);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, v2, a, cr, cg, cb, -x3, 0.0F, -z3);
            }
        }
    }

    /** 半径抖动：光束比火柱规整得多，只留一丝"能量不稳"的毛边 */
    private float wobble(float angle, float p, float time) {
        return 1.0F
                + 0.035F * MathHelper.sin(angle * 5.0F + p * 17.0F + time * 42.0F)
                + 0.020F * MathHelper.sin(angle * 11.0F - p * 9.0F + time * 67.0F);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a,
                        int r, int g, int b,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, nx, ny, nz)
                .next();
    }
}
