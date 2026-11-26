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
import net.minecraft.util.math.Vec3d;
import org.examplee.wangsword.entity.FlameBoltEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 烈焰弹的渲染器：十字交叉的两张火团面片。
 *
 * <p>贴图 {@code textures/entity/flame_bolt.png} 是圆形火球（中心白、外缘暗红）。
 * 两张面片一张正对飞行方向、一张垂直于地面，从任何角度都能看到一块完整的火团，
 * 不会像单张 billboard 那样在侧视角变成一条线。外面再叠一层加色辉光。
 */
public class FlameBoltRenderer extends EntityRenderer<FlameBoltEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/flame_bolt.png");

    private static final RenderLayer BODY = RenderLayer.getEntityTranslucentEmissive(TEXTURE);
    private static final RenderLayer GLOW = RenderLayer.getEyes(TEXTURE);

    /** 面片半宽（格）：贴图边缘渐隐，圆盘本体视觉直径约 0.7 格 */
    private static final float HALF = 0.58F;
    /** 辉光相对本体的大小 */
    private static final float GLOW_SCALE = 1.45F;

    public FlameBoltRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(FlameBoltEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(FlameBoltEntity entity, net.minecraft.client.render.Frustum frustum,
                                double x, double y, double z) {
        // 不做视锥裁剪：火球贴着屏幕边缘飞出去时，整颗被判成不可见会很突兀
        return true;
    }

    @Override
    public void render(FlameBoltEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;

        // 用飞行方向建一组正交基：fwd 是面片法线，right / up 撑开平面
        Vec3d velocity = entity.getVelocity();
        Vec3d fwd = velocity.lengthSquared() > 1.0E-6D
                ? velocity.normalize()
                : new Vec3d(0.0D, 0.0D, 1.0D);
        Vec3d right = fwd.crossProduct(new Vec3d(0.0D, 1.0D, 0.0D));
        if (right.lengthSquared() < 1.0E-6D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(fwd).normalize();

        // 蝠晶强化弹整体放大一圈，辉光也更亮
        boolean empowered = entity.isEmpowered();
        float scale = HALF * (empowered ? 1.32F : 1.0F)
                * (0.96F + 0.04F * (float) Math.sin(life * 0.55F));
        float glowAlpha = empowered ? 0.55F : 0.36F;

        matrices.push();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();

        // 正对飞行方向的一面（玩家从背后看就是完整的圆）
        quad(vertexConsumers.getBuffer(GLOW), m, n, right, up, fwd, scale * GLOW_SCALE, glowAlpha);
        quad(vertexConsumers.getBuffer(BODY), m, n, right, up, fwd, scale, 1.0F);
        // 垂直于地面的一面，侧视角时补体量
        quad(vertexConsumers.getBuffer(GLOW), m, n, fwd, up, right, scale * GLOW_SCALE,
                empowered ? 0.45F : 0.28F);
        quad(vertexConsumers.getBuffer(BODY), m, n, fwd, up, right, scale * 0.94F, 0.95F);

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** 以 right / up 展开、法线为 normal 的四边形 */
    private void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                      Vec3d right, Vec3d up, Vec3d normal, float half, float alpha) {
        int a = (int) (alpha * 255);
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;

        float rx = (float) right.x * half, ry = (float) right.y * half, rz = (float) right.z * half;
        float ux = (float) up.x * half, uy = (float) up.y * half, uz = (float) up.z * half;

        vc.vertex(m, -rx - ux, -ry - uy, -rz - uz).color(255, 255, 255, a).texture(0.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
        vc.vertex(m, rx - ux, ry - uy, rz - uz).color(255, 255, 255, a).texture(1.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
        vc.vertex(m, rx + ux, ry + uy, rz + uz).color(255, 255, 255, a).texture(1.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
        vc.vertex(m, -rx + ux, -ry + uy, -rz + uz).color(255, 255, 255, a).texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, nx, ny, nz).next();
    }
}
