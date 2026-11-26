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
import org.examplee.wangsword.entity.FlameJetEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 烈火喷射的火舌渲染器。
 *
 * <p>照誓约胜利之剑风王结界那套来：不是撒粒子，而是一段实体网格横放在视线上，
 * 贴着准星不断重生，所以转头时火是"跟着走"的实心火锥。
 *
 * <p>与风筒的差别在于形状和走向——
 * <ul>
 *   <li>风筒是等半径的圆筒；火锥是<b>从枪口细到末端粗</b>的锥体，像喷出去的火；</li>
 *   <li>贴图 V 沿火锥长度方向滚动，且滚动方向朝外，读作"火在往前喷"；</li>
 *   <li>末端再套一小截外扩的火团，收住火锥，不至于被一刀切断。</li>
 * </ul>
 */
public class FlameJetRenderer extends EntityRenderer<FlameJetEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/flame_pillar.png");

    private static final int SEGMENTS = 36;
    /** 层数同时是贴图沿长度的重复次数（两者必须相等，否则层缝会露出来） */
    private static final int LAYERS = 12;
    /** 火锥长度（格），要略大于技能判定射程，视觉上才"够长" */
    private static final float LENGTH = 11.5F;

    public FlameJetRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FlameJetEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FlameJetEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;
        float t = MathHelper.clamp(life / FlameJetEntity.LIFE, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }

        // 把局部 +Y 轴转到实体朝向（和风筒同一个变换）
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 2 tick 内长满，最后 3 tick 收掉。因为每 3 tick 就重生一根，收尾要快，
        // 否则接缝处会看到一段明显变淡的"空档"
        float appear = MathHelper.clamp(life / 2.0F, 0.0F, 1.0F);
        float remain = MathHelper.clamp((FlameJetEntity.LIFE - life) / 3.0F, 0.0F, 1.0F);
        float envelope = appear * remain;

        // 火锥整根往后缩一点，枪口才不会顶在脸上
        float length = LENGTH * (0.80F + 0.20F * appear);
        float time = life * 0.08F;

        // 外层：火的主体，从枪口细到末端粗
        drawCone(vc, posMat, normMat, 0.42F, 1.70F, length,
                10.0F, 0.90F, 3.4F, 0.88F * envelope, time,
                255, 198, 120, 236, 62, 10);
        // 内层：反向扭转的亮芯，让火锥内部有翻卷
        drawCone(vc, posMat, normMat, 0.22F, 0.86F, length * 0.98F,
                11.0F, 1.55F, -5.2F, 1.00F * envelope, time,
                255, 252, 220, 255, 176, 48);
        // 末端火团：外扩一小截并快速变淡，火锥的"头部"
        drawCone(vc, posMat, normMat, 1.70F, 2.55F, 1.8F,
                2.0F, 1.20F, 2.0F, 0.62F * envelope, time,
                250, 110, 24, 180, 24, 0, length);

        // 枪口的一小团爆燃，接住玩家手的位置
        drawCone(vc, posMat, normMat, 0.75F, 0.34F, 0.9F,
                1.0F, 1.80F, -3.0F, 0.85F * envelope, time,
                255, 236, 170, 255, 176, 60);
    }

    /**
     * 画一段沿局部 +Y 方向的火锥。
     *
     * @param r0     起点半径
     * @param r1     终点半径
     * @param tiles  贴图沿长度重复几次（取整数，层与层之间才接得上）
     * @param scroll 贴图沿长度滚动的速度，正数表示往外喷
     * @param twist  整根锥体的扭转量（弧度）
     * @param yStart 起点在局部 Y 上的位置
     */
    private void drawCone(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                          float r0, float r1, float height,
                          float tiles, float scroll, float twist, float alpha, float time,
                          int br, int bg, int bb, int tr, int tg, int tb) {
        drawCone(vc, posMat, normMat, r0, r1, height, tiles, scroll, twist, alpha, time,
                br, bg, bb, tr, tg, tb, 0.0F);
    }

    private void drawCone(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                          float r0, float r1, float height,
                          float tiles, float scroll, float twist, float alpha, float time,
                          int br, int bg, int bb, int tr, int tg, int tb,
                          float yStart) {
        for (int layer = 0; layer < LAYERS; layer++) {
            float p1 = (float) layer / LAYERS;
            float p2 = (float) (layer + 1) / LAYERS;

            float y1 = yStart + height * p1;
            float y2 = yStart + height * p2;
            // 半径不是线性的：枪口一小段偏细，往外才慢慢撑开，像真的喷流
            float r1a = MathHelper.lerp((float) Math.pow(p1, 1.15D), r0, r1);
            float r2a = MathHelper.lerp((float) Math.pow(p2, 1.15D), r0, r1);

            // 每层亮度轻微起伏，火才不会像塑料筒
            float flick = 0.94F + 0.06F * MathHelper.sin(time * 30.0F + p1 * 11.0F);
            int a = (int) (MathHelper.clamp(alpha * flick, 0.0F, 1.0F) * 255.0F);
            if (a <= 2) {
                continue;
            }

            int cr = (int) MathHelper.lerp(p1, br, tr);
            int cg = (int) MathHelper.lerp(p1, bg, tg);
            int cb = (int) MathHelper.lerp(p1, bb, tb);

            float tw1 = twist * p1 + time * 6.0F;
            float tw2 = twist * p2 + time * 6.0F;
            // V 坐标必须是连续量，不能取小数部分：一旦在两顶点之间发生 1→0 回绕，
            // 整张贴图就会被拉成一条横带（火柱那版就栽在这儿）
            float v1 = p1 * tiles - time * scroll * 6.0F;
            float v2 = p2 * tiles - time * scroll * 6.0F;

            for (int i = 0; i < SEGMENTS; i++) {
                float ang1 = (float) (Math.PI * 2.0D * i / SEGMENTS);
                float ang2 = (float) (Math.PI * 2.0D * (i + 1) / SEGMENTS);

                float a1 = ang1 + tw1;
                float a2 = ang2 + tw1;
                float a3 = ang2 + tw2;
                float a4 = ang1 + tw2;

                float w1 = wobble(ang1, p1, time);
                float w2 = wobble(ang2, p1, time);
                float w3 = wobble(ang2, p2, time);
                float w4 = wobble(ang1, p2, time);

                float x1 = MathHelper.cos(a1) * r1a * w1;
                float z1 = MathHelper.sin(a1) * r1a * w1;
                float x2 = MathHelper.cos(a2) * r1a * w2;
                float z2 = MathHelper.sin(a2) * r1a * w2;
                float x3 = MathHelper.cos(a3) * r2a * w3;
                float z3 = MathHelper.sin(a3) * r2a * w3;
                float x4 = MathHelper.cos(a4) * r2a * w4;
                float z4 = MathHelper.sin(a4) * r2a * w4;

                float u1 = (float) i / SEGMENTS;
                float u2 = (float) (i + 1) / SEGMENTS;

                // 外表面
                vertex(vc, posMat, normMat, x1, y1, z1, u1, v1, a, cr, cg, cb, x1, 0.0F, z1);
                vertex(vc, posMat, normMat, x2, y1, z2, u2, v1, a, cr, cg, cb, x2, 0.0F, z2);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, v2, a, cr, cg, cb, x3, 0.0F, z3);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, v2, a, cr, cg, cb, x4, 0.0F, z4);

                // 内表面：从侧面也能看见锥体内部在烧
                vertex(vc, posMat, normMat, x2, y1, z2, u2, v1, a, cr, cg, cb, -x2, 0.0F, -z2);
                vertex(vc, posMat, normMat, x1, y1, z1, u1, v1, a, cr, cg, cb, -x1, 0.0F, -z1);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, v2, a, cr, cg, cb, -x4, 0.0F, -z4);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, v2, a, cr, cg, cb, -x3, 0.0F, -z3);
            }
        }
    }

    /** 半径抖动，让火锥边缘有翻腾的毛边 */
    private float wobble(float angle, float p, float time) {
        return 1.0F
                + 0.11F * MathHelper.sin(angle * 4.0F + p * 13.0F + time * 40.0F)
                + 0.06F * MathHelper.sin(angle * 9.0F - p * 7.0F + time * 63.0F);
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
