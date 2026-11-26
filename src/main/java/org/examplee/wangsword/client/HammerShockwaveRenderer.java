package org.examplee.wangsword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.entity.HammerShockwaveEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 地震波渲染：贴地双层光环 + 边缘矮碎屑 + 环外侧立起的水晶。
 */
public class HammerShockwaveRenderer extends EntityRenderer<HammerShockwaveEntity> {

    private static final Identifier RING_TEXTURE =
            new Identifier("wangsword", "textures/entity/hammer_shockwave.png");

    /** 水晶占位贴图：原版紫水晶簇（之后换成专门的特效水晶图） */
    private static final Identifier CRYSTAL_TEXTURE =
            new Identifier("minecraft", "textures/block/amethyst_cluster.png");

    private static final int SEGMENTS = 128;
    /** 一张环贴图占的弧长（格）：= 贴图宽 × 2.4 ÷ (贴图高 × 0.75) */
    private static final float TILE_WIDTH = 6.4F;

    private static final float MAIN_WIDTH = 2.4F;
    private static final float GOLD_WIDTH = 1.1F;
    private static final float Y = 0.04F;

    private static final int SHARDS = 96;
    private static final float SHARD_HEIGHT = 0.9F;

    /** 水晶数量 */
    private static final int CRYSTALS = 48;
    /** 水晶高度（格）；宽度 = 高度 × 宽高比，保证不变形 */
    private static final float CRYSTAL_HEIGHT = 2.0F;
    /** 水晶贴图宽高比（宽 ÷ 高）。换成非正方形贴图时改这里 */
    private static final float CRYSTAL_ASPECT = 1.0F;
    /** 水晶相对主环的外扩距离（格） */
    private static final float CRYSTAL_OFFSET = 0.9F;

    public HammerShockwaveRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(HammerShockwaveEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        float radius = entity.getRadius();
        if (radius < 0.25F) return;

        matrices.push();
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();
        float age = entity.age + tickDelta;

        // ---------- 环（共用一张贴图）----------
        VertexConsumer ringVc = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucentEmissive(RING_TEXTURE));

        ring(ringVc, posMat, normMat, light, radius - MAIN_WIDTH * 0.55F, radius + MAIN_WIDTH * 0.45F, Y,
                255, 255, 255, age, 0.0F);

        float goldRadius = Math.max(0.6F, radius * 0.72F);
        ring(ringVc, posMat, normMat, light, goldRadius - GOLD_WIDTH * 0.5F, goldRadius + GOLD_WIDTH * 0.5F,
                Y + 0.02F, 255, 255, 255, age, 1.7F);

        for (int i = 0; i < SHARDS; i++) {
            double angle = (Math.PI * 2.0) * i / SHARDS;
            float h = SHARD_HEIGHT * (0.5F + 0.5F * (float) Math.sin(age * 0.8F + i * 0.9F));
            shard(ringVc, posMat, normMat, light, angle, radius, 0.28F, h);
        }

        // ---------- 环外侧的水晶 ----------
        VertexConsumer crystalVc = vertexConsumers.getBuffer(
                RenderLayer.getEntityCutoutNoCull(CRYSTAL_TEXTURE));

        for (int i = 0; i < CRYSTALS; i++) {
            double angle = (Math.PI * 2.0) * i / CRYSTALS + 0.13;
            float h = CRYSTAL_HEIGHT * (0.6F + 0.4F * hash01(i * 37 + 11));
            crystal(crystalVc, posMat, normMat, light,
                    Math.cos(angle) * (radius + CRYSTAL_OFFSET),
                    Math.sin(angle) * (radius + CRYSTAL_OFFSET),
                    h);
        }

