package org.examplee.wangsword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.examplee.wangsword.entity.FlameShockwaveEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * melee4 的火焰冲击波：贴地双环 + 一圈立起来的火舌。
 *
 * <p>环体用 {@code flame_shockwave.png} 沿圆周平铺，纵向（v）是径向，
 * 中间一条亮带、上下透明，所以看上去是"火圈"而不是"实心盘子"。
 * 最后一小段按 lifetime 做 alpha 淡出，不会硬消失。
 */
public class FlameShockwaveRenderer extends EntityRenderer<FlameShockwaveEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/flame_shockwave.png");

    private static final int SEGMENTS = 128;
    /** 一张贴图占的弧长（格）：贴图宽 128 × 2.3 ÷ (贴图高 64 × 0.75) ≈ 6.1 */
    private static final float TILE_WIDTH = 6.1F;
    private static final float MAIN_WIDTH = 2.3F;
    private static final float INNER_WIDTH = 0.95F;
    private static final float Y = 0.05F;

    /** 立起来的火舌数量 */
    private static final int TONGUES = 80;

    public FlameShockwaveRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(FlameShockwaveEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        float radius = entity.getRadius();
        if (radius < 0.3F) {
            return;
        }

        float age = entity.age + tickDelta;
        int lifetime = Math.max(1, entity.getLifetime());
        float fade = MathHelper.clamp((lifetime - age) / 4.5F, 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        int alpha = (int) (238.0F * fade);

        matrices.push();
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        VertexConsumer vc = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucentEmissive(TEXTURE));

        // 主环
        ring(vc, posMat, normMat, light,
                radius - MAIN_WIDTH * 0.55F, radius + MAIN_WIDTH * 0.45F, Y, age, 0.0F, alpha);

        // 内圈副环：慢半拍，看起来像两波火浪
        float inner = Math.max(0.5F, radius * 0.72F);
        ring(vc, posMat, normMat, light,
                inner - INNER_WIDTH * 0.5F, inner + INNER_WIDTH * 0.5F,
                Y + 0.02F, age, 1.4F, (int) (alpha * 0.75F));

        // 环上立起来的火舌
        for (int i = 0; i < TONGUES; i++) {
            double angle = (Math.PI * 2.0) * i / TONGUES + age * 0.012;
            float flicker = 0.5F + 0.5F * (float) Math.sin(age * 0.55F + i * 1.37F);
            float height = (1.05F + 0.95F * flicker) * fade;
            float halfW = 0.18F + 0.10F * (0.5F + 0.5F * (float) Math.sin(i * 2.11F));
            tongue(vc, posMat, normMat, light, angle, radius, halfW, height, alpha);
        }

        matrices.pop();
    }

    /** 贴地圆环：贴图沿圆周平铺 */
    private void ring(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                      float rIn, float rOut, float y, float age, float phaseOffset, int alpha) {

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
                    out1x, y, out1z, u1, 0.90F, alpha);

            triUV(vc, posMat, normMat, light,
                    in0x, y, in0z, u0, 0.15F,
                    out1x, y, out1z, u1, 0.90F,
                    out0x, y, out0z, u0, 0.90F, alpha);
        }
    }

    /** 一片竖直火舌：两片互相垂直，底边贴地、向上收成一个尖 */
    private void tongue(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                        double angle, float radius, float halfW, float height, int alpha) {

        float cx = (float) (Math.cos(angle) * radius);
        float cz = (float) (Math.sin(angle) * radius);
        float nx = (float) Math.cos(angle);
        float nz = (float) Math.sin(angle);
        // 切向
        float tx = -nz * halfW;
        float tz = nx * halfW;
        // 径向
        float rx = nx * halfW;
        float rz = nz * halfW;

        int bottom = alpha;
        int top = (int) (alpha * 0.55F);
        float tipX = cx + nx * halfW * 0.25F;
        float tipZ = cz + nz * halfW * 0.25F;

        // 切向展开的一片
        shard(vc, posMat, normMat, light,
                cx - tx, cz - tz, cx + tx, cz + tz, tipX, tipZ, height,
                bottom, top, nx, nz);
        shard(vc, posMat, normMat, light,
                cx + tx, cz + tz, cx - tx, cz - tz, tipX, tipZ, height,
                bottom, top, -nx, -nz);

        // 径向展开的一片，交叉出体积
        shard(vc, posMat, normMat, light,
                cx - rx, cz - rz, cx + rx, cz + rz, tipX, tipZ, height,
                bottom, top, -nz, nx);
        shard(vc, posMat, normMat, light,
                cx + rx, cz + rz, cx - rx, cz - rz, tipX, tipZ, height,
                bottom, top, nz, -nx);
    }

    private void shard(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                       float x1, float z1, float x2, float z2, float tipX, float tipZ, float height,
                       int bottomAlpha, int topAlpha, float nx, float nz) {

        vertex(vc, posMat, normMat, light, x1, 0.0F, z1, 0.26F, 0.72F, bottomAlpha, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, x2, 0.0F, z2, 0.55F, 0.72F, bottomAlpha, nx, 0.0F, nz);
        vertex(vc, posMat, normMat, light, tipX, height, tipZ, 0.41F, 0.20F, topAlpha, nx, 0.0F, nz);
    }

    private void triUV(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                       float x1, float y1, float z1, float u1, float v1,
                       float x2, float y2, float z2, float u2, float v2,
                       float x3, float y3, float z3, float u3, float v3, int alpha) {
        vertex(vc, posMat, normMat, light, x1, y1, z1, u1, v1, alpha, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, light, x2, y2, z2, u2, v2, alpha, 0.0F, 1.0F, 0.0F);
        vertex(vc, posMat, normMat, light, x3, y3, z3, u3, v3, alpha, 0.0F, 1.0F, 0.0F);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                        float x, float y, float z, float u, float v, int alpha,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(255, 255, 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normMat, nx, ny, nz)
                .next();
    }

    private static float mirror(float u) {
        float f = u % 2.0F;
        if (f < 0.0F) {
            f += 2.0F;
        }
        return f <= 1.0F ? f : 2.0F - f;
    }

    @Override
    public Identifier getTexture(FlameShockwaveEntity entity) {
        return TEXTURE;
    }
}
