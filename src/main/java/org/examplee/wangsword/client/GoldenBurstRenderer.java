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
import org.examplee.wangsword.entity.GoldenBurstEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 金色爆炸光柱的渲染器。
 *
 * <p>不是一根直筒子：外层是往上外扩、带螺旋扭曲的"能量漩涡"，里面套一层反向扭转的光壳，
 * 中心再立一根细长的贯穿光柱，底部还有一圈炸开的尖刺，贴地一圈扩散冲击环。
 */
public class GoldenBurstRenderer extends EntityRenderer<GoldenBurstEntity> {

    private static final Identifier GOLD = ArcSlashEntityRenderer.GOLD_TEXTURE;
    private static final int SEGMENTS = 44;
    private static final int LAYERS = 14;

    /** 用哪张贴图 */
    private final Identifier texture;
    /** true = 沿实体朝向横着放（风筒），false = 竖着的光柱（大招爆炸） */
    private final boolean horizontal;
    /** 这根东西画多少 tick 就收掉（风筒要短命，才能跟着视角不断重生） */
    private final float lifeTicks;

    public GoldenBurstRenderer(EntityRendererFactory.Context context) {
        this(context, GOLD, false, GoldenBurstEntity.LIFE);
    }

    public GoldenBurstRenderer(EntityRendererFactory.Context context, Identifier texture,
                               boolean horizontal, float lifeTicks) {
        super(context);
        this.texture = texture;
        this.horizontal = horizontal;
        this.lifeTicks = lifeTicks;
    }

    @Override
    public Identifier getTexture(GoldenBurstEntity entity) {
        return texture;
    }