        matrices.pop();
    }

    private static float hash01(int seed) {
        int x = seed * 1103515245 + 12345;
        return ((x >>> 8) & 0xFFFF) / 65535.0F;
    }

    /** 贴地圆环：贴图沿圆周平铺 */
    private void ring(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                      float rIn, float rOut, float y,
                      int cr, int cg, int cb,
                      float age, float phaseOffset) {

        float circumference = (float) (Math.PI * 2.0 * ((rIn + rOut) * 0.5F));
        float repeats = circumference / TILE_WIDTH;
        float spin = age * 0.012F + phaseOffset;

        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = (Math.PI * 2.0) * i / SEGMENTS;
            double a1 = (Math.PI * 2.0) * (i + 1) / SEGMENTS;

            float u0 = mirror(repeats * i / SEGMENTS + spin);
            float u1 = mirror(repeats * (i + 1) / SEGMENTS + spin);

            float in0x = (float) (Math.cos(a0) * rIn);
            float in0z = (float) (Math.sin(a0) * rIn);
            float out0x = (float) (Math.cos(a0) * rOut);
            float out0z = (float) (Math.sin(a0) * rOut);
            float in1x = (float) (Math.cos(a1) * rIn);
            float in1z = (float) (Math.sin(a1) * rIn);
            float out1x = (float) (Math.cos(a1) * rOut);
            float out1z = (float) (Math.sin(a1) * rOut);

            triUV(vc, posMat, normMat, light,
                    in0x, y, in0z, u0, 0.15F,
                    in1x, y, in1z, u1, 0.15F,
                    out1x, y, out1z, u1, 0.90F,
                    cr, cg, cb);

            triUV(vc, posMat, normMat, light,
                    in0x, y, in0z, u0, 0.15F,
                    out1x, y, out1z, u1, 0.90F,
                    out0x, y, out0z, u0, 0.90F,
                    cr, cg, cb);
        }
    }

    /** 一根矮碎屑 */
    private void shard(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                       double angle, float radius, float width, float height) {

        float cx = (float) (Math.cos(angle) * radius);
        float cz = (float) (Math.sin(angle) * radius);
        float tx = (float) -Math.sin(angle) * width;
        float tz = (float) Math.cos(angle) * width;
        float nx = (float) Math.cos(angle);
        float nz = (float) Math.sin(angle);

        quad(vc, posMat, normMat, light,
                cx - tx, 0.0F, cz - tz, 0.25F, 0.95F,
                cx + tx, 0.0F, cz + tz, 0.55F, 0.95F,
                cx, height, cz, 0.40F, 0.30F,
                nx, nz);

        quad(vc, posMat, normMat, light,
                cx + tx, 0.0F, cz + tz, 0.55F, 0.95F,
                cx - tx, 0.0F, cz - tz, 0.25F, 0.95F,
                cx, height, cz, 0.40F, 0.30F,
                -nx, -nz);
    }

    /** 一根直立水晶：两片互相垂直的竖直面片，宽高严格按贴图比例 */
    private void crystal(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                         double x, double z, float height) {

        float halfW = height * CRYSTAL_ASPECT * 0.5F;

        billboardUp(vc, posMat, normMat, light, (float) x, (float) z, halfW, height, true);
        billboardUp(vc, posMat, normMat, light, (float) x, (float) z, halfW, height, false);
    }

    private void billboardUp(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                             float x, float z, float halfW, float height, boolean alongX) {

        float vTop = 0.0F;
        float vBottom = 1.0F;

        float dx = alongX ? halfW : 0.0F;
        float dz = alongX ? 0.0F : halfW;
        float nx = alongX ? 0.0F : 1.0F;
        float nz = alongX ? 1.0F : 0.0F;

        // 正面
        vertex(vc, posMat, normMat, light, x - dx, 0.0F, z - dz, 0.0F, vBottom, 255, 255, 255, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x + dx, 0.0F, z + dz, 1.0F, vBottom, 255, 255, 255, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x + dx, height, z + dz, 1.0F, vTop, 255, 255, 255, nx, 0.0F, nz);

        vertex(vc, posMat, normMat, light, x - dx, 0.0F, z - dz, 0.0F, vBottom, 255, 255, 255, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x + dx, height, z + dz, 1.0F, vTop, 255, 255, 255, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x - dx, height, z - dz, 0.0F, vTop, 255, 255, 255, nx, 0.0F, nz);

        // 背面
        vertex(vc, posMat, normMat, light, x + dx, 0.0F, z + dz, 1.0F, vBottom, 255, 255, 255, -nx, 0.0F, -nz);
        vertex(vc, posMat, normMat, light, x - dx, 0.0F, z - dz, 0.0F, vBottom, 255, 255, 255, -nx, 0.0F, -nz);
        vertex(vc, posMat, normMat, light, x - dx, height, z - dz, 0.0F, vTop, 255, 255, 255, -nx, 0.0F, -nz);

        vertex(vc, posMat, normMat, light, x + dx, 0.0F, z + dz, 1.0F, vBottom, 255, 255, 255, -nx, 0.0F, -nz);
        vertex(vc, posMat, normMat, light, x - dx, height, z - dz, 0.0F, vTop, 255, 255, 255, -nx, 0.0F, -nz);
        vertex(vc, posMat, normMat, light, x + dx, height, z + dz, 1.0F, vTop, 255, 255, 255, -nx, 0.0F, -nz);
    }

    private void quad(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                      float x1, float y1, float z1, float u1, float v1,
                      float x2, float y2, float z2, float u2, float v2,
                      float x3, float y3, float z3, float u3, float v3,
                      float nx, float nz) {
        vertex(vc, posMat, normMat, light, x1, y1, z1, u1, v1, 255, 255, 255, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x2, y2, z2, u2, v2, 255, 255, 255, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x3, y3, z3, u3, v3, 255, 255, 255, nx, 0.0F, nz);
    }

    private void triUV(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                       float x1, float y1, float z1, float u1, float v1,
                       float x2, float y2, float z2, float u2, float v2,
                       float x3, float y3, float z3, float u3, float v3,
                       int r, int g, int b) {
        vertex(vc, posMat, normMat, light, x1, y1, z1, u1, v1, r, g, b, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, light, x2, y2, z2, u2, v2, r, g, b, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, light, x3, y3, z3, u3, v3, r, g, b, 0.0F, 1.0F, 0.0F);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                        float x, float y, float z, float u, float v,
                        int r, int g, int b,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(r, g, b, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normMat, nx, ny, nz)
                .next();
    }

    private static float mirror(float u) {
        float f = u % 2.0F;
        if (f < 0.0F) f += 2.0F;
        return f <= 1.0F ? f : 2.0F - f;
    }

    @Override
    public Identifier getTexture(HammerShockwaveEntity entity) {
        return RING_TEXTURE;
    }
}