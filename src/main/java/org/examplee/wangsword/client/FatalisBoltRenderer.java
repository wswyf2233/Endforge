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
import org.examplee.wangsword.entity.FatalisBoltEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 黑龙矢渲染：拉长的菱形晶体（八面体），沿飞行方向伸展，带缓慢自转。
 *
 * <p>和炮弹的"三张交叉面片"不同，这里是真正的 3D 网格：
 * 前后两个尖端 + 中间四个顶点，共 8 个三角面。
 */
public class FatalisBoltRenderer extends EntityRenderer<FatalisBoltEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/fatalis_bolt.png");

    /** 前尖端长度 */
    private static final float TIP_FRONT = 0.62F;
    /** 后尖端长度（比前面短，像箭羽） */
    private static final float TIP_BACK = 0.26F;
    /** 腰部半径 */
    private static final float RADIUS = 0.10F;

    public FatalisBoltRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(FatalisBoltEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        matrices.push();

        // 让 +Z 指向飞行方向：直接用四元数把 (0,0,1) 转到方向向量，避免 yaw/pitch 符号出错
        var dir = entity.getDirection();
        if (dir.lengthSquared() > 1.0E-6) {
            org.joml.Vector3f from = new org.joml.Vector3f(0.0F, 0.0F, 1.0F);
            org.joml.Vector3f to = new org.joml.Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
            matrices.multiply(new org.joml.Quaternionf().rotationTo(from, to));
        }

        // 沿自身长轴（局部 Z）自转
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(entity.age * 22.0F));

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // 腰部四个点（绕 Z 轴的菱形）
        float[][] ring = {
                { RADIUS, 0.0F },
                { 0.0F, RADIUS },
                { -RADIUS, 0.0F },
                { 0.0F, -RADIUS },
        };

        // 前尖与后尖
        float[] front = { 0.0F, 0.0F, TIP_FRONT };
        float[] back = { 0.0F, 0.0F, -TIP_BACK };

        // UV：前端亮、后端暗（贴图横向渐变）
        float uFront = 0.06F;
        float uRing = 0.62F;
        float uBack = 0.94F;
        float vMid = 0.5F;

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;

            // 前段：两个三角面
            tri(vc, posMat, normMat, light,
                    front, uFront, vMid,
                    new float[]{ring[i][0], ring[i][1], 0}, uRing, vMid - 0.18F,
                    new float[]{ring[j][0], ring[j][1], 0}, uRing, vMid + 0.18F);

            // 后段：两个三角面
            tri(vc, posMat, normMat, light,
                    back, uBack, vMid,
                    new float[]{ring[j][0], ring[j][1], 0}, uRing, vMid + 0.18F,
                    new float[]{ring[i][0], ring[i][1], 0}, uRing, vMid - 0.18F);
        }

        matrices.pop();
    }

    private void tri(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                     float[] a, float ua, float va,
                     float[] b, float ub, float vb,
                     float[] c, float uc, float vc2) {

        // 面法线
        Vector3f ab = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f ac = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
        Vector3f n = ab.cross(ac).normalize();

        vertex(vc, posMat, normMat, light, a, ua, va, n);
        vertex(vc, posMat, normMat, light, b, ub, vb, n);
        vertex(vc, posMat, normMat, light, c, uc, vc2, n);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                        float[] p, float u, float v, Vector3f normal) {
        vc.vertex(posMat, p[0], p[1], p[2])
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normMat, normal.x, normal.y, normal.z)
                .next();
    }

    @Override
    public Identifier getTexture(FatalisBoltEntity entity) {
        return TEXTURE;
    }
}