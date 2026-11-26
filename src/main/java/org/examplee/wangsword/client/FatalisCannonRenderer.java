package org.examplee.wangsword.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.entity.FatalisCannonEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 黑龙炮的渲染：三个互相垂直的贴图面片，叠起来像一个紫色发光球。
 * 配合径向渐变贴图，任何角度看都是球。
 */
public class FatalisCannonRenderer extends EntityRenderer<FatalisCannonEntity> {

    private static final Identifier TEXTURE =
            new Identifier("wangsword", "textures/entity/fatalis_cannon.png");

    /** 球体半径（格） */
    private static final float SIZE = 1.4F;

    public FatalisCannonRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(FatalisCannonEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.scale(SIZE, SIZE, SIZE);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        Matrix4f posMat = matrices.peek().getPositionMatrix();
        Matrix3f normMat = matrices.peek().getNormalMatrix();

        // XY 面（法线朝 Z）
        quad(vc, posMat, normMat, light,
                -1, -1, 0,
                 1, -1, 0,
                 1,  1, 0,
                -1,  1, 0,
                0, 0, 1);

        // XZ 面（法线朝 Y）
        quad(vc, posMat, normMat, light,
                -1, 0, -1,
                 1, 0, -1,
                 1, 0,  1,
                -1, 0,  1,
                0, 1, 0);

        // YZ 面（法线朝 X）
        quad(vc, posMat, normMat, light,
                0, -1, -1,
                0,  1, -1,
                0,  1,  1,
                0, -1,  1,
                1, 0, 0);

        matrices.pop();
    }

    private void quad(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float nx, float ny, float nz) {

        vertex(vc, posMat, normMat, light, x1, y1, z1, 0.0F, 1.0F, nx, ny, nz);
        vertex(vc, posMat, normMat, light, x2, y2, z2, 1.0F, 1.0F, nx, ny, nz);
        vertex(vc, posMat, normMat, light, x3, y3, z3, 1.0F, 0.0F, nx, ny, nz);
        vertex(vc, posMat, normMat, light, x4, y4, z4, 0.0F, 0.0F, nx, ny, nz);
    }

    private void vertex(VertexConsumer vc, Matrix4f posMat, Matrix3f normMat, int light,
                        float x, float y, float z, float u, float v,
                        float nx, float ny, float nz) {
        vc.vertex(posMat, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normMat, nx, ny, nz)
                .next();
    }

    @Override
    public Identifier getTexture(FatalisCannonEntity entity) {
        return TEXTURE;
    }
}
