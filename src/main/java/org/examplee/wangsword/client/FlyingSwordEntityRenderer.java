package org.examplee.wangsword.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.FlyingSwordEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 飞剑渲染，两种形态：
 *
 * <ul>
 *   <li><b>物品形态</b>（玩家王剑技能）：直接用物品模型画，贴图就是手里那把剑。</li>
 *   <li><b>灵体形态</b>（剑冢守卫出剑）：不画物品，画一整片幽蓝光刃——
 *       两片十字交叉的发光面片 + 一层外辉光 + 一条白芯，配合自旋和粒子拖尾。</li>
 * </ul>
 */
public class FlyingSwordEntityRenderer extends EntityRenderer<FlyingSwordEntity> {

    /** 光刃贴图：中心白芯、向外渐变成青蓝并透明 */
    private static final Identifier SPECTRAL_TEXTURE =
            new Identifier("wangsword", "textures/entity/spectral_blade.png");

    /** 光刃全长（格） */
    private static final float BLADE_LENGTH = 2.5F;
    /** 光刃最宽处的半宽 */
    private static final float BLADE_HALF_WIDTH = 0.30F;
    /** 沿剑身自旋速度（度/tick）：十字面片只有转起来，任何角度都不会塌成一条线 */
    private static final float SPIN_PER_TICK = 11.0F;

    public FlyingSwordEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FlyingSwordEntity entity) {
        return SPECTRAL_TEXTURE;
    }

    @Override
    public void render(FlyingSwordEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        if (entity.isSpectral()) {
            renderSpectral(entity, tickDelta, matrices, vertexConsumers);
            return;
        }

        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) {
            return;
        }
        Vec3d velocity = entity.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6D) {
            velocity = new Vec3d(0.0D, 0.0D, 1.0D);
        }
        velocity = velocity.normalize();

        float yawDeg = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float pitchDeg = (float) -Math.toDegrees(Math.asin(velocity.y));

        // 绕剑身长轴自旋：物品模型是平面贴图，侧对镜头时只剩一条线，
        // 转起来之后任何角度都能看到剑面，也更有"飞剑"的感觉
        float spin = ((entity.age + tickDelta) * 18.0F) % 360.0F;

        matrices.push();
        // 让模型的 +Y（剑身方向）转到飞行方向上
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawDeg));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + pitchDeg));
        // 此时局部 +Y 已经是飞行方向，再绕它转就是沿剑身自旋
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        // FIXED 的 display 缩放是 1.0，乘 2.0 后剑身约 2 格长（原来 GROUND 是 0.5，只有 1 格）
        matrices.scale(2.0F, 2.0F, 2.0F);

        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.FIXED,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers,
                entity.getWorld(),
                entity.getId());
        matrices.pop();
    }

    // ------------------------------------------------------------------
    // 灵体光刃
    // ------------------------------------------------------------------

    private void renderSpectral(FlyingSwordEntity entity, float tickDelta, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers) {
        Vec3d velocity = entity.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6D) {
            velocity = new Vec3d(0.0D, 0.0D, 1.0D);
        }
        velocity = velocity.normalize();

        float yawDeg = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float pitchDeg = (float) -Math.toDegrees(Math.asin(velocity.y));

        float life = entity.age + tickDelta;
        float spin = (life * SPIN_PER_TICK) % 360.0F;
        // 呼吸：亮度轻微起伏，看起来像活的魂火
        float pulse = 0.84F + 0.16F * MathHelper.sin(life * 0.65F);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawDeg));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + pitchDeg));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));

        int maxLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(SPECTRAL_TEXTURE));

        // 三层叠出"发光"观感：外辉光 → 主刃 → 白芯
        blade(vc, matrices, BLADE_LENGTH * 1.16F, BLADE_HALF_WIDTH * 1.60F, 0.16F * pulse);
        blade(vc, matrices, BLADE_LENGTH, BLADE_HALF_WIDTH, 0.90F * pulse);
        blade(vc, matrices, BLADE_LENGTH * 0.94F, BLADE_HALF_WIDTH * 0.38F, 0.95F * pulse);

        matrices.pop();
    }

    /**
     * 画一片光刃（局部 +Y 是剑尖方向，X 是刃宽，Z 为 0 厚）。
     *
     * <p>同一片画两次并绕 Y 转 90°，形成十字，侧看才不会消失；每片面正反各画一次。
     */
    private void blade(VertexConsumer vc, MatrixStack matrices, float length, float halfWidth, float alpha) {
        float tipY = length * 0.62F;
        float wideY = length * 0.02F;
        float tailY = -length * 0.38F;
        float tailW = halfWidth * 0.30F;

        for (int pass = 0; pass < 2; pass++) {
            matrices.push();
            if (pass == 1) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            }

            // 正面（法线 +Z）
            tri(vc, matrices, alpha, 1.0F,
                    0.0F, tipY, 0.5F, 0.0F,
                    -halfWidth, wideY, 0.0F, 0.30F,
                    halfWidth, wideY, 1.0F, 0.30F);
            quad(vc, matrices, alpha, 1.0F,
                    -halfWidth, wideY, 0.0F, 0.30F,
                    halfWidth, wideY, 1.0F, 0.30F,
                    tailW, tailY, 0.58F, 1.0F,
                    -tailW, tailY, 0.42F, 1.0F);

            // 反面（法线 -Z，绕序反过来）
            tri(vc, matrices, alpha, -1.0F,
                    0.0F, tipY, 0.5F, 0.0F,
                    halfWidth, wideY, 1.0F, 0.30F,
                    -halfWidth, wideY, 0.0F, 0.30F);
            quad(vc, matrices, alpha, -1.0F,
                    -tailW, tailY, 0.42F, 1.0F,
                    tailW, tailY, 0.58F, 1.0F,
                    halfWidth, wideY, 1.0F, 0.30F,
                    -halfWidth, wideY, 0.0F, 0.30F);

            matrices.pop();
        }
    }

    private void tri(VertexConsumer vc, MatrixStack matrices, float alpha, float normalZ,
                     float x1, float y1, float u1, float v1,
                     float x2, float y2, float u2, float v2,
                     float x3, float y3, float u3, float v3) {
        vertex(vc, matrices, x1, y1, u1, v1, alpha, normalZ);
        vertex(vc, matrices, x2, y2, u2, v2, alpha, normalZ);
        vertex(vc, matrices, x3, y3, u3, v3, alpha, normalZ);
    }

    private void quad(VertexConsumer vc, MatrixStack matrices, float alpha, float normalZ,
                      float x1, float y1, float u1, float v1,
                      float x2, float y2, float u2, float v2,
                      float x3, float y3, float u3, float v3,
                      float x4, float y4, float u4, float v4) {
        vertex(vc, matrices, x1, y1, u1, v1, alpha, normalZ);
        vertex(vc, matrices, x2, y2, u2, v2, alpha, normalZ);
        vertex(vc, matrices, x3, y3, u3, v3, alpha, normalZ);
        vertex(vc, matrices, x4, y4, u4, v4, alpha, normalZ);
    }

    private void vertex(VertexConsumer vc, MatrixStack matrices, float x, float y,
                        float u, float v, float alpha, float normalZ) {
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();
        vc.vertex(posMat, x, y, 0.0F)
                .color(255, 255, 255, (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F))
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                // 自发光：不吃环境光，洞里、夜里一样亮
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normMat, 0.0F, 0.0F, normalZ)
                .next();
    }
}