    @Override
    public void render(GoldenBurstEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;
        float t = MathHelper.clamp(life / lifeTicks, 0.0F, 1.0F);
        if (t >= 1.0F) {
            return;
        }

        // 风筒：把整根柱体按实体朝向转成横的（+Y 转到视线方向）
        if (horizontal) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));
        }

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(texture));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 快速长起，然后保持
        float grow = Math.min(1.0F, t * 6.0F);
        float height = (horizontal ? 13.0F : 26.0F) * grow;
        float time = life * 0.35F;

        // 外层能量漩涡：往上外扩 + 螺旋扭曲
        drawVortex(vc, posMat, normMat, time, 2.3F + t * 1.2F, height, 0.72F, 1.0F, 2.6F);
        // 内层反向光壳
        drawVortex(vc, posMat, normMat, time, 1.4F + t * 0.5F, height * 0.95F, 0.9F, -1.0F, 3.4F);
        // 中心细长贯穿光柱
        drawCore(vc, posMat, normMat, 0.45F, height * 1.7F + 14.0F, 0.95F);
        // 底部炸开的尖刺
        drawSpikes(vc, posMat, normMat, time, t, 0.85F);
        // 贴地扩散环（横放的风筒不要这个）
        if (!horizontal) {
            drawGroundRing(vc, posMat, normMat, 1.0F + t * 9.0F, 1.2F * (1.0F - t * 0.5F), 0.8F);
        }
    }

    /**
     * 螺旋外扩的能量壳。
     *
     * @param twistDir  扭转方向（1 顺时针，-1 逆时针）
     * @param twistRate 扭转量（弧度/整根柱高）
     */
    private void drawVortex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, float time,
                            float radiusBase, float height, float alpha,
                            float twistDir, float twistRate) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        float bottom = -0.4F;

        for (int layer = 0; layer < LAYERS; layer++) {
            float p1 = (float) layer / LAYERS;
            float p2 = (float) (layer + 1) / LAYERS;

            float y1 = bottom + height * p1;
            float y2 = bottom + height * p2;
            // 越往上越外扩
            float r1 = radiusBase * (1.0F + 0.85F * (float) Math.pow(p1, 1.7));
            float r2 = radiusBase * (1.0F + 0.85F * (float) Math.pow(p2, 1.7));
            float tw1 = twistDir * twistRate * p1 + time;
            float tw2 = twistDir * twistRate * p2 + time;

            for (int i = 0; i < SEGMENTS; i++) {
                float u1 = (float) i / SEGMENTS;
                float u2 = (float) (i + 1) / SEGMENTS;
                double ang1 = Math.PI * 2.0D * i / SEGMENTS;
                double ang2 = Math.PI * 2.0D * (i + 1) / SEGMENTS;

                // 表面起伏，避免看起来是光滑圆筒
                float wob1 = 1.0F + 0.10F * MathHelper.sin((float) ang1 * 5.0F + p1 * 6.0F + time * 2.0F);
                float wob2 = 1.0F + 0.10F * MathHelper.sin((float) ang2 * 5.0F + p1 * 6.0F + time * 2.0F);
                float wob3 = 1.0F + 0.10F * MathHelper.sin((float) ang1 * 5.0F + p2 * 6.0F + time * 2.0F);
                float wob4 = 1.0F + 0.10F * MathHelper.sin((float) ang2 * 5.0F + p2 * 6.0F + time * 2.0F);

                float a1 = (float) (ang1 + tw1);
                float a2 = (float) (ang2 + tw1);
                float a3 = (float) (ang2 + tw2);
                float a4 = (float) (ang1 + tw2);

                float x1 = MathHelper.cos(a1) * r1 * wob1;
                float z1 = MathHelper.sin(a1) * r1 * wob1;
                float x2 = MathHelper.cos(a2) * r1 * wob2;
                float z2 = MathHelper.sin(a2) * r1 * wob2;
                float x3 = MathHelper.cos(a3) * r2 * wob3;
                float z3 = MathHelper.sin(a3) * r2 * wob3;
                float x4 = MathHelper.cos(a4) * r2 * wob4;
                float z4 = MathHelper.sin(a4) * r2 * wob4;

                vertex(vc, posMat, normMat, x1, y1, z1, u1, 1.0F, a, x1, 0.0F, z1);
                vertex(vc, posMat, normMat, x2, y1, z2, u2, 1.0F, a, x2, 0.0F, z2);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, 0.0F, a, x3, 0.0F, z3);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, 0.0F, a, x4, 0.0F, z4);

                // 内侧
                vertex(vc, posMat, normMat, x2, y1, z2, u2, 1.0F, a, -x2, 0.0F, -z2);
                vertex(vc, posMat, normMat, x1, y1, z1, u1, 1.0F, a, -x1, 0.0F, -z1);
                vertex(vc, posMat, normMat, x4, y2, z4, u1, 0.0F, a, -x4, 0.0F, -z4);
                vertex(vc, posMat, normMat, x3, y2, z3, u2, 0.0F, a, -x3, 0.0F, -z3);
            }
        }
    }

    /** 中心细长贯穿光柱（比外壳更高更细） */
    private void drawCore(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                          float radius, float height, float alpha) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        float y0 = -0.8F;
        float y1 = y0 + height;

        for (int i = 0; i < 24; i++) {
            float u1 = (float) i / 24.0F;
            float u2 = (float) (i + 1) / 24.0F;
            double ang1 = Math.PI * 2.0D * i / 24.0D;
            double ang2 = Math.PI * 2.0D * (i + 1) / 24.0D;

            float x1 = MathHelper.cos((float) ang1) * radius;
            float z1 = MathHelper.sin((float) ang1) * radius;
            float x2 = MathHelper.cos((float) ang2) * radius;
            float z2 = MathHelper.sin((float) ang2) * radius;

            vertex(vc, posMat, normMat, x1, y0, z1, u1, 1.0F, a, x1, 0.0F, z1);
            vertex(vc, posMat, normMat, x2, y0, z2, u2, 1.0F, a, x2, 0.0F, z2);
            vertex(vc, posMat, normMat, x2, y1, z2, u2, 0.0F, a, x2, 0.0F, z2);
            vertex(vc, posMat, normMat, x1, y1, z1, u1, 0.0F, a, x1, 0.0F, z1);

            vertex(vc, posMat, normMat, x2, y0, z2, u2, 1.0F, a, -x2, 0.0F, -z2);
            vertex(vc, posMat, normMat, x1, y0, z1, u1, 1.0F, a, -x1, 0.0F, -z1);
            vertex(vc, posMat, normMat, x1, y1, z1, u1, 0.0F, a, -x1, 0.0F, -z1);
            vertex(vc, posMat, normMat, x2, y1, z2, u2, 0.0F, a, -x2, 0.0F, -z2);
        }
    }

    /** 底部向外炸开的尖刺 */
    private void drawSpikes(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                            float time, float t, float alpha) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int count = 22;
        float spread = Math.min(1.0F, t * 5.0F);

        for (int i = 0; i < count; i++) {
            double ang = Math.PI * 2.0D * i / count + time * 0.25D;
            float inner = 1.2F;
            float outer = (2.5F + 5.5F * spread) * (i % 2 == 0 ? 1.0F : 0.72F);
            float width = 0.35F;

            float cx = MathHelper.cos((float) ang);
            float cz = MathHelper.sin((float) ang);
            float px = -cz * width;
            float pz = cx * width;

            float tipX = cx * outer;
            float tipZ = cz * outer;
            float inX = cx * inner;
            float inZ = cz * inner;

            // 一个细长三角形，两侧各画一次
            vertex(vc, posMat, normMat, inX + px, 0.05F, inZ + pz, 0.0F, 1.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, inX - px, 0.05F, inZ - pz, 1.0F, 1.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, tipX, 0.6F + 1.2F * spread, tipZ, 0.5F, 0.0F, a, 0.0F, 1.0F, 0.0F);

            vertex(vc, posMat, normMat, inX - px, 0.05F, inZ - pz, 1.0F, 1.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, inX + px, 0.05F, inZ + pz, 0.0F, 1.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, tipX, 0.6F + 1.2F * spread, tipZ, 0.5F, 0.0F, a, 0.0F, 1.0F, 0.0F);
        }
    }

    /** 贴地的水平扩散环 */
    private void drawGroundRing(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                                float radius, float width, float alpha) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        float y = 0.08F;
        int segments = 64;

        for (int i = 0; i < segments; i++) {
            float u1 = (float) i / segments;
            float u2 = (float) (i + 1) / segments;
            double ang1 = Math.PI * 2.0D * i / segments;
            double ang2 = Math.PI * 2.0D * (i + 1) / segments;

            float inX1 = (float) Math.cos(ang1) * (radius - width);
            float inZ1 = (float) Math.sin(ang1) * (radius - width);
            float outX1 = (float) Math.cos(ang1) * (radius + width);
            float outZ1 = (float) Math.sin(ang1) * (radius + width);
            float inX2 = (float) Math.cos(ang2) * (radius - width);
            float inZ2 = (float) Math.sin(ang2) * (radius - width);
            float outX2 = (float) Math.cos(ang2) * (radius + width);
            float outZ2 = (float) Math.sin(ang2) * (radius + width);

            vertex(vc, posMat, normMat, inX1, y, inZ1, u1, 1.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, outX1, y, outZ1, u1, 0.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, outX2, y, outZ2, u2, 0.0F, a, 0.0F, 1.0F, 0.0F);
            vertex(vc, posMat, normMat, inX2, y, inZ2, u2, 1.0F, a, 0.0F, 1.0F, 0.0F);
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
                        float x, float y, float z, float u, float v, int a,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(255, 255, 255, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, nx, ny, nz)
                .next();
    }
}
