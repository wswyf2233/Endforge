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
import org.examplee.wangsword.entity.GhostOrbEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 裂冠幽灵光弹的渲染器：一张正对镜头的发光团面片。
 *
 * <p>贴图 {@code textures/entity/ghost_orb.png} 是 32×32 的幽蓝光球，边缘渐隐。
 * 本体外面叠一层加色辉光，暗处也能看到一颗亮团。
 */
public class GhostOrbRenderer extends EntityRenderer<GhostOrbEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/ghost_orb.png");

    private static final RenderLayer BODY = RenderLayer.getEntityTranslucentEmissive(TEXTURE);
    private static final RenderLayer GLOW = RenderLayer.getEyes(TEXTURE);

    /** 面片半宽（格）：贴图边缘渐隐，视觉直径约 0.5 格 */
    private static final float HALF = 0.30F;
    private static final float GLOW_SCALE = 1.6F;

    public GhostOrbRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(GhostOrbEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(GhostOrbEntity entity, net.minecraft.client.render.Frustum frustum,
                                double x, double y, double z) {
        return true;
    }

    @Override
    public void render(GhostOrbEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;
        float angle = life * 0.22F;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        matrices.push();
        // 正对镜头
        matrices.multiply(this.dispatcher.getRotation());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();

        float scale = HALF * (0.95F + 0.05F * (float) Math.sin(life * 0.7F));

        quad(vertexConsumers.getBuffer(GLOW), m, n, scale * GLOW_SCALE, cos, sin, 0.45F);
        quad(vertexConsumers.getBuffer(BODY), m, n, scale, cos, sin, 1.0F);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** 在 billboard 平面上按 (cos, sin) 旋转的四边形 */
    private void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float half,
                      float cos, float sin, float alpha) {
        int a = (int) (alpha * 255);
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        vc.vertex(m, -half * cos + half * sin, -half * sin - half * cos, 0.0F)
                .color(255, 255, 255, a).texture(0.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 0.0F, 1.0F).next();
        vc.vertex(m, half * cos + half * sin, half * sin - half * cos, 0.0F)
                .color(255, 255, 255, a).texture(1.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 0.0F, 1.0F).next();
        vc.vertex(m, half * cos - half * sin, half * sin + half * cos, 0.0F)
                .color(255, 255, 255, a).texture(1.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 0.0F, 1.0F).next();
        vc.vertex(m, -half * cos - half * sin, -half * sin + half * cos, 0.0F)
                .color(255, 255, 255, a).texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0.0F, 0.0F, 1.0F).next();
    }
}
