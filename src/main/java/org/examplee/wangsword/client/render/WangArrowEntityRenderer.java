package org.examplee.wangsword.client.render;

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
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.WangArrowEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 流星箭的渲染：完全照冰霜法杖那一套——三层同轴圆柱，外层冷蓝辉光 / 主束亮青 / 内芯近白，
 * 贴图沿长度方向滚动，读作"能量在往前冲"。
 *
 * <p>唯一的区别是长度由"发射点到箭头"的距离决定，所以光束是从枪口一路拉出来的。
 * 伤害与方块破坏仍由实体自己按飞行线段结算。
 */
public class WangArrowEntityRenderer extends EntityRenderer<WangArrowEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/frost_beam.png");

    private static final int SEGMENTS = 24;

    /** 头端超出箭头位置的一点余量 */
    private static final float HEAD_EXTRA = 0.30F;

    /** 长度越大分段越多，段数按长度给，避免长束上一段被拉成十几格 */
    private static final float LAYERS_PER_BLOCK = 1.5F;
    private static final int MIN_LAYERS = 12;
    private static final int MAX_LAYERS = 96;

    public WangArrowEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(WangArrowEntity entity) {
        return TEXTURE;
    }

    /**
     * 跳过视锥裁剪：实体本身的包围盒只有 0.5 格，而光柱能拉到几十格，
     * 按包围盒判断的话，光柱只要一斜出屏幕，整条都会被裁掉。
     */
    @Override
    public boolean shouldRender(WangArrowEntity entity, net.minecraft.client.render.Frustum frustum,
                                double x, double y, double z) {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public void render(WangArrowEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        Vec3d dir = direction(entity);
        Vec3d start = entity.getTrailStart();
        double dist = start == null ? 0.0D : start.distanceTo(entity.getLerpedPos(tickDelta));
        // 不设上限：枪口到箭头有多远就画多长，箭飞出去整条激光跟着一起射出去
        float length = (float) Math.max(0.6D, dist) + HEAD_EXTRA;
        int layers = MathHelper.clamp((int) (length * LAYERS_PER_BLOCK), MIN_LAYERS, MAX_LAYERS);

        matrices.push();
        // 把局部 +Y 转到飞行方向
        matrices.multiply(new Quaternionf().rotationTo(
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize()));
        // 圆柱从尾端画起
        matrices.translate(0.0F, HEAD_EXTRA - length, 0.0F);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        float time = entity.age + tickDelta;

        // 外层：冷蓝辉光，负责体量。半径不能给大——激光要细而锐
        drawCylinder(vc, posMat, normMat, 0.24F, length, 0.55F, 0.55F, time, 44, 112, 198, layers);
        // 主束：亮青，光束本体
        drawCylinder(vc, posMat, normMat, 0.11F, length, 1.30F, 0.95F, time, 140, 220, 255, layers);
        // 内芯：近白，暗处也有一条刺眼的中线
        drawCylinder(vc, posMat, normMat, 0.048F, length, 2.40F, 1.00F, time, 240, 252, 255, layers);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** 飞行方向：速度为准，速度为零时退回 yaw/pitch */
    private Vec3d direction(WangArrowEntity entity) {
        Vec3d v = entity.getVelocity();
        if (v.lengthSquared() > 1.0E-6D) {
            return v.normalize();
        }
        double yawRad = Math.toRadians(entity.getYaw());
        double pitchRad = Math.toRadians(entity.getPitch());
        return new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
    }

    /** 等半径圆柱，贴图沿长度滚动 */
    private void drawCylinder(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                              float radius, float height, float scroll, float alpha, float time,
                              int r, int g, int b, int layers) {
        for (int layer = 0; layer < layers; layer++) {
            float p1 = (float) layer / layers;
            float p2 = (float) (layer + 1) / layers;

            float y1 = height * p1;
            float y2 = height * p2;

            // 亮度轻微流动，光束才不是一根塑料管
            float flick = 0.92F + 0.08F * MathHelper.sin(time * 26.0F + p1 * 9.0F);
            int a = (int) (MathHelper.clamp(alpha * flick, 0.0F, 1.0F) * 255.0F);
            if (a <= 2) {
                continue;
            }

            // V 必须连续，不能取小数部分：两顶点之间发生 1→0 回绕会把贴图拉成一条横带
            float v1 = p1 * layers - time * scroll * 8.0F;
            float v2 = p2 * layers - time * scroll * 8.0F;

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
