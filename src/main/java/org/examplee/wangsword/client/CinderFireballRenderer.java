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
import org.examplee.wangsword.entity.CinderFireballEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * attack3 火球的渲染器：十字交叉的两张火团面片 + 一圈加色辉光。
 *
 * <p>贴图 {@code textures/entity/cinder_fireball.png} 是 64×64 的圆形火团。
 * 两张面片一张正对飞行方向、一张垂直，侧视角不会缩成一条线；
 * 面片本身随 age 缓慢自转，边缘的火舌就会转，看起来像在翻滚燃烧。
 */
public class CinderFireballRenderer extends EntityRenderer<CinderFireballEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/cinder_fireball.png");

    private static final RenderLayer BODY = RenderLayer.getEntityTranslucentEmissive(TEXTURE);
    private static final RenderLayer GLOW = RenderLayer.getEyes(TEXTURE);

    /** 面片半宽（格） */
    private static final float HALF = 0.72F;
    private static final float GLOW_SCALE = 1.5F;
    /** 每秒自转弧度 */
    private static final double SPIN = 0.35D;

    public CinderFireballRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(CinderFireballEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(CinderFireballEntity entity, net.minecraft.client.render.Frustum frustum,
                                double x, double y, double z) {
        return true;
    }

    @Override
    public void render(CinderFireballEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        float life = entity.age + tickDelta;

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

        // 绕飞行方向自转，火舌跟着转
        double spin = life * SPIN;
        double cs = Math.cos(spin);
        double sn = Math.sin(spin);
        Vec3d spinRight = right.multiply(cs).add(up.multiply(sn));
        Vec3d spinUp = up.multiply(cs).subtract(right.multiply(sn));

        float scale = HALF * (0.96F + 0.05F * (float) Math.sin(life * 0.7F));

        matrices.push();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        Matrix3f n = entry.getNormalMatrix();

        quad(vertexConsumers.getBuffer(GLOW), m, n, spinRight, spinUp, fwd, scale * GLOW_SCALE, 0.34F);
        quad(vertexConsumers.getBuffer(BODY), m, n, spinRight, spinUp, fwd, scale, 1.0F);
        quad(vertexConsumers.getBuffer(GLOW), m, n, fwd, spinUp, spinRight, scale * GLOW_SCALE, 0.26F);
        quad(vertexConsumers.getBuffer(BODY), m, n, fwd, spinUp, spinRight, scale * 0.94F, 0.95F);

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
